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
    fun `get formatting rules returns defaults in the ui shape`() {
        mockMvc
            .get("/rules/formatting?language=printscript") {
                header("X-User-Id", "user-1")
            }.andExpect {
                status { isOk() }
                jsonPath(
                    "$[?(@.name == 'enforce-spacing-around-equals')].id",
                ) { value("enforce-spacing-around-equals") }
                jsonPath("$[?(@.name == 'enforce-spacing-around-equals')].isActive") { value(false) }
                // reglas enteras salen como number para que la UI muestre el campo numérico
                jsonPath("$[?(@.name == 'line-breaks-after-println')].value") { value(0) }
            }
    }

    @Test
    fun `format and lint stay as aliases of formatting and linting`() {
        mockMvc
            .get("/rules/format") { header("X-User-Id", "user-1") }
            .andExpect { status { isOk() } }
        mockMvc
            .get("/rules/lint") { header("X-User-Id", "user-1") }
            .andExpect { status { isOk() } }
        mockMvc
            .get("/rules/unknown") { header("X-User-Id", "user-1") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `put formatting rules returns saved rules, job location, and enqueues bulk job per user`() {
        mockMvc
            .put("/rules/formatting?language=printscript") {
                header("X-User-Id", "user-1")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    [{"id":"enforce-spacing-around-equals","name":"enforce-spacing-around-equals","isActive":true},
                     {"id":"line-breaks-after-println","name":"line-breaks-after-println","isActive":true,"value":1}]
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                header { exists(JOB_ID_HEADER) }
                header { string("Location", org.hamcrest.Matchers.startsWith("/jobs/")) }
                jsonPath("$[?(@.name == 'enforce-spacing-around-equals')].isActive") { value(true) }
                jsonPath("$[?(@.name == 'line-breaks-after-println')].value") { value(1) }
            }

        assertEquals(1, producer.published.size)
        assertEquals(StreamNames.BULK_JOB_REQUESTS, producer.published.first().first)

        mockMvc
            .get("/rules/formatting?language=printscript") {
                header("X-User-Id", "user-2")
            }.andExpect {
                status { isOk() }
                jsonPath("$[?(@.name == 'enforce-spacing-around-equals')].isActive") { value(false) }
            }
    }

    @Test
    fun `put rejects unknown rule, out of range value, and incompatible rules`() {
        mockMvc
            .put("/rules/formatting") {
                header("X-User-Id", "user-1")
                contentType = MediaType.APPLICATION_JSON
                content = """[{"name":"unknown-rule","isActive":true}]"""
            }.andExpect { status { isBadRequest() } }

        mockMvc
            .put("/rules/formatting") {
                header("X-User-Id", "user-1")
                contentType = MediaType.APPLICATION_JSON
                content = """[{"name":"line-breaks-after-println","isActive":true,"value":5}]"""
            }.andExpect { status { isBadRequest() } }

        mockMvc
            .put("/rules/formatting") {
                header("X-User-Id", "user-1")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    [{"name":"if-brace-same-line","isActive":true},
                     {"name":"if-brace-below-line","isActive":true}]
                    """.trimIndent()
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `put linting rules validates enum values`() {
        mockMvc
            .put("/rules/linting") {
                header("X-User-Id", "user-1")
                contentType = MediaType.APPLICATION_JSON
                content = """[{"name":"identifier_format","isActive":true,"value":"kebab case"}]"""
            }.andExpect { status { isBadRequest() } }

        mockMvc
            .put("/rules/linting") {
                header("X-User-Id", "user-1")
                contentType = MediaType.APPLICATION_JSON
                content = """[{"name":"identifier_format","isActive":true,"value":"snake case"}]"""
            }.andExpect {
                status { isOk() }
                jsonPath("$[?(@.name == 'identifier_format')].value") { value("snake case") }
            }
    }
}
