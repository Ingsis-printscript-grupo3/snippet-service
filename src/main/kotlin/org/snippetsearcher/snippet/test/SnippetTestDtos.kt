package org.snippetsearcher.snippet.test

import org.snippetsearcher.snippet.common.InvalidRequestException
import org.snippetsearcher.snippet.language.SnippetError
import java.time.Instant
import java.util.UUID

// el largo de la columna name
private const val MAX_NAME_LENGTH = 255

// US8: input son las entradas en orden y output lo que se espera que imprima, tambien en orden.
// Se llaman como en el TestCase de la UI de la catedra
data class SnippetTestRequest(
    val name: String = "",
    val input: List<String> = emptyList(),
    val output: List<String> = emptyList(),
) {
    fun requireValidName() {
        if (name.isBlank()) throw InvalidRequestException("El campo 'name' no puede estar vacío")
        if (name.length > MAX_NAME_LENGTH) {
            throw InvalidRequestException("El campo 'name' no puede tener más de $MAX_NAME_LENGTH caracteres")
        }
    }
}

// US9: se corre lo que viene en el pedido, este guardado como test o no
data class RunTestRequest(
    val input: List<String> = emptyList(),
    val output: List<String> = emptyList(),
)

// status, lastOutput y lastRunAt son de la ultima corrida automatica, la que dispara editar el snippet (US16)
data class SnippetTestResponse(
    val id: UUID,
    val name: String,
    val input: List<String>,
    val output: List<String>,
    val status: TestStatus,
    val lastOutput: List<String>?,
    val lastRunAt: Instant?,
)

data class TestRunResponse(
    val status: TestStatus,
    val outputs: List<String>,
    val errors: List<SnippetError>,
)
