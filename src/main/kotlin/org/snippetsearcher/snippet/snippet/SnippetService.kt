package org.snippetsearcher.snippet.snippet

import org.slf4j.LoggerFactory
import org.snippetsearcher.snippet.common.DownstreamException
import org.snippetsearcher.snippet.common.ForbiddenException
import org.snippetsearcher.snippet.common.InvalidSnippetException
import org.snippetsearcher.snippet.common.SnippetNotFoundException
import org.snippetsearcher.snippet.language.LanguageRegistry
import org.snippetsearcher.snippet.permission.Access
import org.snippetsearcher.snippet.permission.PermissionClient
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

        val lintStatus = lintStatusOf(request.language, request.version, request.content)
        val snippet = repository.save(request.toSnippet(userId, lintStatus))
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
            permissions.grantOwner(snippet.id, userId)
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
        val snippet = findWithAccess(id, userId)
        if (accessOf(snippet, userId) != Access.OWNER) {
            throw ForbiddenException("Solo el owner puede modificar el snippet")
        }

        val language = request.language ?: snippet.language
        val version = request.version ?: snippet.version
        val content = request.content ?: contents.get(snippet.contentKey)
        // cambiar el codigo, el lenguaje o la version puede romper el snippet: se revalida
        val changesCode = request.content != null || language != snippet.language || version != snippet.version
        if (changesCode) validate(language, version, content)

        request.name?.let { snippet.name = it }
        request.description?.let { snippet.description = it }
        snippet.language = language
        snippet.version = version
        snippet.updatedAt = Instant.now()
        if (changesCode) snippet.lintStatus = lintStatusOf(language, version, content)
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
        query: SnippetQuery,
    ): List<SnippetSummary> {
        val ids = permissions.snippetIds(userId, query.relation)
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

    // lintear no bloquea: el snippet ya es valido. Si el printscript-service no responde queda PENDING
    // y se puede volver a lintear despues, sin perder el snippet que el usuario acaba de guardar
    private fun lintStatusOf(
        language: String,
        version: String,
        content: String,
    ): LintStatus =
        try {
            if (languages
                    .get(
                        language,
                    ).lint(version, content)
                    .compliant
            ) {
                LintStatus.COMPLIANT
            } else {
                LintStatus.NOT_COMPLIANT
            }
        } catch (e: DownstreamException) {
            log.warn("No se pudo lintear el snippet, queda {}", LintStatus.PENDING, e)
            LintStatus.PENDING
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
}
