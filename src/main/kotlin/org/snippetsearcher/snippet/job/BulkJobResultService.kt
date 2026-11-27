package org.snippetsearcher.snippet.job

import org.snippetsearcher.snippet.language.LintResult
import org.snippetsearcher.snippet.rule.SnippetLinter
import org.snippetsearcher.snippet.snippet.LintStatus
import org.snippetsearcher.snippet.snippet.Snippet
import org.snippetsearcher.snippet.snippet.SnippetRepository
import org.snippetsearcher.snippet.storage.ContentStore
import org.snippetsearcher.snippet.streams.FormatRequestMessage
import org.snippetsearcher.snippet.streams.FormatResultMessage
import org.snippetsearcher.snippet.streams.LintRequestMessage
import org.snippetsearcher.snippet.streams.LintResultMessage
import org.snippetsearcher.snippet.streams.TestRequestMessage
import org.snippetsearcher.snippet.streams.TestResultMessage
import org.snippetsearcher.snippet.test.SnippetTestRepository
import org.snippetsearcher.snippet.test.TestStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class BulkJobResultService(
    private val snippets: SnippetRepository,
    private val linter: SnippetLinter,
    private val store: JobTrackingStore,
    private val tests: SnippetTestRepository,
    private val contents: ContentStore,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun applyLintResult(msg: LintResultMessage) {
        val snippet = findFreshSnippet(msg.snippetId, msg.snippetVersion, msg.jobId) ?: return
        if (!store.isPending(msg.jobId, snippet.id)) return
        snippet.lintStatus = linter.record(snippet.id, LintResult(msg.warnings, msg.errors))
        store.completeItem(msg.jobId, snippet.id, BulkJobItemStatus.DONE)
    }

    @Transactional
    fun applyFormatResult(msg: FormatResultMessage) {
        val snippet = findFreshSnippet(msg.snippetId, msg.snippetVersion, msg.jobId) ?: return
        if (!store.isPending(msg.jobId, snippet.id)) return
        val formatted = msg.formattedContent
        if (formatted != null && msg.errors.isEmpty()) {
            val key = "${snippet.id}/formatted"
            contents.put(key, formatted)
            snippet.formattedKey = key
            store.completeItem(msg.jobId, snippet.id, BulkJobItemStatus.DONE)
        } else {
            store.completeItem(msg.jobId, snippet.id, BulkJobItemStatus.FAILED)
        }
    }

    @Transactional
    fun applyTestResult(msg: TestResultMessage) {
        val snippet = snippets.findByIdOrNull(msg.snippetId)
        if (snippet != null && sameVersion(snippet.updatedAt, msg.snippetVersion)) {
            tests.findByIdOrNull(msg.testId)?.let { test ->
                test.status = TestStatus.valueOf(msg.status)
                test.lastOutputJson = objectMapper.writeValueAsString(msg.outputs)
                test.lastRunAt = Instant.now()
            }
        }
    }

    @Transactional
    fun applyLintDlq(msg: LintRequestMessage) {
        val snippet = findFreshSnippet(msg.snippetId, msg.snippetVersion, msg.jobId) ?: return
        if (!store.isPending(msg.jobId, snippet.id)) return
        snippet.lintStatus = LintStatus.FAILED
        store.completeItem(msg.jobId, snippet.id, BulkJobItemStatus.FAILED)
    }

    @Transactional
    fun applyFormatDlq(msg: FormatRequestMessage) {
        val snippet = findFreshSnippet(msg.snippetId, msg.snippetVersion, msg.jobId) ?: return
        if (!store.isPending(msg.jobId, snippet.id)) return
        store.completeItem(msg.jobId, snippet.id, BulkJobItemStatus.FAILED)
    }

    @Transactional
    fun applyTestDlq(msg: TestRequestMessage) {
        val snippet = snippets.findByIdOrNull(msg.snippetId)
        if (snippet != null && sameVersion(snippet.updatedAt, msg.snippetVersion)) {
            tests.findByIdOrNull(msg.testId)?.let { test ->
                test.status = TestStatus.ERROR
                test.lastRunAt = Instant.now()
            }
        }
    }

    private fun findFreshSnippet(
        snippetId: UUID,
        snippetVersion: Instant,
        jobId: UUID?,
    ): Snippet? {
        val snippet = snippets.findByIdOrNull(snippetId)
        if (snippet == null || !sameVersion(snippet.updatedAt, snippetVersion)) {
            store.completeItem(jobId, snippetId, BulkJobItemStatus.DONE)
            return null
        }
        return snippet
    }

    private fun sameVersion(
        a: Instant,
        b: Instant,
    ): Boolean = a.truncatedTo(ChronoUnit.MILLIS) == b.truncatedTo(ChronoUnit.MILLIS)
}
