package org.snippetsearcher.snippet.snippet

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

// la metadata del snippet. El codigo no esta aca: esta en el bucket, con el id como key
@Entity
@Table(name = "snippet")
class Snippet(
    @Id
    val id: UUID,
    var name: String,
    var description: String,
    var language: String,
    var version: String,
    val ownerId: String,
    val createdAt: Instant,
    var updatedAt: Instant,
    @Enumerated(EnumType.STRING)
    var lintStatus: LintStatus = LintStatus.PENDING,
) {
    val contentKey: String get() = id.toString()
}

// el orden de las constantes es el orden cuando se ordena por validez: primero los que cumplen
enum class LintStatus {
    COMPLIANT,
    NOT_COMPLIANT,

    // todavia no se pudo lintear, por ejemplo porque el printscript-service no respondia
    PENDING,
}

interface SnippetRepository : JpaRepository<Snippet, UUID>
