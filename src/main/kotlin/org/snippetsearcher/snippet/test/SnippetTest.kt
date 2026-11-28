package org.snippetsearcher.snippet.test

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "snippet_test")
class SnippetTest(
    @Id
    val id: UUID,
    val snippetId: UUID,
    var name: String,
    @Column(name = "inputs")
    var inputsJson: String,
    @Column(name = "expected_outputs")
    var expectedOutputsJson: String,
    @Enumerated(EnumType.STRING)
    var status: TestStatus = TestStatus.PENDING,
    @Column(name = "last_output")
    var lastOutputJson: String? = null,
    var lastRunAt: Instant? = null,
)

enum class TestStatus {
    PENDING,
    PASSED,
    FAILED,
    ERROR,
}

interface SnippetTestRepository : JpaRepository<SnippetTest, UUID> {
    fun findBySnippetId(snippetId: UUID): List<SnippetTest>
}
