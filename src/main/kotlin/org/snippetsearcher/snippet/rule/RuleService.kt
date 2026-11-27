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
    ): List<RuleResponse> {
        val type = parseType(typeParam)
        val definitions = languages.get(language).rules(type)
        val overrides =
            repository
                .findByIdUserIdAndIdLanguageAndIdType(userId, language, type.name)
                .associateBy { it.id.name }
        return definitions.map { toRuleResponse(it, overrides[it.name]) }
    }

    @Transactional
    fun updateRules(
        userId: String,
        typeParam: String,
        language: String,
        updates: List<UpdateRuleRequest>,
    ): BulkJobCreatedResponse {
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
        return BulkJobCreatedResponse(job.id)
    }

    @Transactional(readOnly = true)
    fun buildRulesMap(
        userId: String,
        language: String,
        type: RuleType,
    ): Map<String, Any?> =
        getRules(userId, type.name, language)
            .filter { it.enabled }
            .associate { it.name to toEngineValue(type, it) }

    private fun toEngineValue(
        type: RuleType,
        rule: RuleResponse,
    ): Any? =
        when (type) {
            RuleType.FORMAT -> if (rule.valueType == RuleValueType.INTEGER) rule.value?.toIntOrNull() ?: 0 else true
            RuleType.LINT -> rule.value ?: "true"
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
                    enabled = it.enabled,
                    value = it.value,
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
            if (update.enabled) validateValue(def, update.value)
        }
        val effectiveEnabled =
            definitions.mapValues { (name, def) ->
                updates.find { it.name == name }?.enabled ?: existing[name]?.enabled ?: def.defaultEnabled
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

    private fun parseType(typeParam: String): RuleType =
        RuleType.entries.firstOrNull { it.name.equals(typeParam, ignoreCase = true) }
            ?: throw InvalidRequestException("Tipo de regla inválido: '$typeParam'")
}

private fun toRuleResponse(
    def: RuleDefinition,
    override: RuleConfig?,
): RuleResponse =
    RuleResponse(
        name = def.name,
        enabled = override?.enabled ?: def.defaultEnabled,
        value = override?.value ?: def.defaultValue,
        valueType = def.valueType,
        defaultValue = def.defaultValue,
        min = def.min,
        max = def.max,
        allowedValues = def.allowedValues,
        onlyVersions = def.onlyVersions,
    )
