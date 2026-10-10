package org.snippetsearcher.snippet.permission

import org.snippetsearcher.snippet.common.callService
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.util.UUID

private const val SERVICE = "permission-service"
private const val RESOURCE_TYPE = "snippet"

// el contrato es el del README del permission-service: todo bajo /internal/permissions
class HttpPermissionClient(
    private val client: RestClient,
) : PermissionClient {
    // el permission-service no duplica: si ya era owner devuelve 200, asi que se puede reintentar
    override fun grantOwner(
        snippetId: UUID,
        userId: String,
    ) {
        callService(SERVICE) {
            client
                .post()
                .uri("/internal/permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(GrantRequest(RESOURCE_TYPE, snippetId.toString(), userId, "OWNER"))
                .retrieve()
                .toBodilessEntity()
        }
    }

    override fun check(
        snippetId: UUID,
        userId: String,
    ): Access =
        callService(SERVICE) {
            client
                .get()
                .uri { uri ->
                    uri
                        .path("/internal/permissions/check")
                        .queryParam("resourceType", RESOURCE_TYPE)
                        .queryParam("resourceId", snippetId)
                        .queryParam("userId", userId)
                        .build()
                }.retrieve()
                .body<CheckResponse>()!!
                .type
        }

    override fun snippetIds(
        userId: String,
        relation: Relation,
    ): List<UUID> =
        callService(SERVICE) {
            client
                .get()
                .uri { uri ->
                    uri
                        .path("/internal/permissions/resources")
                        .queryParam("resourceType", RESOURCE_TYPE)
                        .queryParam("userId", userId)
                        .queryParam("relation", relation)
                        .build()
                }.retrieve()
                .body<List<ResourceResponse>>()!!
                // un id que no es UUID no lo creo este servicio: se ignora
                .mapNotNull { runCatching { UUID.fromString(it.resourceId) }.getOrNull() }
        }

    override fun deleteAll(snippetId: UUID) {
        callService(SERVICE) {
            client
                .delete()
                .uri { uri ->
                    uri
                        .path("/internal/permissions")
                        .queryParam("resourceType", RESOURCE_TYPE)
                        .queryParam("resourceId", snippetId)
                        .build()
                }.retrieve()
                .toBodilessEntity()
        }
    }

    data class GrantRequest(
        val resourceType: String,
        val resourceId: String,
        val userId: String,
        val type: String,
    )

    data class CheckResponse(
        val type: Access,
    )

    data class ResourceResponse(
        val resourceId: String,
        val type: String,
    )
}
