package org.snippetsearcher.snippet.job

import org.snippetsearcher.snippet.common.CurrentUser
import org.snippetsearcher.snippet.common.JobNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/jobs")
class BulkJobController(
    private val jobs: BulkJobRepository,
) {
    @GetMapping("/{jobId}")
    fun get(
        @CurrentUser userId: String,
        @PathVariable jobId: UUID,
    ): BulkJobResponse {
        val job = jobs.findByIdOrNull(jobId) ?: throw JobNotFoundException(jobId)
        if (job.userId != userId) throw JobNotFoundException(jobId)
        return BulkJobResponse(
            id = job.id,
            type = job.type,
            total = job.total,
            done = job.done,
            failed = job.failed,
            status = job.status,
        )
    }
}

data class BulkJobResponse(
    val id: UUID,
    val type: String,
    val total: Int,
    val done: Int,
    val failed: Int,
    val status: BulkJobStatus,
)
