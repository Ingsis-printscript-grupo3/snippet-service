package org.snippetsearcher.snippet.snippet

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.snippetsearcher.snippet.common.USER_HEADER
import org.snippetsearcher.snippet.permission.Access
import org.snippetsearcher.snippet.support.FakeCollaborators
import org.snippetsearcher.snippet.support.FakeContentStore
import org.snippetsearcher.snippet.support.FakePermissionClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val OWNER = "auth0|owner"
private const val READER = "auth0|reader"
private const val STRANGER = "auth0|stranger"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeCollaborators::class)
class SnippetControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val service: SnippetService,
    @Autowired private val repository: SnippetRepository,
    @Autowired private val permissions: FakePermissionClient,
    @Autowired private val contents: FakeContentStore,
) {
    @BeforeEach
    fun cleanUp() {
        repository.deleteAll()
        permissions.reset()
        contents.contents.clear()
    }

    @Test
    fun `creating a valid snippet from the editor stores it and makes the user its owner`() {
        val json =
            """{"name":"suma","description":"suma dos numeros","language":"printscript",""" +
                """"version":"1.1","content":"let x: number = 5;"}"""

        val response =
            mockMvc
                .post("/snippets") {
                    header(USER_HEADER, OWNER)
                    contentType = MediaType.APPLICATION_JSON
                    content = json
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.name") { value("suma") }
                    jsonPath("$.ownerId") { value(OWNER) }
                    jsonPath("$.content") { value("let x: number = 5;") }
                }.andReturn()

        val id = UUID.fromString(JsonPath.read(response.response.contentAsString, "$.id"))
        assertTrue(repository.existsById(id))
        assertEquals("let x: number = 5;", contents.contents[id.toString()])
        assertEquals(Access.OWNER, permissions.accessOf(id, OWNER))
    }

    @Test
    fun `creating a snippet from a file uses the file as its content`() {
        mockMvc
            .multipart("/snippets") {
                file(MockMultipartFile("file", "suma.ps", "text/plain", "println(1);".toByteArray()))
                param("name", "suma")
                param("language", "printscript")
                param("version", "1.0")
                header(USER_HEADER, OWNER)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.content") { value("println(1);") }
                jsonPath("$.description") { value("") }
            }
    }

    @Test
    fun `an invalid snippet returns 422 with the rule line and column and stores nothing`() {
        mockMvc
            .post("/snippets") {
                header(USER_HEADER, OWNER)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"roto","language":"printscript","version":"1.1","content":"error"}"""
            }.andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.errors.length()") { value(1) }
                jsonPath("$.errors[0].message") { value("Expected ';'.") }
                jsonPath("$.errors[0].line") { value(2) }
                jsonPath("$.errors[0].column") { value(11) }
            }

        assertEquals(0, repository.count())
        assertTrue(contents.contents.isEmpty())
    }

    @Test
    fun `an unsupported language returns 400`() {
        mockMvc
            .post("/snippets") {
                header(USER_HEADER, OWNER)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"x","language":"python","version":"3","content":"print(1)"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `a blank name returns 400`() {
        mockMvc
            .post("/snippets") {
                header(USER_HEADER, OWNER)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":" ","language":"printscript","version":"1.1","content":"println(1);"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("El campo 'name' no puede estar vacío") }
            }
    }

    @Test
    fun `a request without user returns 401`() {
        mockMvc.get("/snippets").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `if the permission cannot be granted nothing is left behind and it returns 502`() {
        permissions.failGrant = true

        mockMvc
            .post("/snippets") {
                header(USER_HEADER, OWNER)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"x","language":"printscript","version":"1.1","content":"println(1);"}"""
            }.andExpect {
                status { isBadGateway() }
                jsonPath("$.error") { value("permission-service no responde") }
            }

        assertEquals(0, repository.count())
        assertTrue(contents.contents.isEmpty())
    }

    @Test
    fun `the owner and a shared user can see a snippet but a stranger gets 404`() {
        val id = createSnippet("suma", "println(1);")
        permissions.share(id, READER)

        for (user in listOf(OWNER, READER)) {
            mockMvc
                .get("/snippets/$id") { header(USER_HEADER, user) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.content") { value("println(1);") }
                }
        }
        mockMvc.get("/snippets/$id") { header(USER_HEADER, STRANGER) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `the owner can update the content from the editor`() {
        val id = createSnippet("suma", "println(1);")

        mockMvc
            .patch("/snippets/$id") {
                header(USER_HEADER, OWNER)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"suma nueva","content":"println(2);"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("suma nueva") }
                jsonPath("$.content") { value("println(2);") }
            }

        assertEquals("println(2);", contents.contents[id.toString()])
        assertEquals("suma nueva", repository.findById(id).get().name)
    }

    @Test
    fun `the owner can update the content from a file`() {
        val id = createSnippet("suma", "println(1);")

        mockMvc
            .multipart(HttpMethod.PATCH, "/snippets/$id") {
                file(MockMultipartFile("file", "suma.ps", "text/plain", "println(3);".toByteArray()))
                header(USER_HEADER, OWNER)
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("suma") }
                jsonPath("$.content") { value("println(3);") }
            }
    }

    @Test
    fun `an invalid update returns 422 and keeps the previous content`() {
        val id = createSnippet("suma", "println(1);")

        mockMvc
            .patch("/snippets/$id") {
                header(USER_HEADER, OWNER)
                contentType = MediaType.APPLICATION_JSON
                content = """{"content":"error"}"""
            }.andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.errors[0].line") { value(2) }
            }

        assertEquals("println(1);", contents.contents[id.toString()])
    }

    @Test
    fun `a shared user cannot update or delete the snippet`() {
        val id = createSnippet("suma", "println(1);")
        permissions.share(id, READER)

        mockMvc
            .patch("/snippets/$id") {
                header(USER_HEADER, READER)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"mio"}"""
            }.andExpect { status { isForbidden() } }
        mockMvc.delete("/snippets/$id") { header(USER_HEADER, READER) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `the list filters by relation and name and is sorted by name`() {
        val mine = createSnippet("b suma", "println(1);")
        createSnippet("a resta", "println(2);")
        val shared = createSnippet("c suma ajena", "println(3);", owner = STRANGER)
        permissions.share(shared, OWNER)

        mockMvc
            .get("/snippets") {
                header(USER_HEADER, OWNER)
                param("relation", "ALL")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(3) }
                jsonPath("$[0].name") { value("a resta") }
            }
        mockMvc
            .get("/snippets") {
                header(USER_HEADER, OWNER)
                param("relation", "OWNED")
                param("name", "SUMA")
            }.andExpect {
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].id") { value(mine.toString()) }
            }
        mockMvc
            .get("/snippets") {
                header(USER_HEADER, OWNER)
                param("relation", "SHARED")
            }.andExpect {
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].id") { value(shared.toString()) }
            }
    }

    @Test
    fun `the owner can delete a snippet and it disappears from everywhere`() {
        val id = createSnippet("suma", "println(1);")

        mockMvc.delete("/snippets/$id") { header(USER_HEADER, OWNER) }.andExpect { status { isNoContent() } }

        assertNull(repository.findById(id).orElse(null))
        assertTrue(contents.contents.isEmpty())
        assertEquals(Access.NONE, permissions.accessOf(id, OWNER))
    }

    private fun createSnippet(
        name: String,
        content: String,
        owner: String = OWNER,
    ): UUID = service.create(owner, CreateSnippetRequest(name, "", "printscript", "1.1", content)).id
}
