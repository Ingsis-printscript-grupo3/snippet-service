package org.snippetsearcher.snippet.job

import org.snippetsearcher.snippet.streams.BulkJobRequestMessage
import org.snippetsearcher.snippet.streams.FormatRequestMessage
import org.snippetsearcher.snippet.streams.FormatResultMessage
import org.snippetsearcher.snippet.streams.LintRequestMessage
import org.snippetsearcher.snippet.streams.LintResultMessage
import org.snippetsearcher.snippet.streams.StreamHandler
import org.snippetsearcher.snippet.streams.StreamNames
import org.snippetsearcher.snippet.streams.TestRequestMessage
import org.snippetsearcher.snippet.streams.TestResultMessage
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class BulkJobFanOutHandler(
    private val fanOut: BulkJobFanOutService,
    private val objectMapper: ObjectMapper,
) : StreamHandler {
    override val stream = StreamNames.BULK_JOB_REQUESTS
    override val group = StreamNames.SNIPPET_SERVICE_GROUP

    override fun handle(payload: String) {
        fanOut.fanOut(objectMapper.readValue(payload, BulkJobRequestMessage::class.java))
    }
}

@Component
class LintResultHandler(
    private val results: BulkJobResultService,
    private val objectMapper: ObjectMapper,
) : StreamHandler {
    override val stream = StreamNames.LINT_RESULTS
    override val group = StreamNames.SNIPPET_SERVICE_GROUP

    override fun handle(payload: String) {
        results.applyLintResult(objectMapper.readValue(payload, LintResultMessage::class.java))
    }
}

@Component
class FormatResultHandler(
    private val results: BulkJobResultService,
    private val objectMapper: ObjectMapper,
) : StreamHandler {
    override val stream = StreamNames.FORMAT_RESULTS
    override val group = StreamNames.SNIPPET_SERVICE_GROUP

    override fun handle(payload: String) {
        results.applyFormatResult(objectMapper.readValue(payload, FormatResultMessage::class.java))
    }
}

@Component
class TestResultHandler(
    private val results: BulkJobResultService,
    private val objectMapper: ObjectMapper,
) : StreamHandler {
    override val stream = StreamNames.TEST_RESULTS
    override val group = StreamNames.SNIPPET_SERVICE_GROUP

    override fun handle(payload: String) {
        results.applyTestResult(objectMapper.readValue(payload, TestResultMessage::class.java))
    }
}

@Component
class LintDlqHandler(
    private val results: BulkJobResultService,
    private val objectMapper: ObjectMapper,
) : StreamHandler {
    override val stream = StreamNames.LINT_REQUESTS_DLQ
    override val group = StreamNames.SNIPPET_SERVICE_GROUP

    override fun handle(payload: String) {
        results.applyLintDlq(objectMapper.readValue(payload, LintRequestMessage::class.java))
    }
}

@Component
class FormatDlqHandler(
    private val results: BulkJobResultService,
    private val objectMapper: ObjectMapper,
) : StreamHandler {
    override val stream = StreamNames.FORMAT_REQUESTS_DLQ
    override val group = StreamNames.SNIPPET_SERVICE_GROUP

    override fun handle(payload: String) {
        results.applyFormatDlq(objectMapper.readValue(payload, FormatRequestMessage::class.java))
    }
}

@Component
class TestDlqHandler(
    private val results: BulkJobResultService,
    private val objectMapper: ObjectMapper,
) : StreamHandler {
    override val stream = StreamNames.TEST_REQUESTS_DLQ
    override val group = StreamNames.SNIPPET_SERVICE_GROUP

    override fun handle(payload: String) {
        results.applyTestDlq(objectMapper.readValue(payload, TestRequestMessage::class.java))
    }
}
