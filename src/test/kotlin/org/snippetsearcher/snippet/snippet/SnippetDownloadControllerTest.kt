package org.snippetsearcher.snippet.snippet

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.snippetsearcher.snippet.common.USER_HEADER
import org.snippetsearcher.snippet.rule.RuleService
import org.snippetsearcher.snippet.rule.UpdateRuleRequest
import org.snippetsearcher.snippet.support.FakeCollaborators
import org.snippetsearcher.snippet.support.TestEnvironment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID
import kotlin.test.assertEquals

private const val OWNER = "auth0|download-owner"
private const val READER = "auth0|download-reader"
private const val STRANGER = "auth0|download-stranger"

// US13. El lenguaje falso "formatea" agregando una linea con las reglas que recibio
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeCollaborators::class)
class SnippetDownloadControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val snippets: SnippetService,
    @Autowired private val repository: SnippetRepository,
    @Autowired private val rules: RuleService,
    @Autowired private val environment: TestEnvironment,
) {
    @BeforeEach
    @AfterEach
    fun cleanUp() = environment.reset()

    @Test
    fun `without asking for a version the code comes as it was written, as a file`() {
        val snippet = snippet("saludo", "println(\"año\");")

        val response =
            download(snippet)
                .andExpect {
                    status { isOk() }
                    content { string("println(\"año\");") }
                    content { contentType("text/plain;charset=UTF-8") }
                }.andReturn()
                .response

        assertEquals("saludo.ps", fileNameOf(response.getHeader(HttpHeaders.CONTENT_DISPOSITION)))
    }

    @Test
    fun `the file name keeps accents and drops what a file system rejects`() {
        val snippet = snippet("año: a/b", "println(1);")

        val header = download(snippet).andReturn().response.getHeader(HttpHeaders.CONTENT_DISPOSITION)

        assertEquals("año_ a_b.ps", fileNameOf(header))
    }

    // las reglas son las del owner aunque descargue otro (US12)
    @Test
    fun `a shared user downloads the code formatted with the rules of the owner`() {
        val snippet = snippet("s", "println(1);")
        enableFormatRule(OWNER, "if-brace-same-line")
        enableFormatRule(READER, "if-brace-below-line")
        environment.permissions.share(snippet, READER)

        val response =
            download(snippet, "FORMATTED", READER)
                .andExpect {
                    status { isOk() }
                    content { string("println(1);\n// if-brace-same-line") }
                }.andReturn()
                .response

        assertEquals("s.formatted.ps", fileNameOf(response.getHeader(HttpHeaders.CONTENT_DISPOSITION)))
    }

    @Test
    fun `the version left by the bulk formatting is the one downloaded`() {
        val snippet = snippet("s", "println(1);")
        environment.contents.put("$snippet/formatted", "ya formateado")
        val stored = repository.findById(snippet).get()
        stored.formattedKey = "$snippet/formatted"
        repository.save(stored)

        download(snippet, "FORMATTED").andExpect { content { string("ya formateado") } }
        download(snippet, "ORIGINAL").andExpect { content { string("println(1);") } }
    }

    @Test
    fun `code that cannot be parsed has no formatted version`() {
        val snippet = snippet("s", "println(1);")
        environment.contents.put(snippet.toString(), "error")

        download(snippet, "FORMATTED").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].line") { value(2) }
        }
    }

    @Test
    fun `without access the snippet does not exist and an unknown version is rejected`() {
        val snippet = snippet("s", "println(1);")

        download(snippet, user = STRANGER).andExpect { status { isNotFound() } }
        download(snippet, "COMPILED").andExpect { status { isBadRequest() } }
    }

    private fun snippet(
        name: String,
        content: String,
    ): UUID = snippets.create(OWNER, CreateSnippetRequest(name, "", "printscript", "1.1", content)).id

    private fun enableFormatRule(
        user: String,
        rule: String,
    ) {
        rules.updateRules(user, "format", "printscript", listOf(UpdateRuleRequest(name = rule, isActive = true)))
    }

    private fun download(
        id: UUID,
        version: String? = null,
        user: String = OWNER,
    ) = mockMvc.get("/snippets/$id/download") {
        header(USER_HEADER, user)
        version?.let { param("version", it) }
    }

    private fun fileNameOf(header: String?): String? = ContentDisposition.parse(header!!).filename
}
