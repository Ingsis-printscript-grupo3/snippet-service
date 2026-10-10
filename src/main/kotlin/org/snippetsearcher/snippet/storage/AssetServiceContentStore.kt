package org.snippetsearcher.snippet.storage

import org.snippetsearcher.snippet.common.callService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

private const val SERVICE = "asset-service"
private const val CONTAINER = "snippets"

// el bucket es el asset service de la catedra (austral-ingsis/asset-service), que guarda en Azurite.
// API: PUT, GET y DELETE sobre /v1/asset/{container}/{key}
class AssetServiceContentStore(
    private val client: RestClient,
) : ContentStore {
    override fun put(
        key: String,
        content: String,
    ) {
        callService(SERVICE) {
            client
                .put()
                .uri("/v1/asset/{container}/{key}", CONTAINER, key)
                .contentType(MediaType.TEXT_PLAIN)
                .body(content.toByteArray(Charsets.UTF_8))
                .retrieve()
                .toBodilessEntity()
        }
    }

    // se lee como bytes: el asset service no promete un content type, y el snippet siempre es UTF-8
    override fun get(key: String): String =
        callService(SERVICE) {
            val bytes =
                client
                    .get()
                    .uri("/v1/asset/{container}/{key}", CONTAINER, key)
                    .accept(MediaType.ALL)
                    .retrieve()
                    .body<ByteArray>()
            bytes?.toString(Charsets.UTF_8) ?: ""
        }

    override fun delete(key: String) {
        callService(SERVICE) {
            try {
                client
                    .delete()
                    .uri("/v1/asset/{container}/{key}", CONTAINER, key)
                    .retrieve()
                    .toBodilessEntity()
            } catch (e: HttpClientErrorException) {
                if (e.statusCode != HttpStatus.NOT_FOUND) throw e
            }
        }
    }
}
