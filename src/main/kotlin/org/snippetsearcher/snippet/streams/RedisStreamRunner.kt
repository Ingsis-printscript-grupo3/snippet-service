package org.snippetsearcher.snippet.streams

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.PendingMessage
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

interface StreamHandler {
    val stream: String
    val group: String
    val dlqStream: String?
        get() = null

    fun handle(payload: String)
}

@Component
@ConditionalOnProperty(name = ["app.redis.enabled"], havingValue = "true", matchIfMissing = true)
class RedisStreamRunner(
    private val redis: StringRedisTemplate,
    private val producer: StreamProducer,
    private val handlers: List<StreamHandler>,
    @Value("\${app.redis.min-idle-ms:30000}") private val minIdleMs: Long,
    @Value("\${app.redis.max-deliveries:3}") private val maxDeliveries: Long,
    @Value("\${app.redis.poll-delay-ms:250}") private val pollDelayMs: Long,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)
    private val consumerName = "snippet-${UUID.randomUUID()}"
    private var executor: ScheduledExecutorService? = null

    @Volatile
    private var running = false

    override fun start() {
        if (running || handlers.isEmpty()) return
        handlers.forEach { ensureGroup(it.stream, it.group) }
        val pool = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "redis-stream-runner") }
        executor = pool
        running = true
        pool.scheduleWithFixedDelay({ pollAll() }, pollDelayMs, pollDelayMs, TimeUnit.MILLISECONDS)
    }

    override fun stop() {
        running = false
        executor?.shutdownNow()
        executor = null
    }

    override fun isRunning(): Boolean = running

    fun pollAll() {
        handlers.forEach { handler ->
            runCatching { pollHandler(handler) }
                .onFailure { log.warn("Error polling stream {}: {}", handler.stream, it.message) }
        }
    }

    internal fun pollHandler(handler: StreamHandler) {
        val ops = redis.opsForStream<String, String>()
        val records =
            ops.read(
                Consumer.from(handler.group, consumerName),
                StreamReadOptions.empty().count(BATCH_SIZE),
                StreamOffset.create(handler.stream, ReadOffset.lastConsumed()),
            ) ?: emptyList()
        records.forEach { processRecord(handler, it) }
        reclaimPending(handler)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processRecord(
        handler: StreamHandler,
        record: MapRecord<String, String, String>,
    ) {
        val payload = record.value["payload"] ?: return
        try {
            handler.handle(payload)
            redis.opsForStream<String, String>().acknowledge(handler.stream, handler.group, record.id)
        } catch (ex: RuntimeException) {
            log.warn("Fallo procesando {} id={}: {}", handler.stream, record.id, ex.message)
        }
    }

    private fun reclaimPending(handler: StreamHandler) {
        val ops = redis.opsForStream<String, String>()
        val pending = ops.pending(handler.stream, handler.group, Range.unbounded<String>(), BATCH_SIZE)
        pending
            .filter { it.elapsedTimeSinceLastDelivery.toMillis() >= minIdleMs }
            .forEach { handlePendingMessage(handler, it) }
    }

    private fun handlePendingMessage(
        handler: StreamHandler,
        msg: PendingMessage,
    ) {
        val ops = redis.opsForStream<String, String>()
        if (msg.totalDeliveryCount >= maxDeliveries && handler.dlqStream != null) {
            moveToDlq(handler, msg)
            return
        }
        val claimed =
            ops.claim(
                handler.stream,
                handler.group,
                consumerName,
                Duration.ofMillis(minIdleMs),
                msg.id,
            )
        claimed.forEach { processRecord(handler, it) }
    }

    private fun moveToDlq(
        handler: StreamHandler,
        msg: PendingMessage,
    ) {
        val ops = redis.opsForStream<String, String>()
        val records = ops.range(handler.stream, Range.closed(msg.idAsString, msg.idAsString)) ?: emptyList()
        val payload = records.firstOrNull()?.value?.get("payload")
        if (payload != null) {
            producer.publish(handler.dlqStream!!, payload)
        }
        ops.acknowledge(handler.stream, handler.group, msg.id)
        log.error("Mensaje {} movido a DLQ {} tras {} intentos", msg.idAsString, handler.dlqStream, maxDeliveries)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun ensureGroup(
        stream: String,
        group: String,
    ) {
        try {
            redis.opsForStream<String, String>().createGroup(stream, ReadOffset.from("0"), group)
        } catch (ex: RuntimeException) {
            if (ex.message?.contains("BUSYGROUP") != true && ex.cause?.message?.contains("BUSYGROUP") != true) {
                log.warn("No se pudo crear grupo {} en {}: {}", group, stream, ex.message)
            }
        }
    }

    companion object {
        private const val BATCH_SIZE = 10L
    }
}
