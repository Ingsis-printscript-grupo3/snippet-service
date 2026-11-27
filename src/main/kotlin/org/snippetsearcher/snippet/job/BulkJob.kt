package org.snippetsearcher.snippet.job

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "bulk_job")
class BulkJob(
    @Id
    val id: UUID,
    val userId: String?,
    val type: String,
    val language: String,
    var total: Int = 0,
    var done: Int = 0,
    var failed: Int = 0,
    @Enumerated(EnumType.STRING)
    var status: BulkJobStatus = BulkJobStatus.RUNNING,
    val createdAt: Instant,
)

enum class BulkJobStatus {
    RUNNING,
    DONE,
}

@Embeddable
data class BulkJobItemId(
    val jobId: UUID = UUID(0, 0),
    val snippetId: UUID = UUID(0, 0),
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

@Entity
@Table(name = "bulk_job_item")
class BulkJobItem(
    @EmbeddedId
    val id: BulkJobItemId,
    @Enumerated(EnumType.STRING)
    var status: BulkJobItemStatus = BulkJobItemStatus.PENDING,
)

enum class BulkJobItemStatus {
    PENDING,
    DONE,
    FAILED,
}

interface BulkJobRepository : JpaRepository<BulkJob, UUID>

interface BulkJobItemRepository : JpaRepository<BulkJobItem, BulkJobItemId> {
    fun findByIdJobId(jobId: UUID): List<BulkJobItem>
}
