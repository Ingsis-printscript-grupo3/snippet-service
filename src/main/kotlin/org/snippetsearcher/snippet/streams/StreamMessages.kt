package org.snippetsearcher.snippet.streams

import org.snippetsearcher.snippet.language.LintWarning
import org.snippetsearcher.snippet.language.SnippetError
import java.time.Instant
import java.util.UUID

data class BulkJobRequestMessage(
    val jobId: UUID,
    val userId: String,
    val type: String,
    val language: String,
)

data class LintRequestMessage(
    val jobId: UUID? = null,
    val userId: String,
    val snippetId: UUID,
    val snippetVersion: Instant,
    val language: String,
    val version: String,
    val content: String,
    val rules: Map<String, Any?> = emptyMap(),
)

data class LintResultMessage(
    val jobId: UUID? = null,
    val snippetId: UUID,
    val snippetVersion: Instant,
    val warnings: List<LintWarning>,
    val errors: List<SnippetError>,
)

data class FormatRequestMessage(
    val jobId: UUID? = null,
    val userId: String,
    val snippetId: UUID,
    val snippetVersion: Instant,
    val language: String,
    val version: String,
    val content: String,
    val rules: Map<String, Any?> = emptyMap(),
)

data class FormatResultMessage(
    val jobId: UUID? = null,
    val snippetId: UUID,
    val snippetVersion: Instant,
    val formattedContent: String?,
    val errors: List<SnippetError>,
)

data class TestRequestMessage(
    val jobId: UUID? = null,
    val userId: String,
    val snippetId: UUID,
    val snippetVersion: Instant,
    val testId: UUID,
    val language: String,
    val version: String,
    val content: String,
    val inputs: List<String> = emptyList(),
    val expectedOutputs: List<String> = emptyList(),
)

data class TestResultMessage(
    val jobId: UUID? = null,
    val snippetId: UUID,
    val snippetVersion: Instant,
    val testId: UUID,
    val status: String,
    val outputs: List<String>,
    val error: SnippetError? = null,
)
