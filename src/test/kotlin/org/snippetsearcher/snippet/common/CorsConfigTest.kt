package org.snippetsearcher.snippet.common

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.options

private const val UI_ORIGIN = "http://localhost:5173"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigTest(
    @Autowired private val mockMvc: MockMvc,
) {
    // el navegador manda este OPTIONS antes de un PATCH con X-User-Id: si no lo aprueban, no manda el pedido de verdad
    @Test
    fun `the preflight of the UI is allowed`() {
        mockMvc
            .options("/snippets") {
                header(HttpHeaders.ORIGIN, UI_ORIGIN)
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-user-id")
            }.andExpect {
                status { isOk() }
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, UI_ORIGIN) }
            }
    }

    @Test
    fun `another origin is rejected`() {
        mockMvc
            .options("/snippets") {
                header(HttpHeaders.ORIGIN, "http://sitio-ajeno.com")
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            }.andExpect {
                status { isForbidden() }
            }
    }
}
