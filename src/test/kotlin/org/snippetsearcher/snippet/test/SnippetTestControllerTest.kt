package org.snippetsearcher.snippet.test

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.snippetsearcher.snippet.common.USER_HEADER
import org.snippetsearcher.snippet.snippet.CreateSnippetRequest
import org.snippetsearcher.snippet.snippet.SnippetService
import org.snippetsearcher.snippet.streams.StreamNames
import org.snippetsearcher.snippet.streams.TestRequestMessage
import org.snippetsearcher.snippet.support.FakeCollaborators
import org.snippetsearcher.snippet.support.TestEnvironment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID
import kotlin.test.assertEquals

private const val OWNER = "auth0|tests-owner"
private const val READER = "auth0|tests-reader"
private const val STRANGER = "auth0|tests-stranger"
private const val SUM = """{"name":"saluda","input":["a","b"],"output":["a","b"]}"""

// US8 y US9. El lenguaje falso imprime los inputs que recibe, y falla si el codigo dice "explota"
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeCollaborators::class)
class SnippetTestControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val snippets: SnippetService,
    @Autowired private val tests: SnippetTestRepository,
    @Autowired private val environment: TestEnvironment,
) {
    @BeforeEach
    @AfterEach
    fun cleanUp() = environment.reset()

    @Test
    fun `the owner creates a test and sees it in the list`() {
        val snippet = snippet()

        createTest(snippet, SUM)

        mockMvc.get("/snippets/$snippet/tests") { header(USER_HEADER, OWNER) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].name") { value("saluda") }
            jsonPath("$[0].input[1]") { value("b") }
            jsonPath("$[0].output[0]") { value("a") }
            jsonPath("$[0].status") { value("PENDING") }
        }
    }

    @Test
    fun `a shared user can see and run the tests but not change them`() {
        val snippet = snippet()
        val test = createTest(snippet, SUM)
        environment.permissions.share(snippet, READER)

        mockMvc.get("/snippets/$snippet/tests") { header(USER_HEADER, READER) }.andExpect { status { isOk() } }
        run(snippet, """{"input":["a"],"output":["a"]}""", READER).andExpect { status { isOk() } }
        mockMvc
            .post("/snippets/$snippet/tests") {
                header(USER_HEADER, READER)
                contentType = MediaType.APPLICATION_JSON
                content = SUM
            }.andExpect { status { isForbidden() } }
        mockMvc
            .put("/snippets/$snippet/tests/$test") {
                header(USER_HEADER, READER)
                contentType = MediaType.APPLICATION_JSON
                content = SUM
            }.andExpect { status { isForbidden() } }
        mockMvc
            .delete("/snippets/$snippet/tests/$test") { header(USER_HEADER, READER) }
            .andExpect { status { isForbidden() } }
        assertEquals(1, tests.count())
    }

    @Test
    fun `without access to the snippet its tests do not exist`() {
        val snippet = snippet()
        createTest(snippet, SUM)

        mockMvc.get("/snippets/$snippet/tests") { header(USER_HEADER, STRANGER) }.andExpect { status { isNotFound() } }
        run(snippet, """{"input":[],"output":[]}""", STRANGER).andExpect { status { isNotFound() } }
    }

    @Test
    fun `running a test says whether the outputs are the expected ones in order`() {
        val snippet = snippet()

        run(snippet, """{"input":["a","b"],"output":["a","b"]}""").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("PASSED") }
            jsonPath("$.outputs.length()") { value(2) }
        }
        run(snippet, """{"input":["a","b"],"output":["b","a"]}""").andExpect {
            jsonPath("$.status") { value("FAILED") }
        }
        // correr no guarda nada: se puede probar un formulario antes de guardarlo
        assertEquals(0, tests.count())
    }

    @Test
    fun `a snippet that fails while running gives error with what it managed to print`() {
        val snippet = snippet("println(explota);")

        run(snippet, """{"input":["a","b"],"output":["a"]}""").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ERROR") }
            jsonPath("$.outputs[0]") { value("a") }
            jsonPath("$.errors[0].message") { value("Division by zero.") }
        }
    }

    @Test
    fun `updating a test discards the result of the previous values`() {
        val snippet = snippet()
        val test = createTest(snippet, SUM)
        val stored = tests.findById(test).get()
        stored.status = TestStatus.PASSED
        stored.lastOutputJson = """["a","b"]"""
        tests.save(stored)

        mockMvc
            .put("/snippets/$snippet/tests/$test") {
                header(USER_HEADER, OWNER)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"otro","input":["c"],"output":["d"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("otro") }
                jsonPath("$.status") { value("PENDING") }
                jsonPath("$.lastOutput") { value(null) }
            }
    }

    @Test
    fun `a test of another snippet cannot be reached through this one`() {
        val mine = snippet()
        val other = createTest(snippet(), SUM)

        mockMvc
            .delete("/snippets/$mine/tests/$other") { header(USER_HEADER, OWNER) }
            .andExpect { status { isNotFound() } }
        assertEquals(1, tests.count())
    }

    @Test
    fun `the owner deletes a test and deleting the snippet takes the rest with it`() {
        val snippet = snippet()
        val first = createTest(snippet, SUM)
        createTest(snippet, SUM)

        mockMvc
            .delete("/snippets/$snippet/tests/$first") { header(USER_HEADER, OWNER) }
            .andExpect { status { isNoContent() } }
        assertEquals(1, tests.count())

        snippets.delete(OWNER, snippet)
        assertEquals(0, tests.count())
    }

    @Test
    fun `a test without name is rejected`() {
        mockMvc
            .post("/snippets/${snippet()}/tests") {
                header(USER_HEADER, OWNER)
                contentType = MediaType.APPLICATION_JSON
                content = """{"input":["a"]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("El campo 'name' no puede estar vacío") }
            }
    }

    // US16: lo que se crea por aca es lo que se encola al editar el snippet
    @Test
    fun `editing the snippet queues the tests created through the api`() {
        val snippet = snippet()
        val test = createTest(snippet, SUM)

        mockMvc
            .patch("/snippets/$snippet") {
                header(USER_HEADER, OWNER)
                contentType = MediaType.APPLICATION_JSON
                content = """{"content":"println(2);"}"""
            }.andExpect { status { isOk() } }

        val queued =
            environment.producer.published
                .filter { it.first == StreamNames.TEST_REQUESTS }
                .map { it.second as TestRequestMessage }
        assertEquals(listOf(test), queued.map { it.testId })
        assertEquals(listOf("a", "b"), queued.single().inputs)
        assertEquals(listOf("a", "b"), queued.single().expectedOutputs)
    }

    private fun snippet(content: String = "println(1);"): UUID =
        snippets.create(OWNER, CreateSnippetRequest("s", "", "printscript", "1.1", content)).id

    private fun createTest(
        snippetId: UUID,
        body: String,
    ): UUID {
        val response =
            mockMvc
                .post("/snippets/$snippetId/tests") {
                    header(USER_HEADER, OWNER)
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                }.andExpect { status { isCreated() } }
                .andReturn()
        return UUID.fromString(JsonPath.read(response.response.contentAsString, "$.id"))
    }

    private fun run(
        snippetId: UUID,
        body: String,
        user: String = OWNER,
    ) = mockMvc.post("/snippets/$snippetId/tests/run") {
        header(USER_HEADER, user)
        contentType = MediaType.APPLICATION_JSON
        content = body
    }
}
