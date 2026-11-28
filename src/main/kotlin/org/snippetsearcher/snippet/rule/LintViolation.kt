package org.snippetsearcher.snippet.rule

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Entity
@Table(name = "lint_violation")
class LintViolation(
    @Id
    val id: UUID,
    val snippetId: UUID,
    val rule: String,
    val message: String,
    val line: Int,
    @Column(name = "column_number")
    val column: Int,
)

interface LintViolationRepository : JpaRepository<LintViolation, UUID> {
    fun findBySnippetId(snippetId: UUID): List<LintViolation>

    fun deleteBySnippetId(snippetId: UUID)
}
