package org.snippetsearcher.snippet.rule

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.io.Serializable

@Embeddable
data class RuleConfigId(
    val userId: String = "",
    val language: String = "",
    val type: String = "",
    val name: String = "",
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

@Entity
@Table(name = "rule_config")
class RuleConfig(
    @EmbeddedId
    val id: RuleConfigId,
    var enabled: Boolean,
    @Column(name = "rule_value")
    var value: String? = null,
)

interface RuleConfigRepository : JpaRepository<RuleConfig, RuleConfigId> {
    fun findByIdUserIdAndIdLanguageAndIdType(
        userId: String,
        language: String,
        type: String,
    ): List<RuleConfig>
}
