package org.snippetsearcher.snippet.permission

import org.junit.jupiter.api.Test
import org.snippetsearcher.snippet.common.DownstreamException
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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpPermissionClientTest {
    private val builder = RestClient.builder().baseUrl("http://permission")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = HttpPermissionClient(builder.build())
    private val snippetId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun `granting the owner posts an OWNER permission over the snippet`() {
        server
            .expect(requestTo("http://permission/internal/permissions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.resourceType").value("snippet"))
            .andExpect(jsonPath("$.resourceId").value(snippetId.toString()))
            .andExpect(jsonPath("$.userId").value("u1"))
            .andExpect(jsonPath("$.type").value("OWNER"))
            .andRespond(withStatus(HttpStatus.CREATED))

        client.grantOwner(snippetId, "u1")

        server.verify()
    }

    @Test
    fun `check reads the access type`() {
        server
            .expect(
                requestTo(
                    "http://permission/internal/permissions/check?resourceType=snippet&resourceId=$snippetId&userId=u1",
                ),
            ).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"type":"READ"}""", MediaType.APPLICATION_JSON))

        assertEquals(Access.READ, client.check(snippetId, "u1"))
    }

    @Test
    fun `snippetIds asks for the resources of the user by relation`() {
        server
            .expect(
                requestTo(
                    "http://permission/internal/permissions/resources?resourceType=snippet&userId=u1&relation=SHARED",
                ),
            ).andRespond(
                withSuccess("""[{"resourceId":"$snippetId","type":"READ"}]""", MediaType.APPLICATION_JSON),
            )

        assertEquals(listOf(snippetId), client.snippetIds("u1", Relation.SHARED))
    }

    @Test
    fun `deleteAll deletes every permission over the snippet`() {
        server
            .expect(requestTo("http://permission/internal/permissions?resourceType=snippet&resourceId=$snippetId"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.NO_CONTENT))

        client.deleteAll(snippetId)

        server.verify()
    }

    @Test
    fun `a 5xx is a downstream failure`() {
        server.expect(requestTo("http://permission/internal/permissions")).andRespond(withServerError())

        assertFailsWith<DownstreamException> { client.grantOwner(snippetId, "u1") }
    }
}
