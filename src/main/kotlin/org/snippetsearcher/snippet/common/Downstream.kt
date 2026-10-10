package org.snippetsearcher.snippet.common

import org.springframework.web.client.RestClientException

// cualquier falla de red o respuesta que el cliente no supo manejar termina en un 502.
// Los 4xx que significan algo para el dominio los traduce cada cliente antes de llegar aca.
inline fun <T> callService(
    service: String,
    call: () -> T,
): T =
    try {
        call()
    } catch (e: RestClientException) {
        throw DownstreamException(service, e)
    }
