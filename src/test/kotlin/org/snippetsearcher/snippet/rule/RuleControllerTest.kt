package org.snippetsearcher.snippet.rule

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.snippetsearcher.snippet.job.BulkJobRepository
import org.snippetsearcher.snippet.streams.StreamNames
import org.snippetsearcher.snippet.support.FakeCollaborators
import org.snippetsearcher.snippet.support.FakeStreamProducer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeCollaborators::class)
class RuleControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val rules: RuleConfigRepository,
    @Autowired private val jobs: BulkJobRepository,
    @Autowired private val producer: FakeStreamProducer,
) {
    @BeforeEach
    fun clean() {
        rules.deleteAll()
        jobs.deleteAll()
        producer.reset()
    }

    @Test
    fun `get format rules returns defaults when user has no overrides`() {
        mockMvc
            .get("/rules/format?language=printscript") {
                header("X-User-Id", "user-1")
            }.andExpect {
                status { isOk() }
                jsonPath("$[?(@.name == 'enforce-spacing-around-equals')].enabled") { value(false) }
            }
    }

    @Test
    fun `put format rules saves overrides per user and enqueues bulk job`() {
        mockMvc
            .put("/rules/format?language=printscript") {
                header("X-User-Id", "user-1")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    [{"name":"enforce-spacing-around-equals","enabled":true},
                     {"name":"line-breaks-after-println","enabled":true,"value":"1"}]
                    """.trimIndent()
            }.andExpect {
                status { isAccepted() }
                jsonPath("$.jobId") { exists() }
            }

        assertEquals(1, producer.published.size)
        assertEquals(StreamNames.BULK_JOB_REQUESTS, producer.published.first().first)

        mockMvc
            .get("/rules/format?language=printscript") {
                header("X-User-Id", "user-2")
            }.andExpect {
                status { isOk() }
                jsonPath("$[?(@.name == 'enforce-spacing-around-equals')].enabled") { value(false) }
            }
    }

    @Test
    fun `put rejects unknown rule, out of range value, and incompatible rules`() {
        mockMvc
            .put("/rules/format") {
                header("X-User-Id", "user-1")
                contentType = MediaType.APPLICATION_JSON
                content = """[{"name":"unknown-rule","enabled":true}]"""
            }.andExpect { status { isBadRequest() } }

        mockMvc
            .put("/rules/format") {
                header("X-User-Id", "user-1")
                contentType = MediaType.APPLICATION_JSON
                content = """[{"name":"line-breaks-after-println","enabled":true,"value":"5"}]"""
            }.andExpect { status { isBadRequest() } }

        mockMvc
            .put("/rules/format") {
                header("X-User-Id", "user-1")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    [{"name":"if-brace-same-line","enabled":true},
                     {"name":"if-brace-below-line","enabled":true}]
                    """.trimIndent()
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `put lint rules validates enum values`() {
        mockMvc
            .put("/rules/lint") {
                header("X-User-Id", "user-1")
                contentType = MediaType.APPLICATION_JSON
                content = """[{"name":"identifier_format","enabled":true,"value":"kebab case"}]"""
            }.andExpect { status { isBadRequest() } }

        mockMvc
            .put("/rules/lint") {
                header("X-User-Id", "user-1")
                contentType = MediaType.APPLICATION_JSON
                content = """[{"name":"identifier_format","enabled":true,"value":"snake case"}]"""
            }.andExpect { status { isAccepted() } }
    }
}
