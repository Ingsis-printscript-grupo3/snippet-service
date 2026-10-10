package org.snippetsearcher.snippet.language

import org.junit.jupiter.api.Test
import org.snippetsearcher.snippet.common.DownstreamException
import org.snippetsearcher.snippet.common.InvalidRequestException
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PrintScriptLanguageServiceTest {
    private val builder = RestClient.builder().baseUrl("http://printscript")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val service = PrintScriptLanguageService(builder.build())

    @Test
    fun `sends the snippet to validations and reads a valid result`() {
        server
            .expect(requestTo("http://printscript/validations"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.language").value("printscript"))
            .andExpect(jsonPath("$.version").value("1.1"))
            .andExpect(jsonPath("$.content").value("println(1);"))
            .andRespond(withSuccess("""{"valid":true,"errors":[]}""", MediaType.APPLICATION_JSON))

        val result = service.validate("1.1", "println(1);")

        assertTrue(result.valid)
        server.verify()
    }

    @Test
    fun `an invalid snippet comes back with its position`() {
        server
            .expect(requestTo("http://printscript/validations"))
            .andRespond(
                withSuccess(
                    """{"valid":false,"errors":[{"message":"Expected ';'.","line":2,"column":11,"endLine":2,"endColumn":11}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = service.validate("1.1", "let x: number = 5\nprintln(x)")

        assertEquals(listOf(SnippetError("Expected ';'.", 2, 11, 2, 11)), result.errors)
    }

    @Test
    fun `a 400 from the service is an invalid request with its message`() {
        server
            .expect(requestTo("http://printscript/validations"))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":"Unsupported PrintScript version: 9.9"}"""),
            )

        val error = assertFailsWith<InvalidRequestException> { service.validate("9.9", "println(1);") }
        assertEquals("Unsupported PrintScript version: 9.9", error.message)
    }

    @Test
    fun `a 5xx from the service is a downstream failure`() {
        server.expect(requestTo("http://printscript/validations")).andRespond(withServerError())

        val error = assertFailsWith<DownstreamException> { service.validate("1.1", "println(1);") }
        assertEquals("printscript-service", error.service)
    }
}
