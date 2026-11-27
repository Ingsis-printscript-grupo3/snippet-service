package org.snippetsearcher.snippet.job

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.snippetsearcher.snippet.snippet.CreateSnippetRequest
import org.snippetsearcher.snippet.snippet.LintStatus
import org.snippetsearcher.snippet.snippet.SnippetService
import org.snippetsearcher.snippet.streams.BulkJobRequestMessage
import org.snippetsearcher.snippet.streams.LintRequestMessage
import org.snippetsearcher.snippet.streams.LintResultMessage
import org.snippetsearcher.snippet.support.FakeCollaborators
import org.snippetsearcher.snippet.support.TestEnvironment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val ADMIN = "auth0|job-admin"

// US 12 y 15: el reparto de un job masivo tiene que poder repetirse y no puede perder resultados
@SpringBootTest
@ActiveProfiles("test")
@Import(FakeCollaborators::class)
class BulkJobFanOutTest(
    @Autowired private val fanOut: BulkJobFanOutService,
    @Autowired private val results: BulkJobResultService,
    @Autowired private val snippetService: SnippetService,
    @Autowired private val jobs: BulkJobRepository,
    @Autowired private val environment: TestEnvironment,
) {
    @BeforeEach
    @AfterEach
    fun cleanUp() = environment.reset()

    // un worker contesta en milisegundos: si el pedido saliera antes del commit, el resultado llegaria
    // cuando el item todavia no existe para los demas, se descartaria y el job no terminaria nunca
    @Test
    fun `requests reach the queue only after the job items are committed`() {
        repeat(3) { createSnippet("snippet$it") }
        val job = newLintJob()
        val itemsCommittedAtPublish = mutableListOf<Int>()
        environment.producer.onPublish = { itemsCommittedAtPublish.add(environment.committedRows("bulk_job_item")) }

        fanOut.fanOut(BulkJobRequestMessage(job.id, ADMIN, "LINT", "printscript"))

        assertEquals(listOf(3, 3, 3), itemsCommittedAtPublish)
        assertEquals(3, jobs.findById(job.id).get().total)
    }

    // un reinicio a mitad del reparto hace que el mismo mensaje se entregue de nuevo
    @Test
    fun `a repeated fan-out skips the snippets that were already resolved`() {
        val resolved = createSnippet("resuelto")
        val waiting = createSnippet("pendiente")
        val job = newLintJob()
        val request = BulkJobRequestMessage(job.id, ADMIN, "LINT", "printscript")
        fanOut.fanOut(request)
        val sent = lintRequests().first { it.snippetId == resolved }
        results.applyLintResult(LintResultMessage(job.id, resolved, sent.snippetVersion, emptyList(), emptyList()))
        environment.producer.reset()

        fanOut.fanOut(request)

        assertEquals(listOf(waiting), lintRequests().map { it.snippetId })
        assertEquals(LintStatus.COMPLIANT, snippetService.get(ADMIN, resolved).lintStatus)
        assertEquals(1, jobs.findById(job.id).get().done)
    }

    // Redis se cae a mitad de la publicacion: el reparto tiene que fallar, para que el mensaje no se
    // confirme, y al repetirse salir completo
    @Test
    fun `if publishing fails the fan-out fails and repeating it sends every snippet`() {
        repeat(3) { createSnippet("snippet$it") }
        val request = BulkJobRequestMessage(newLintJob().id, ADMIN, "LINT", "printscript")
        var attempts = 0
        environment.producer.onPublish = { if (++attempts == 2) error("redis no responde") }

        assertFailsWith<IllegalStateException> { fanOut.fanOut(request) }
        assertEquals(3, environment.committedRows("bulk_job_item"))
        environment.producer.reset()

        fanOut.fanOut(request)

        assertEquals(3, lintRequests().size)
    }

    // si un snippet desaparece entre dos repartos ya no llega ningun resultado que cierre el job
    @Test
    fun `a repeated fan-out closes the job when nothing is left to resolve`() {
        val first = createSnippet("primero")
        val second = createSnippet("segundo")
        val deleted = createSnippet("borrado")
        val job = newLintJob()
        val request = BulkJobRequestMessage(job.id, ADMIN, "LINT", "printscript")
        fanOut.fanOut(request)
        lintRequests().filter { it.snippetId != deleted }.forEach {
            results.applyLintResult(
                LintResultMessage(job.id, it.snippetId, it.snippetVersion, emptyList(), emptyList()),
            )
        }
        assertEquals(BulkJobStatus.RUNNING, jobs.findById(job.id).get().status)
        snippetService.delete(ADMIN, deleted)

        fanOut.fanOut(request)

        val closed = jobs.findById(job.id).get()
        assertEquals(BulkJobStatus.DONE, closed.status)
        assertEquals(listOf(first, second).size, closed.done)
    }

    private fun lintRequests(): List<LintRequestMessage> =
        environment.producer.published.map { it.second as LintRequestMessage }

    private fun createSnippet(name: String): UUID =
        snippetService.create(ADMIN, CreateSnippetRequest(name, "", "printscript", "1.1", "println(1);")).id

    private fun newLintJob(): BulkJob =
        jobs.save(
            BulkJob(
                id = UUID.randomUUID(),
                userId = ADMIN,
                type = "LINT",
                language = "printscript",
                createdAt = Instant.now(),
            ),
        )
}
