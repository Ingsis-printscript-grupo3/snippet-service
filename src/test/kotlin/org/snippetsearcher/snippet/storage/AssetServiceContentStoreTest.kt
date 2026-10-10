package org.snippetsearcher.snippet.storage

import org.junit.jupiter.api.Test
import org.snippetsearcher.snippet.common.DownstreamException
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssetServiceContentStoreTest {
    private val builder = RestClient.builder().baseUrl("http://asset")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val store = AssetServiceContentStore(builder.build())

    @Test
    fun `put sends the content to the snippets container`() {
        server
            .expect(requestTo("http://asset/v1/asset/snippets/abc"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().string("println(\"hola\");"))
            .andRespond(withStatus(HttpStatus.CREATED))

        store.put("abc", "println(\"hola\");")

        server.verify()
    }

    @Test
    fun `get reads the content as utf-8 whatever the content type`() {
        server
            .expect(requestTo("http://asset/v1/asset/snippets/abc"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("println(\"ñandú\");".toByteArray(), MediaType.APPLICATION_OCTET_STREAM))

        assertEquals("println(\"ñandú\");", store.get("abc"))
    }

    @Test
    fun `deleting something that does not exist is not an error`() {
        server
            .expect(requestTo("http://asset/v1/asset/snippets/abc"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        store.delete("abc")

        server.verify()
    }

    @Test
    fun `a missing asset on get is a downstream failure`() {
        server.expect(requestTo("http://asset/v1/asset/snippets/abc")).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFailsWith<DownstreamException> { store.get("abc") }
    }
}
