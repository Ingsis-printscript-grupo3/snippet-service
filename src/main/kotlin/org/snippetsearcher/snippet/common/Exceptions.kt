package org.snippetsearcher.snippet.common

import org.snippetsearcher.snippet.language.SnippetError
import java.util.UUID

// el pedido esta mal armado: falta un campo, el lenguaje no existe, la version no existe
class InvalidRequestException(
    message: String,
) : RuntimeException(message)

// no vino el usuario. Hoy es el header X-User-Id, con Auth0 va a ser el token
class MissingUserException : RuntimeException("Falta el header X-User-Id")

class SnippetNotFoundException(
    id: UUID,
) : RuntimeException("No existe el snippet $id")

// tiene acceso de lectura pero intenta algo que solo puede hacer el owner
class ForbiddenException(
    message: String,
) : RuntimeException(message)

// el snippet no compila: no es un error del protocolo, es lo que hay que mostrarle al usuario
class InvalidSnippetException(
    val errors: List<SnippetError>,
) : RuntimeException("El snippet no es válido")

// un servicio de abajo esta caido o respondio algo inesperado: el usuario no hizo nada mal, puede reintentar
class DownstreamException(
    val service: String,
    cause: Throwable? = null,
) : RuntimeException("$service no responde", cause)
