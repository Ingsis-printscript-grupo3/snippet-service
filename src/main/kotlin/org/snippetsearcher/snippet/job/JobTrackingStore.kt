package org.snippetsearcher.snippet.job

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class JobTrackingStore(
    val jobs: BulkJobRepository,
    val items: BulkJobItemRepository,
) {
    fun findJob(jobId: UUID): BulkJob? = jobs.findByIdOrNull(jobId)

    fun ensurePendingItem(
        jobId: UUID,
        snippetId: UUID,
    ) {
        val id = BulkJobItemId(jobId, snippetId)
        if (!items.existsById(id)) {
            items.save(BulkJobItem(id, BulkJobItemStatus.PENDING))
        }
    }

    fun isPending(
        jobId: UUID?,
        snippetId: UUID,
    ): Boolean =
        jobId == null ||
            items.findByIdOrNull(BulkJobItemId(jobId, snippetId))?.status == BulkJobItemStatus.PENDING

    fun completeItem(
        jobId: UUID?,
        snippetId: UUID,
        targetStatus: BulkJobItemStatus,
    ) {
        val id = jobId ?: return
        val item = items.findByIdOrNull(BulkJobItemId(id, snippetId))
        if (item != null && item.status == BulkJobItemStatus.PENDING) {
            item.status = targetStatus
            jobs.findByIdOrNull(id)?.let { job ->
                if (targetStatus == BulkJobItemStatus.DONE) job.done += 1 else job.failed += 1
                if (job.done + job.failed >= job.total) job.status = BulkJobStatus.DONE
            }
        }
    }
}
