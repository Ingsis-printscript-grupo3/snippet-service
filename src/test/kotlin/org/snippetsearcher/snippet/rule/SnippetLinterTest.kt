package org.snippetsearcher.snippet.rule

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.snippetsearcher.snippet.common.USER_HEADER
import org.snippetsearcher.snippet.snippet.SnippetRepository
import org.snippetsearcher.snippet.support.FakeCollaborators
import org.snippetsearcher.snippet.support.TestEnvironment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ADMIN = "auth0|lint-admin"
private const val OTHER = "auth0|lint-other"
private const val SNAKE_CASE_SNIPPET = "let mi_var: number = 1;"
private const val CAMEL_CASE_SNIPPET = "let miVar: number = 1;"

// US 14: un snippet se evalua con las reglas de lint de su owner, tambien cuando se crea o se edita
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeCollaborators::class, TestEnvironment::class)
class SnippetLinterTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val snippets: SnippetRepository,
    @Autowired private val violations: LintViolationRepository,
    @Autowired private val environment: TestEnvironment,
) {
    // tambien al terminar: las reglas guardadas cambiarian el linteo de los tests de otras clases
    @BeforeEach
    @AfterEach
    fun cleanUp() = environment.reset()

    @Test
    fun `a snippet is linted with the rules of its owner instead of the default ones`() {
        putLintRules(ADMIN, """[{"name":"identifier_format","isActive":true,"value":"snake case"}]""")

        assertEquals("COMPLIANT", lintStatusOf(createSnippet(ADMIN, SNAKE_CASE_SNIPPET)))
        assertEquals("NOT_COMPLIANT", lintStatusOf(createSnippet(ADMIN, CAMEL_CASE_SNIPPET)))
        // otro usuario no configuro nada: le corre el camel case predeterminado
        assertEquals("NOT_COMPLIANT", lintStatusOf(createSnippet(OTHER, SNAKE_CASE_SNIPPET)))
    }

    @Test
    fun `with every lint rule disabled any snippet complies`() {
        putLintRules(
            ADMIN,
            """
            [{"name":"identifier_format","isActive":false},
             {"name":"mandatory-variable-or-literal-in-println","isActive":false},
             {"name":"mandatory-variable-or-literal-in-readInput","isActive":false}]
            """.trimIndent(),
        )

        assertEquals("COMPLIANT", lintStatusOf(createSnippet(ADMIN, SNAKE_CASE_SNIPPET)))
    }

    @Test
    fun `each evaluation keeps only its own violations`() {
        val id = idOf(createSnippet(ADMIN, SNAKE_CASE_SNIPPET))
        assertEquals(listOf("Identifier must be camel case"), violations.findBySnippetId(id).map { it.message })

        assertEquals("COMPLIANT", lintStatusOf(patchContent(id, CAMEL_CASE_SNIPPET)))

        assertTrue(violations.findBySnippetId(id).isEmpty())
    }

    @Test
    fun `if the language service cannot tell the rules the snippet is still saved as pending`() {
        environment.language.failRules = true

        assertEquals("PENDING", lintStatusOf(createSnippet(ADMIN, SNAKE_CASE_SNIPPET)))
        assertEquals(1, snippets.count())
    }

    @Test
    fun `when an edit cannot be linted the violations of the old content are dropped`() {
        val id = idOf(createSnippet(ADMIN, SNAKE_CASE_SNIPPET))
        environment.language.failLint = true

        assertEquals("PENDING", lintStatusOf(patchContent(id, "let otra_var: number = 2;")))

        assertTrue(violations.findBySnippetId(id).isEmpty())
    }

    private fun putLintRules(
        userId: String,
        body: String,
    ) {
        mockMvc
            .put("/rules/linting") {
                header(USER_HEADER, userId)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isOk() } }
    }

    private fun createSnippet(
        userId: String,
        code: String,
    ): String =
        mockMvc
            .post("/snippets") {
                header(USER_HEADER, userId)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"snippet","language":"printscript","version":"1.1","content":"$code"}"""
            }.andExpect { status { isCreated() } }
            .andReturn()
            .response.contentAsString

    private fun patchContent(
        id: UUID,
        code: String,
    ): String =
        mockMvc
            .patch("/snippets/$id") {
                header(USER_HEADER, ADMIN)
                contentType = MediaType.APPLICATION_JSON
                content = """{"content":"$code"}"""
            }.andExpect { status { isOk() } }
            .andReturn()
            .response.contentAsString

    private fun lintStatusOf(response: String): String = JsonPath.read(response, "$.lintStatus")

    private fun idOf(response: String): UUID = UUID.fromString(JsonPath.read(response, "$.id"))
}
