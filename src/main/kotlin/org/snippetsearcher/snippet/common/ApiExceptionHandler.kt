package org.snippetsearcher.snippet.common

import org.slf4j.LoggerFactory
import org.snippetsearcher.snippet.language.SnippetError
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.support.MissingServletRequestPartException

// traduce las excepciones a status HTTP en un solo lugar, para todos los controllers
@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidRequest(e: InvalidRequestException): ErrorResponse = ErrorResponse(e.message ?: "Pedido inválido")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun unreadableBody(e: HttpMessageNotReadableException): ErrorResponse =
        ErrorResponse("El body no es válido: tiene que traer name, language, version y content")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun missingParameter(e: MissingServletRequestParameterException): ErrorResponse =
        ErrorResponse("Falta el campo '${e.parameterName}'")

    @ExceptionHandler(MissingServletRequestPartException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun missingPart(e: MissingServletRequestPartException): ErrorResponse =
        ErrorResponse("Falta el archivo '${e.requestPartName}'")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun typeMismatch(e: MethodArgumentTypeMismatchException): ErrorResponse =
        ErrorResponse("El valor '${e.value}' no es válido para '${e.name}'")

    @ExceptionHandler(MissingUserException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun missingUser(e: MissingUserException): ErrorResponse = ErrorResponse(e.message!!)

    @ExceptionHandler(ForbiddenException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun forbidden(e: ForbiddenException): ErrorResponse = ErrorResponse(e.message!!)

    @ExceptionHandler(SnippetNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: SnippetNotFoundException): ErrorResponse = ErrorResponse(e.message!!)

    // 422 y no 400: el pedido esta bien armado, lo que no cumple las reglas es el codigo del snippet
    @ExceptionHandler(InvalidSnippetException::class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    fun invalidSnippet(e: InvalidSnippetException): InvalidSnippetResponse =
        InvalidSnippetResponse(e.message!!, e.errors)

    @ExceptionHandler(DownstreamException::class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    fun downstream(e: DownstreamException): ErrorResponse {
        log.warn("Fallo la llamada a {}", e.service, e)
        return ErrorResponse(e.message!!)
    }
}

data class ErrorResponse(
    val error: String,
)

// errors hoy trae un solo elemento: el Engine de PrintScript corta en el primer error
data class InvalidSnippetResponse(
    val error: String,
    val errors: List<SnippetError>,
)
