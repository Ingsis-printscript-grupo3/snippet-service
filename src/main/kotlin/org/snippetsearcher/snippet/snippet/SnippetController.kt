package org.snippetsearcher.snippet.snippet

import org.snippetsearcher.snippet.common.CurrentUser
import org.snippetsearcher.snippet.permission.Relation
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

// lo consume la UI. El mismo path acepta JSON (editor) o multipart (archivo): decide el Content-Type
@RestController
@RequestMapping("/snippets")
class SnippetController(
    private val service: SnippetService,
) {
    // US3
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUser userId: String,
        @RequestBody request: CreateSnippetRequest,
    ): SnippetResponse = service.create(userId, request)

    // US1
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun createFromFile(
        @CurrentUser userId: String,
        @RequestPart("file") file: MultipartFile,
        @RequestParam name: String,
        @RequestParam(defaultValue = "") description: String,
        @RequestParam language: String,
        @RequestParam version: String,
    ): SnippetResponse = service.create(userId, CreateSnippetRequest(name, description, language, version, file.text()))

    // US4
    @PatchMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun update(
        @CurrentUser userId: String,
        @PathVariable id: UUID,
        @RequestBody request: UpdateSnippetRequest,
    ): SnippetResponse = service.update(userId, id, request)

    // US2
    @PatchMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateFromFile(
        @CurrentUser userId: String,
        @PathVariable id: UUID,
        @RequestPart("file") file: MultipartFile,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) description: String?,
        @RequestParam(required = false) language: String?,
        @RequestParam(required = false) version: String?,
    ): SnippetResponse =
        service.update(userId, id, UpdateSnippetRequest(name, description, language, version, file.text()))

    // US5
    @GetMapping
    fun list(
        @CurrentUser userId: String,
        @RequestParam(defaultValue = "ALL") relation: Relation,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) language: String?,
        @RequestParam(required = false) lintStatus: LintStatus?,
        @RequestParam(defaultValue = "NAME") sort: SnippetSort,
        @RequestParam(defaultValue = "ASC") direction: SortDirection,
    ): List<SnippetSummary> = service.list(userId, SnippetQuery(relation, name, language, lintStatus, sort, direction))

    // US6
    @GetMapping("/{id}")
    fun get(
        @CurrentUser userId: String,
        @PathVariable id: UUID,
    ): SnippetResponse = service.get(userId, id)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUser userId: String,
        @PathVariable id: UUID,
    ) = service.delete(userId, id)

    private fun MultipartFile.text(): String = bytes.toString(Charsets.UTF_8)
}
