package org.snippetsearcher.snippet.snippet

import org.snippetsearcher.snippet.common.InvalidRequestException
import org.snippetsearcher.snippet.language.LanguageRegistry
import java.time.Instant
import java.util.UUID

// US1 y US3: lo que llega por HTTP, como JSON desde el editor o armado desde un archivo subido.
// version es opcional (el test de home de la catedra no la manda): si no viene, la pone el lenguaje
data class CreateSnippetBody(
    val name: String,
    val description: String = "",
    val language: String,
    val version: String? = null,
    val content: String,
) {
    fun toRequest(languages: LanguageRegistry) =
        CreateSnippetRequest(name, description, language, languages.versionOrDefault(language, version), content)
}

// lo que el service necesita para crear un snippet: la version ya esta resuelta
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

    // el lintStatus lo calcula el service: hace falta llamar al lenguaje
    fun toSnippet(
        ownerId: String,
        lintStatus: LintStatus,
    ): Snippet {
        val now = Instant.now()
        return Snippet(UUID.randomUUID(), name, description, language, version, ownerId, now, now, lintStatus)
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
    val lintStatus: LintStatus,
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
    val lintStatus: LintStatus,
)

fun Snippet.toResponse(content: String) =
    SnippetResponse(id, name, description, language, version, ownerId, lintStatus, content)

fun Snippet.toSummary() = SnippetSummary(id, name, description, language, version, ownerId, lintStatus)

// un campo en null no se valida: en el update significa "dejalo como estaba"
private fun requireNotBlank(vararg fields: Pair<String, String?>) {
    val blank = fields.firstOrNull { (_, value) -> value != null && value.isBlank() }
    if (blank != null) throw InvalidRequestException("El campo '${blank.first}' no puede estar vacío")
}
