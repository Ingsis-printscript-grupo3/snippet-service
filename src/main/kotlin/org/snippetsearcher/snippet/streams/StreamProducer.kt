package org.snippetsearcher.snippet.streams

import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.ObjectMapper

interface StreamProducer {
    fun publish(
        stream: String,
        payload: Any,
    ): RecordId?
}

fun StreamProducer.publishAfterCommit(
    stream: String,
    payload: Any,
) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
        publish(stream, payload)
        return
    }
    TransactionSynchronizationManager.registerSynchronization(
        object : TransactionSynchronization {
            override fun afterCommit() {
                publish(stream, payload)
            }
        },
    )
}

@Component
class RedisStreamProducer(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : StreamProducer {
    override fun publish(
        stream: String,
        payload: Any,
    ): RecordId? {
        val json = if (payload is String) payload else objectMapper.writeValueAsString(payload)
        return redis.opsForStream<String, String>().add(stream, mapOf("payload" to json))
    }
}
