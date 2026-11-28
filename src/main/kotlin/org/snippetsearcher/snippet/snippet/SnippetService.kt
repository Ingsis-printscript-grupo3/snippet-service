package org.snippetsearcher.snippet.snippet

import org.slf4j.LoggerFactory
import org.snippetsearcher.snippet.common.InvalidSnippetException
import org.snippetsearcher.snippet.language.LanguageRegistry
import org.snippetsearcher.snippet.rule.SnippetLinter
import org.snippetsearcher.snippet.storage.ContentStore
import org.snippetsearcher.snippet.test.TestTriggerService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SnippetService(
    private val repository: SnippetRepository,
    private val languages: LanguageRegistry,
    private val access: SnippetAccess,
    private val contents: ContentStore,
    private val linter: SnippetLinter,
    private val testTrigger: TestTriggerService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // la fila, el contenido y el permiso viven en tres lugares distintos. La fila se confirma recien al
    // final de la transaccion: si falla el bucket o el permiso no queda ningun snippet a medias en la base
    @Transactional
    fun create(
        userId: String,
        request: CreateSnippetRequest,
    ): SnippetResponse {
        request.requireNoBlanks()
        validate(request.language, request.version, request.content)

        // primero la fila: las violaciones de lint apuntan a ella
        val snippet = repository.save(request.toSnippet(userId, LintStatus.PENDING))
        snippet.lintStatus = linter.lint(snippet, request.content)
        contents.put(snippet.contentKey, request.content)
        grantOwnerOrDropContent(snippet, userId)
        return snippet.toResponse(request.content)
    }

    // la fila se deshace sola con el rollback, el contenido del bucket no. Se atrapa cualquier falla
    // a proposito: sea cual sea, el contenido queda huerfano, y la excepcion se vuelve a tirar igual
    @Suppress("TooGenericExceptionCaught")
    private fun grantOwnerOrDropContent(
        snippet: Snippet,
        userId: String,
    ) {
        try {
            access.grantOwner(snippet.id, userId)
        } catch (e: RuntimeException) {
            runCatching { contents.delete(snippet.contentKey) }
                .onFailure { log.warn("No se pudo borrar el contenido huerfano {}", snippet.contentKey, it) }
            throw e
        }
    }

    @Transactional
    fun update(
        userId: String,
        id: UUID,
        request: UpdateSnippetRequest,
    ): SnippetResponse {
        request.requireNoBlanks()
        val snippet = access.owned(id, userId, "Solo el owner puede modificar el snippet")
        val language = request.language ?: snippet.language
        val version = request.version ?: snippet.version
        val content = request.content ?: contents.get(snippet.contentKey)
        val changesCode = request.content != null || language != snippet.language || version != snippet.version
        if (changesCode) validate(language, version, content)

        snippet.applyMetadata(request, language, version)
        if (request.content != null) contents.put(snippet.contentKey, content)
        if (changesCode) {
            snippet.formattedKey = null
            snippet.lintStatus = linter.lint(snippet, content)
            testTrigger.enqueueForSnippet(snippet, userId, content)
        }
        return snippet.toResponse(content)
    }

    @Transactional(readOnly = true)
    fun get(
        userId: String,
        id: UUID,
    ): SnippetResponse {
        val snippet = access.readable(id, userId)
        return snippet.toResponse(contents.get(snippet.contentKey))
    }

    // US5: el permission-service sabe a que snippets accede el usuario, la base sabe como se llaman
    @Transactional(readOnly = true)
    fun list(
        userId: String,
        query: SnippetQuery,
    ): List<SnippetSummary> {
        val ids = access.visibleIds(userId, query.relation)
        if (ids.isEmpty()) return emptyList()
        return repository
            .findAllById(ids)
            .filter { query.matches(it) }
            .sortedWith(query.comparator())
            .map { it.toSummary() }
    }

    @Transactional
    fun delete(
        userId: String,
        id: UUID,
    ) {
        val snippet = access.owned(id, userId, "Solo el owner puede borrar el snippet")
        // la fila se borra recien al confirmar la transaccion: si algo falla antes, el owner la sigue viendo
        // (su acceso sale de la fila) y puede reintentar el delete
        access.revokeAll(snippet.id)
        contents.delete(snippet.contentKey)
        repository.delete(snippet)
    }

    private fun validate(
        language: String,
        version: String,
        content: String,
    ) {
        val result = languages.get(language).validate(version, content)
        if (!result.valid) throw InvalidSnippetException(result.errors)
    }
}
