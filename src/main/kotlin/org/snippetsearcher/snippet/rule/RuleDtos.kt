package org.snippetsearcher.snippet.rule

import com.fasterxml.jackson.annotation.JsonProperty
import org.snippetsearcher.snippet.language.RuleValueType
import java.util.UUID

// La forma { id, name, isActive, value } es la que usa printscript-ui (types/Rule.ts). La UI elige el input
// según el tipo JSON de value (number -> campo numérico, string -> texto, null -> solo checkbox), así que
// value sale tipado. El resto de los campos es metadata extra que la UI ignora.
data class RuleResponse(
    val id: String,
    val name: String,
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val value: Any?,
    val valueType: RuleValueType,
    val defaultValue: String?,
    val min: Int?,
    val max: Int?,
    val allowedValues: List<String>,
    val onlyVersions: List<String>,
)

data class UpdateRuleRequest(
    val name: String,
    @param:JsonProperty("isActive")
    val isActive: Boolean,
    // la UI manda number para reglas enteras y string para enums; se guarda como texto
    val value: Any? = null,
    val id: String? = null,
) {
    fun valueAsText(): String? =
        when (value) {
            null -> null
            is Number -> value.toLong().toString()
            else -> value.toString()
        }
}

data class RulesUpdateResult(
    val jobId: UUID,
    val rules: List<RuleResponse>,
)
