package org.snippetsearcher.snippet.snippet

import jakarta.persistence.Entity
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
) {
    val contentKey: String get() = id.toString()
}

interface SnippetRepository : JpaRepository<Snippet, UUID>
