package org.snippetsearcher.snippet.snippet

import org.snippetsearcher.snippet.common.InvalidSnippetException
import org.snippetsearcher.snippet.language.LanguageRegistry
import org.snippetsearcher.snippet.language.RuleType
import org.snippetsearcher.snippet.rule.RuleService
import org.snippetsearcher.snippet.storage.ContentStore
import org.springframework.stereotype.Service
import java.util.UUID

// que codigo se descarga: el que escribio el autor, o pasado por las reglas de formato
enum class DownloadVersion {
    ORIGINAL,
    FORMATTED,
}

data class SnippetDownload(
    val fileName: String,
    val content: String,
)

// lo que no puede ir en el nombre de un archivo en Windows, Linux o Mac
private val NOT_ALLOWED_IN_FILE_NAMES = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

// US13. Descargar es leer: puede el owner y cualquiera con quien se haya compartido.
// Sin transaccion: no escribe nada y no hay que retener una conexion mientras se espera el formateo
@Service
class SnippetDownloadService(
    private val access: SnippetAccess,
    private val contents: ContentStore,
    private val languages: LanguageRegistry,
    private val rules: RuleService,
) {
    fun download(
        userId: String,
        id: UUID,
        version: DownloadVersion,
    ): SnippetDownload {
        val snippet = access.readable(id, userId)
        val extension = languages.get(snippet.language).extension
        val name = snippet.name.replace(NOT_ALLOWED_IN_FILE_NAMES, "_")
        return when (version) {
            DownloadVersion.ORIGINAL -> SnippetDownload("$name.$extension", contents.get(snippet.contentKey))
            DownloadVersion.FORMATTED -> SnippetDownload("$name.formatted.$extension", formatted(snippet))
        }
    }

    // si el reformateo masivo ya dejo una version, es esa: se borra cuando cambia el codigo o las reglas.
    // Si no hay, se formatea ahora con las reglas del owner (US12) y no se guarda: un GET no escribe
    private fun formatted(snippet: Snippet): String {
        snippet.formattedKey?.let { return contents.get(it) }
        val ownerRules = rules.buildRulesMap(snippet.ownerId, snippet.language, RuleType.FORMAT)
        val result =
            languages.get(snippet.language).format(snippet.version, contents.get(snippet.contentKey), ownerRules)
        return result.content ?: throw InvalidSnippetException(result.errors)
    }
}
