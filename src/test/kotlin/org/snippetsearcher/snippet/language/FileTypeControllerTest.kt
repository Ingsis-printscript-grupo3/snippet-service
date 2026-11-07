package org.snippetsearcher.snippet.language

import org.junit.jupiter.api.Test
import org.snippetsearcher.snippet.support.FakeCollaborators
import org.snippetsearcher.snippet.support.OTHER_LANGUAGE
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeCollaborators::class)
class FileTypeControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    // no manda X-User-Id: la lista de lenguajes no depende de quien pregunta
    @Test
    fun `lists every supported language with its extension`() {
        mockMvc.get("/file-types").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].language") { value(PRINTSCRIPT) }
            jsonPath("$[0].extension") { value("ps") }
            jsonPath("$[1].language") { value(OTHER_LANGUAGE) }
        }
    }
}
