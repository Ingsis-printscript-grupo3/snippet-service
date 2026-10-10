package org.snippetsearcher.snippet.language

import org.snippetsearcher.snippet.common.InvalidRequestException
import org.snippetsearcher.snippet.common.callService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

const val PRINTSCRIPT = "printscript"

// habla con el printscript-service. Un snippet invalido vuelve 200 con valid=false;
// un 400 es que pedimos algo imposible, como una version que no existe
class PrintScriptLanguageService(
    private val client: RestClient,
) : LanguageService {
    override fun validate(
        version: String,
        content: String,
    ): ValidationResult =
        callService("printscript-service") {
            try {
                client
                    .post()
                    .uri("/validations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ValidationRequest(PRINTSCRIPT, version, content))
                    .retrieve()
                    .body<ValidationResult>()!!
            } catch (e: HttpClientErrorException) {
                if (e.statusCode != HttpStatus.BAD_REQUEST) throw e
                val message = runCatching { e.getResponseBodyAs(RemoteError::class.java)?.error }.getOrNull()
                throw InvalidRequestException(message ?: "printscript-service rechazó el pedido")
            }
        }

    data class ValidationRequest(
        val language: String,
        val version: String,
        val content: String,
    )

    data class RemoteError(
        val error: String,
    )
}
