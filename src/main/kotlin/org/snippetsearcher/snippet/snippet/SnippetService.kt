package org.snippetsearcher.snippet.snippet

import org.slf4j.LoggerFactory
import org.snippetsearcher.snippet.common.ForbiddenException
import org.snippetsearcher.snippet.common.InvalidSnippetException
import org.snippetsearcher.snippet.common.SnippetNotFoundException
import org.snippetsearcher.snippet.language.LanguageRegistry
import org.snippetsearcher.snippet.permission.Access
import org.snippetsearcher.snippet.permission.PermissionClient
import org.snippetsearcher.snippet.permission.Relation
import org.snippetsearcher.snippet.storage.ContentStore
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SnippetService(
    private val repository: SnippetRepository,
    private val languages: LanguageRegistry,
    private val permissions: PermissionClient,
    private val contents: ContentStore,
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

        val now = Instant.now()
        val snippet =
            repository.save(
                Snippet(
                    id = UUID.randomUUID(),
                    name = request.name,
                    description = request.description,
                    language = request.language,
                    version = request.version,
                    ownerId = userId,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        contents.put(snippet.contentKey, request.content)
        try {
            permissions.grantOwner(snippet.id, userId)
        } catch (e: RuntimeException) {
            deleteContentQuietly(snippet.contentKey)
            throw e
        }
        return snippet.toResponse(request.content)
    }

    @Transactional
    fun update(
        userId: String,
        id: UUID,
        request: UpdateSnippetRequest,
    ): SnippetResponse {
        request.requireNoBlanks()
        val snippet = findWithAccess(id, userId)
        if (accessOf(snippet, userId) != Access.OWNER) {
            throw ForbiddenException("Solo el owner puede modificar el snippet")
        }

        val language = request.language ?: snippet.language
        val version = request.version ?: snippet.version
        val content = request.content ?: contents.get(snippet.contentKey)
        // cambiar el codigo, el lenguaje o la version puede romper el snippet: se revalida
        if (request.content != null || language != snippet.language || version != snippet.version) {
            validate(language, version, content)
        }

        request.name?.let { snippet.name = it }
        request.description?.let { snippet.description = it }
        snippet.language = language
        snippet.version = version
        snippet.updatedAt = Instant.now()
        if (request.content != null) contents.put(snippet.contentKey, content)
        return snippet.toResponse(content)
    }

    @Transactional(readOnly = true)
    fun get(
        userId: String,
        id: UUID,
    ): SnippetResponse {
        val snippet = findWithAccess(id, userId)
        return snippet.toResponse(contents.get(snippet.contentKey))
    }

    // US5: el permission-service sabe a que snippets accede el usuario, la base sabe como se llaman
    @Transactional(readOnly = true)
    fun list(
        userId: String,
        relation: Relation,
        name: String?,
        language: String?,
    ): List<SnippetSummary> {
        val ids = permissions.snippetIds(userId, relation)
        if (ids.isEmpty()) return emptyList()
        return repository
            .findAllById(ids)
            .asSequence()
            .filter { name.isNullOrBlank() || it.name.contains(name, ignoreCase = true) }
            .filter { language.isNullOrBlank() || it.language.equals(language, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
            .map { it.toSummary() }
            .toList()
    }

    @Transactional
    fun delete(
        userId: String,
        id: UUID,
    ) {
        val snippet = findWithAccess(id, userId)
        if (accessOf(snippet, userId) != Access.OWNER) {
            throw ForbiddenException("Solo el owner puede borrar el snippet")
        }
        // la fila se borra recien al confirmar la transaccion: si algo falla antes, el owner la sigue viendo
        // (su acceso sale de la fila) y puede reintentar el delete
        permissions.deleteAll(snippet.id)
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

    // sin ningun permiso es 404 y no 403: no se le confirma a nadie que el snippet existe
    private fun findWithAccess(
        id: UUID,
        userId: String,
    ): Snippet {
        val snippet = repository.findByIdOrNull(id) ?: throw SnippetNotFoundException(id)
        if (accessOf(snippet, userId) == Access.NONE) throw SnippetNotFoundException(id)
        return snippet
    }

    // el owner esta en la fila: no hace falta preguntarle al permission-service
    private fun accessOf(
        snippet: Snippet,
        userId: String,
    ): Access = if (snippet.ownerId == userId) Access.OWNER else permissions.check(snippet.id, userId)

    private fun deleteContentQuietly(key: String) {
        runCatching { contents.delete(key) }
            .onFailure { log.warn("No se pudo borrar el contenido huerfano {}", key, it) }
    }
}
