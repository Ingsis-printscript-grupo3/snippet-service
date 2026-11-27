package org.snippetsearcher.snippet.rule

import org.snippetsearcher.snippet.language.RuleValueType
import java.util.UUID

data class RuleResponse(
    val name: String,
    val enabled: Boolean,
    val value: String?,
    val valueType: RuleValueType,
    val defaultValue: String?,
    val min: Int?,
    val max: Int?,
    val allowedValues: List<String>,
    val onlyVersions: List<String>,
)

data class UpdateRuleRequest(
    val name: String,
    val enabled: Boolean,
    val value: String? = null,
)

data class BulkJobCreatedResponse(
    val jobId: UUID,
)
