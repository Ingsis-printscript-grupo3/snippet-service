package org.snippetsearcher.snippet.rule

import org.snippetsearcher.snippet.common.InvalidRequestException
import org.snippetsearcher.snippet.job.BulkJob
import org.snippetsearcher.snippet.job.BulkJobRepository
import org.snippetsearcher.snippet.job.BulkJobStatus
import org.snippetsearcher.snippet.language.LanguageRegistry
import org.snippetsearcher.snippet.language.RuleDefinition
import org.snippetsearcher.snippet.language.RuleType
import org.snippetsearcher.snippet.language.RuleValueType
import org.snippetsearcher.snippet.snippet.SnippetRepository
import org.snippetsearcher.snippet.streams.BulkJobRequestMessage
import org.snippetsearcher.snippet.streams.StreamNames
import org.snippetsearcher.snippet.streams.StreamProducer
import org.snippetsearcher.snippet.streams.publishAfterCommit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class RuleService(
    private val repository: RuleConfigRepository,
    private val languages: LanguageRegistry,
    private val snippets: SnippetRepository,
    private val jobs: BulkJobRepository,
    private val producer: StreamProducer,
) {
    @Transactional(readOnly = true)
    fun getRules(
        userId: String,
        typeParam: String,
        language: String,
    ): List<RuleResponse> = resolve(userId, language, parseType(typeParam)).map { it.toResponse() }

    @Transactional
    fun updateRules(
        userId: String,
        typeParam: String,
        language: String,
        updates: List<UpdateRuleRequest>,
    ): RulesUpdateResult {
        val type = parseType(typeParam)
        val definitions = languages.get(language).rules(type).associateBy { it.name }
        val existing =
            repository
                .findByIdUserIdAndIdLanguageAndIdType(userId, language, type.name)
                .associateBy { it.id.name }
        validateUpdates(updates, definitions, existing)
        saveUpdates(userId, language, type, updates)
        if (type == RuleType.FORMAT) snippets.clearFormattedKey(userId, language)

        val job = createBulkJob(userId, type, language)
        producer.publishAfterCommit(
            StreamNames.BULK_JOB_REQUESTS,
            BulkJobRequestMessage(job.id, userId, type.name, language),
        )
        return RulesUpdateResult(job.id, resolve(userId, language, type).map { it.toResponse() })
    }

    // las reglas habilitadas de un usuario, con las claves y valores que espera el lenguaje.
    // Sin @Transactional a proposito: lo llaman desde transacciones que siguen adelante si el servicio del
    // lenguaje no responde, y una excepcion que cruza un metodo transaccional las marcaria para rollback
    fun buildRulesMap(
        userId: String,
        language: String,
        type: RuleType,
    ): Map<String, Any?> =
        resolve(userId, language, type)
            .filter { it.enabled }
            .associate { it.definition.name to toEngineValue(type, it) }

    // reglas del lenguaje con los overrides del usuario aplicados encima de los defaults
    private fun resolve(
        userId: String,
        language: String,
        type: RuleType,
    ): List<ResolvedRule> {
        val overrides =
            repository
                .findByIdUserIdAndIdLanguageAndIdType(userId, language, type.name)
                .associateBy { it.id.name }
        return languages.get(language).rules(type).map { def ->
            val override = overrides[def.name]
            ResolvedRule(def, override?.enabled ?: def.defaultEnabled, override?.value ?: def.defaultValue)
        }
    }

    private fun saveUpdates(
        userId: String,
        language: String,
        type: RuleType,
        updates: List<UpdateRuleRequest>,
    ) {
        val entities =
            updates.map {
                RuleConfig(
                    id = RuleConfigId(userId, language, type.name, it.name),
                    enabled = it.isActive,
                    value = it.valueAsText(),
                )
            }
        repository.saveAll(entities)
    }

    private fun createBulkJob(
        userId: String,
        type: RuleType,
        language: String,
    ): BulkJob =
        jobs.save(
            BulkJob(
                id = UUID.randomUUID(),
                userId = userId,
                type = type.name,
                language = language,
                status = BulkJobStatus.RUNNING,
                createdAt = Instant.now(),
            ),
        )

    private fun validateUpdates(
        updates: List<UpdateRuleRequest>,
        definitions: Map<String, RuleDefinition>,
        existing: Map<String, RuleConfig>,
    ) {
        updates.forEach { update ->
            val def =
                definitions[update.name]
                    ?: throw InvalidRequestException("La regla '${update.name}' no existe")
            if (update.isActive) validateValue(def, update.valueAsText())
        }
        val effectiveEnabled =
            definitions.mapValues { (name, def) ->
                updates.find { it.name == name }?.isActive ?: existing[name]?.enabled ?: def.defaultEnabled
            }
        definitions.values.forEach { def ->
            if (effectiveEnabled[def.name] == true) {
                def.exclusiveWith.firstOrNull { effectiveEnabled[it] == true }?.let { conflict ->
                    throw InvalidRequestException("Las reglas '${def.name}' y '$conflict' son incompatibles")
                }
            }
        }
    }

    private fun validateValue(
        def: RuleDefinition,
        value: String?,
    ) {
        when (def.valueType) {
            RuleValueType.BOOLEAN -> Unit
            RuleValueType.INTEGER -> validateIntValue(def, value)
            RuleValueType.ENUM -> {
                if (value == null || value !in def.allowedValues) {
                    throw InvalidRequestException(
                        "Valor inválido '$value' para '${def.name}'. Permitidos: ${def.allowedValues}",
                    )
                }
            }
        }
    }

    private fun validateIntValue(
        def: RuleDefinition,
        value: String?,
    ) {
        val parsed =
            value?.toIntOrNull()
                ?: throw InvalidRequestException("La regla '${def.name}' requiere un valor entero")
        val min = def.min
        val max = def.max
        val belowMin = min != null && parsed < min
        val aboveMax = max != null && parsed > max
        if (belowMin || aboveMax) {
            throw InvalidRequestException("Valor $parsed fuera de rango para '${def.name}' ($min..${max ?: "∞"})")
        }
    }
}

// formatting/linting son las rutas de printscript-ui; format/lint quedan como alias
private fun parseType(typeParam: String): RuleType =
    when (typeParam.lowercase()) {
        "format", "formatting" -> RuleType.FORMAT
        "lint", "linting" -> RuleType.LINT
        else -> throw InvalidRequestException("Tipo de regla inválido: '$typeParam'")
    }

private fun toEngineValue(
    type: RuleType,
    rule: ResolvedRule,
): Any? =
    when (type) {
        RuleType.FORMAT ->
            if (rule.definition.valueType == RuleValueType.INTEGER) rule.value?.toIntOrNull() ?: 0 else true
        RuleType.LINT -> rule.value ?: "true"
    }

private data class ResolvedRule(
    val definition: RuleDefinition,
    val enabled: Boolean,
    val value: String?,
)

// value sale tipado para la UI: number en reglas enteras, string en enums y null en booleanas
private fun ResolvedRule.toResponse(): RuleResponse =
    RuleResponse(
        id = definition.name,
        name = definition.name,
        isActive = enabled,
        value =
            when (definition.valueType) {
                RuleValueType.INTEGER -> value?.toIntOrNull()
                RuleValueType.ENUM -> value
                RuleValueType.BOOLEAN -> null
            },
        valueType = definition.valueType,
        defaultValue = definition.defaultValue,
        min = definition.min,
        max = definition.max,
        allowedValues = definition.allowedValues,
        onlyVersions = definition.onlyVersions,
    )
