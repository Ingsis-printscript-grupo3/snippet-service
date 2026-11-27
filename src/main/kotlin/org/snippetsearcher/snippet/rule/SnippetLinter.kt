package org.snippetsearcher.snippet.rule

import org.slf4j.LoggerFactory
import org.snippetsearcher.snippet.common.DownstreamException
import org.snippetsearcher.snippet.language.LanguageRegistry
import org.snippetsearcher.snippet.language.LintResult
import org.snippetsearcher.snippet.language.RuleType
import org.snippetsearcher.snippet.snippet.LintStatus
import org.snippetsearcher.snippet.snippet.Snippet
import org.springframework.stereotype.Service
import java.util.UUID

// Un snippet se evalua siempre con las reglas de lint de su owner (US 14), y de cada evaluacion quedan
// guardadas solo sus violaciones: las de la anterior se borran (US 15).
// Lo usan el alta y la edicion, que lintean en el momento, y el re-linteo masivo, que trae el resultado
// ya calculado por el worker. Los metodos corren dentro de la transaccion de quien los llama
@Service
class SnippetLinter(
    private val rules: RuleService,
    private val languages: LanguageRegistry,
    private val violations: LintViolationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // lintear no bloquea: el snippet ya es valido y no se pierde lo que el usuario acaba de guardar. Si el
    // servicio del lenguaje no responde queda PENDING hasta que se lo edite o el owner cambie sus reglas
    fun lint(
        snippet: Snippet,
        content: String,
    ): LintStatus =
        try {
            val ownerRules = rules.buildRulesMap(snippet.ownerId, snippet.language, RuleType.LINT)
            record(snippet.id, languages.get(snippet.language).lint(snippet.version, content, ownerRules))
        } catch (e: DownstreamException) {
            log.warn("No se pudo lintear el snippet {}, queda {}", snippet.id, LintStatus.PENDING, e)
            // las violaciones guardadas eran de otro contenido: mostrarlas seria mentir
            violations.deleteBySnippetId(snippet.id)
            LintStatus.PENDING
        }

    fun record(
        snippetId: UUID,
        result: LintResult,
    ): LintStatus {
        violations.deleteBySnippetId(snippetId)
        violations.saveAll(result.toViolations(snippetId))
        return if (result.compliant) LintStatus.COMPLIANT else LintStatus.NOT_COMPLIANT
    }
}

// el linter de PrintScript informa el mensaje y la posicion, no que regla se incumplio: por eso las
// violaciones de estilo comparten una etiqueta y las de parseo otra
private const val STYLE_RULE = "lint"
private const val PARSER_RULE = "Parser"

private fun LintResult.toViolations(snippetId: UUID): List<LintViolation> =
    warnings.map { LintViolation(UUID.randomUUID(), snippetId, STYLE_RULE, it.message, it.line, it.column) } +
        errors.map { LintViolation(UUID.randomUUID(), snippetId, PARSER_RULE, it.message, it.line, it.column) }
