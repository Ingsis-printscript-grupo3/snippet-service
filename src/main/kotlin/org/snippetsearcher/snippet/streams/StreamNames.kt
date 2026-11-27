package org.snippetsearcher.snippet.streams

object StreamNames {
    const val BULK_JOB_REQUESTS = "bulk-job-requests"

    const val LINT_REQUESTS = "lint-requests"
    const val LINT_RESULTS = "lint-results"
    const val LINT_REQUESTS_DLQ = "lint-requests-dlq"

    const val FORMAT_REQUESTS = "format-requests"
    const val FORMAT_RESULTS = "format-results"
    const val FORMAT_REQUESTS_DLQ = "format-requests-dlq"

    const val TEST_REQUESTS = "test-requests"
    const val TEST_RESULTS = "test-results"
    const val TEST_REQUESTS_DLQ = "test-requests-dlq"

    const val SNIPPET_SERVICE_GROUP = "snippet-service"
}
