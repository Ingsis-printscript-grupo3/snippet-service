package org.snippetsearcher.snippet.language

import org.snippetsearcher.snippet.common.InvalidRequestException

// lo que el snippet-service necesita de un lenguaje. Hoy hay uno solo, PrintScript,
// pero la consigna dice que en el futuro no sera el unico
interface LanguageService {
    fun validate(
        version: String,
        content: String,
    ): ValidationResult

    // corre las reglas predeterminadas del lenguaje. Cuando exista la US14 va a recibir las del owner
    fun lint(
        version: String,
        content: String,
    ): LintResult
}

data class ValidationResult(
    val valid: Boolean,
    val errors: List<SnippetError>,
)

// warnings son reglas de estilo incumplidas; errors, que el snippet ni siquiera parsea
data class LintResult(
    val warnings: List<LintWarning>,
    val errors: List<SnippetError>,
) {
    val compliant: Boolean get() = warnings.isEmpty() && errors.isEmpty()
}

data class LintWarning(
    val message: String,
    val line: Int,
    val column: Int,
)

// que regla se incumplio y donde: es lo que piden las US 1 a 4
data class SnippetError(
    val message: String,
    val line: Int,
    val column: Int,
    val endLine: Int,
    val endColumn: Int,
)

// elige el servicio segun el campo language del snippet
class LanguageRegistry(
    private val services: Map<String, LanguageService>,
) {
    fun get(language: String): LanguageService =
        services[language] ?: throw InvalidRequestException(
            "El lenguaje '$language' no está soportado. Soportados: ${services.keys.joinToString()}",
        )
}
