package org.snippetsearcher.snippet.snippet

import org.snippetsearcher.snippet.common.InvalidRequestException
import java.util.UUID

// US1 y US3: el mismo pedido llega como JSON desde el editor o armado desde un archivo subido
data class CreateSnippetRequest(
    val name: String,
    val description: String = "",
    val language: String,
    val version: String,
    val content: String,
) {
    fun requireNoBlanks() {
        requireNotBlank("name" to name, "language" to language, "version" to version, "content" to content)
    }
}

// US2 y US4: el owner puede cambiar cualquier dato. Lo que viene en null queda como estaba
data class UpdateSnippetRequest(
    val name: String? = null,
    val description: String? = null,
    val language: String? = null,
    val version: String? = null,
    val content: String? = null,
) {
    fun requireNoBlanks() {
        requireNotBlank("name" to name, "language" to language, "version" to version, "content" to content)
    }
}

data class SnippetResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val language: String,
    val version: String,
    val ownerId: String,
    val content: String,
)

// el listado no trae el contenido: serian N lecturas al bucket para mostrar una tabla
data class SnippetSummary(
    val id: UUID,
    val name: String,
    val description: String,
    val language: String,
    val version: String,
    val ownerId: String,
)

fun Snippet.toResponse(content: String) = SnippetResponse(id, name, description, language, version, ownerId, content)

fun Snippet.toSummary() = SnippetSummary(id, name, description, language, version, ownerId)

// un campo en null no se valida: en el update significa "dejalo como estaba"
private fun requireNotBlank(vararg fields: Pair<String, String?>) {
    val blank = fields.firstOrNull { (_, value) -> value != null && value.isBlank() }
    if (blank != null) throw InvalidRequestException("El campo '${blank.first}' no puede estar vacío")
}
