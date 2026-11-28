package org.snippetsearcher.snippet.job

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.snippetsearcher.snippet.rule.LintViolationRepository
import org.snippetsearcher.snippet.rule.RuleConfigRepository
import org.snippetsearcher.snippet.snippet.LintStatus
import org.snippetsearcher.snippet.snippet.Snippet
import org.snippetsearcher.snippet.snippet.SnippetRepository
import org.snippetsearcher.snippet.snippet.SnippetService
import org.snippetsearcher.snippet.snippet.UpdateSnippetRequest
import org.snippetsearcher.snippet.streams.BulkJobRequestMessage
import org.snippetsearcher.snippet.streams.FormatRequestMessage
import org.snippetsearcher.snippet.streams.FormatResultMessage
import org.snippetsearcher.snippet.streams.LintRequestMessage
import org.snippetsearcher.snippet.streams.LintResultMessage
import org.snippetsearcher.snippet.streams.StreamNames
import org.snippetsearcher.snippet.streams.TestRequestMessage
import org.snippetsearcher.snippet.streams.TestResultMessage
import org.snippetsearcher.snippet.support.FakeCollaborators
import org.snippetsearcher.snippet.support.FakeContentStore
import org.snippetsearcher.snippet.support.FakePermissionClient
import org.snippetsearcher.snippet.support.FakeStreamProducer
import org.snippetsearcher.snippet.test.SnippetTest
import org.snippetsearcher.snippet.test.SnippetTestRepository
import org.snippetsearcher.snippet.test.TestStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@ActiveProfiles("test")
@Import(FakeCollaborators::class)
class BulkJobFlowTest {
    @Autowired private lateinit var fanOut: BulkJobFanOutService

    @Autowired private lateinit var resultService: BulkJobResultService

    @Autowired private lateinit var snippetService: SnippetService

    @Autowired private lateinit var snippets: SnippetRepository

    @Autowired private lateinit var violations: LintViolationRepository

    @Autowired private lateinit var jobs: BulkJobRepository

    @Autowired private lateinit var items: BulkJobItemRepository

    @Autowired private lateinit var tests: SnippetTestRepository

    @Autowired private lateinit var rules: RuleConfigRepository

    @Autowired private lateinit var permissions: FakePermissionClient

    @Autowired private lateinit var contents: FakeContentStore

    @Autowired private lateinit var producer: FakeStreamProducer

    @BeforeEach
    fun clean() {
        tests.deleteAll()
        violations.deleteAll()
        items.deleteAll()
        jobs.deleteAll()
        rules.deleteAll()
        snippets.deleteAll()
        permissions.reset()
        contents.contents.clear()
        producer.reset()
    }

    @Test
    fun `fan out with zero snippets marks job DONE`() {
        val jobId = UUID.randomUUID()
        jobs.save(
            BulkJob(jobId, "u1", "FORMAT", "printscript", status = BulkJobStatus.RUNNING, createdAt = Instant.now()),
        )

        fanOut.fanOut(BulkJobRequestMessage(jobId, "u1", "FORMAT", "printscript"))

        val job = jobs.findByIdOrNull(jobId)!!
        assertEquals(0, job.total)
        assertEquals(BulkJobStatus.DONE, job.status)
    }

    @Test
    fun `format job dispatches snippets, persists formatted content, and handles dlq`() {
        val s1 = seedSnippet("u1", "let x:number=1;")
        val s2 = seedSnippet("u1", "let y:number=2;")
        val jobId = UUID.randomUUID()
        jobs.save(
            BulkJob(jobId, "u1", "FORMAT", "printscript", status = BulkJobStatus.RUNNING, createdAt = Instant.now()),
        )

        fanOut.fanOut(BulkJobRequestMessage(jobId, "u1", "FORMAT", "printscript"))

        assertEquals(2, producer.published.size)
        assertEquals(StreamNames.FORMAT_REQUESTS, producer.published.first().first)

        resultService.applyFormatResult(
            FormatResultMessage(jobId, s1.id, s1.updatedAt, "let x: number = 1;", emptyList()),
        )
        // redelivery is ignored
        resultService.applyFormatResult(
            FormatResultMessage(jobId, s1.id, s1.updatedAt, "let x: number = 1;", emptyList()),
        )
        // second snippet hits DLQ
        resultService.applyFormatDlq(FormatRequestMessage(jobId, "u1", s2.id, s2.updatedAt, "printscript", "1.0", ""))

        val job = jobs.findByIdOrNull(jobId)!!
        assertEquals(2, job.total)
        assertEquals(1, job.done)
        assertEquals(1, job.failed)
        assertEquals(BulkJobStatus.DONE, job.status)
        assertEquals("${s1.id}/formatted", snippets.findByIdOrNull(s1.id)!!.formattedKey)
        assertEquals("let x: number = 1;", contents.get("${s1.id}/formatted"))
    }

