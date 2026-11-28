package org.snippetsearcher.snippet.language

import org.snippetsearcher.snippet.common.InvalidRequestException

// lo que el snippet-service necesita de un lenguaje. Hoy hay uno solo, PrintScript,
// pero la consigna dice que en el futuro no sera el unico
interface LanguageService {
    // la extension de los archivos de este lenguaje, sin el punto. Con ella la UI reconoce de que lenguaje
    // es un archivo que se sube
    val extension: String

    // la version que se usa cuando quien crea un snippet no manda ninguna
    val defaultVersion: String

    fun validate(
        version: String,
        content: String,
    ): ValidationResult

    // rules son las reglas de lint del owner ya resueltas: solo las habilitadas, con las claves del lenguaje.
    // Un mapa vacio es "ninguna regla habilitada", no "las predeterminadas"
    fun lint(
        version: String,
        content: String,
        rules: Map<String, Any?>,
    ): LintResult

    fun rules(type: RuleType): List<RuleDefinition>

    // corre el snippet entregando los inputs en orden. Que el snippet falle no es una falla del servicio:
    // vuelve en errors, junto con lo que llego a imprimir
    fun execute(
        version: String,
        content: String,
        inputs: List<String>,
    ): ExecutionResult

    // rules son las reglas de formato del owner ya resueltas, con las claves del lenguaje.
    // Un mapa vacio es "ninguna regla habilitada": el codigo vuelve como estaba
    fun format(
        version: String,
        content: String,
        rules: Map<String, Any?>,
    ): FormattingResult
}

// content es null cuando el snippet no se puede parsear: ahi los errores dicen por que
data class FormattingResult(
    val content: String?,
    val errors: List<SnippetError>,
)

data class ExecutionResult(
    val outputs: List<String>,
    val errors: List<SnippetError>,
)

enum class RuleType {
    LINT,
    FORMAT,
}

enum class RuleValueType {
    BOOLEAN,
    INTEGER,
    ENUM,
}

data class RuleDefinition(
    val name: String,
    val type: RuleType,
    val valueType: RuleValueType,
    val defaultEnabled: Boolean,
    val defaultValue: String? = null,
    val min: Int? = null,
    val max: Int? = null,
    val allowedValues: List<String> = emptyList(),
    val exclusiveWith: List<String> = emptyList(),
    val onlyVersions: List<String> = emptyList(),
)

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

// un lenguaje soportado y la extension de sus archivos: lo que la UI ofrece al elegir en que lenguaje esta un snippet
data class FileType(
    val language: String,
    val extension: String,
)

// elige el servicio segun el campo language del snippet
class LanguageRegistry(
    private val services: Map<String, LanguageService>,
) {
    fun get(language: String): LanguageService =
        services[language] ?: throw InvalidRequestException(
            "El lenguaje '$language' no está soportado. Soportados: ${services.keys.joinToString()}",
        )

    fun fileTypes(): List<FileType> = services.map { (language, service) -> FileType(language, service.extension) }

    fun versionOrDefault(
        language: String,
        version: String?,
    ): String = version ?: get(language).defaultVersion
}
