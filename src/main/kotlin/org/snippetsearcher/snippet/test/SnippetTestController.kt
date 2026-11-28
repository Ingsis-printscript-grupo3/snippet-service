package org.snippetsearcher.snippet.test

import org.snippetsearcher.snippet.common.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// lo consume la UI. Los tests cuelgan de su snippet: no existen sueltos
@RestController
@RequestMapping("/snippets/{snippetId}/tests")
class SnippetTestController(
    private val service: SnippetTestService,
) {
    @GetMapping
    fun list(
        @CurrentUser userId: String,
        @PathVariable snippetId: UUID,
    ): List<SnippetTestResponse> = service.list(userId, snippetId)

    // US8
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUser userId: String,
        @PathVariable snippetId: UUID,
        @RequestBody request: SnippetTestRequest,
    ): SnippetTestResponse = service.create(userId, snippetId, request)

    @PutMapping("/{testId}")
    fun update(
        @CurrentUser userId: String,
        @PathVariable snippetId: UUID,
        @PathVariable testId: UUID,
        @RequestBody request: SnippetTestRequest,
    ): SnippetTestResponse = service.update(userId, snippetId, testId, request)

    @DeleteMapping("/{testId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUser userId: String,
        @PathVariable snippetId: UUID,
        @PathVariable testId: UUID,
    ) = service.delete(userId, snippetId, testId)

    // US9. 200 aunque el test falle: lo que se pidio fue correrlo, y el resultado va adentro
    @PostMapping("/run")
    fun run(
        @CurrentUser userId: String,
        @PathVariable snippetId: UUID,
        @RequestBody request: RunTestRequest,
    ): TestRunResponse = service.run(userId, snippetId, request)
}