    @Test
    fun `lint result ignores stale snippetVersion and DLQ marks FAILED`() {
        val s1 = seedSnippet("u1", "let x: number = 1;")
        val jobId = UUID.randomUUID()
        jobs.save(
            BulkJob(
                jobId,
                "u1",
                "LINT",
                "printscript",
                total = 1,
                status = BulkJobStatus.RUNNING,
                createdAt = Instant.now(),
            ),
        )
        items.save(BulkJobItem(BulkJobItemId(jobId, s1.id), BulkJobItemStatus.PENDING))

        // stale version is ignored without changing snippet
        resultService.applyLintResult(
            LintResultMessage(jobId, s1.id, s1.updatedAt.minusSeconds(10), emptyList(), emptyList()),
        )
        assertEquals(LintStatus.PENDING, snippets.findByIdOrNull(s1.id)!!.lintStatus)
        assertEquals(BulkJobStatus.DONE, jobs.findByIdOrNull(jobId)!!.status)

        // DLQ marks snippet FAILED
        val job2 = UUID.randomUUID()
        val j2 =
            BulkJob(
                job2,
                "u1",
                "LINT",
                "printscript",
                total = 1,
                status = BulkJobStatus.RUNNING,
                createdAt = Instant.now(),
            )
        jobs.save(j2)
        items.save(BulkJobItem(BulkJobItemId(job2, s1.id), BulkJobItemStatus.PENDING))
        resultService.applyLintDlq(LintRequestMessage(job2, "u1", s1.id, s1.updatedAt, "printscript", "1.0", ""))
        assertEquals(LintStatus.FAILED, snippets.findByIdOrNull(s1.id)!!.lintStatus)
    }

    @Test
    fun `updating snippet code enqueues tests and applies test results`() {
        val s1 = seedSnippet("u1", "let x: number = 1;")
        val testId = UUID.randomUUID()
        tests.save(SnippetTest(testId, s1.id, "t1", "[]", """["2"]""", TestStatus.PASSED))

        val updated =
            snippetService.update(
                "u1",
                s1.id,
                UpdateSnippetRequest(content = "let x: number = 2;\nprintln(x);"),
            )
        assertNull(snippets.findByIdOrNull(s1.id)!!.formattedKey)
        assertEquals(TestStatus.PENDING, tests.findByIdOrNull(testId)!!.status)
        assertEquals(1, producer.published.size)
        val (stream, payload) = producer.published.first()
        assertEquals(StreamNames.TEST_REQUESTS, stream)
        val req = payload as TestRequestMessage

        resultService.applyTestResult(TestResultMessage(null, s1.id, req.snippetVersion, testId, "FAILED", listOf("2")))
        val savedTest = tests.findByIdOrNull(testId)!!
        assertEquals(TestStatus.FAILED, savedTest.status)
        assertNotNull(savedTest.lastRunAt)
        assertEquals(updated.id, s1.id)
    }

    private fun seedSnippet(
        owner: String,
        content: String,
    ): Snippet {
        val now = Instant.now()
        val snippet =
            snippets.save(
                Snippet(
                    id = UUID.randomUUID(),
                    name = "s-${UUID.randomUUID()}",
                    description = "",
                    language = "printscript",
                    version = "1.0",
                    ownerId = owner,
                    createdAt = now,
                    updatedAt = now,
                    lintStatus = LintStatus.PENDING,
                ),
            )
        permissions.grantOwner(snippet.id, owner)
        contents.put(snippet.contentKey, content)
        return snippet
    }
}
