package org.snippetsearcher.snippet.test

import org.snippetsearcher.snippet.snippet.Snippet
import org.snippetsearcher.snippet.streams.StreamNames
import org.snippetsearcher.snippet.streams.StreamProducer
import org.snippetsearcher.snippet.streams.TestRequestMessage
import org.snippetsearcher.snippet.streams.publishAfterCommit
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Service
class TestTriggerService(
    private val tests: SnippetTestRepository,
    private val producer: StreamProducer,
    private val objectMapper: ObjectMapper,
) {
    fun enqueueForSnippet(
        snippet: Snippet,
        userId: String,
        content: String,
    ) {
        tests.findBySnippetId(snippet.id).forEach { test ->
            test.status = TestStatus.PENDING
            val inputs: List<String> = objectMapper.readValue(test.inputsJson)
            val expected: List<String> = objectMapper.readValue(test.expectedOutputsJson)
            producer.publishAfterCommit(
                StreamNames.TEST_REQUESTS,
                TestRequestMessage(
                    userId = userId,
                    snippetId = snippet.id,
                    snippetVersion = snippet.updatedAt,
                    testId = test.id,
                    language = snippet.language,
                    version = snippet.version,
                    content = content,
                    inputs = inputs,
                    expectedOutputs = expected,
                ),
            )
        }
    }
}
