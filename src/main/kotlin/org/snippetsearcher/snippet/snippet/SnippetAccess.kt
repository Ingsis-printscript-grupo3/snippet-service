package org.snippetsearcher.snippet.snippet

import org.snippetsearcher.snippet.common.ForbiddenException
import org.snippetsearcher.snippet.common.SnippetNotFoundException
import org.snippetsearcher.snippet.permission.Access
import org.snippetsearcher.snippet.permission.PermissionClient
import org.snippetsearcher.snippet.permission.Relation
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.util.UUID

// quien puede ver y quien puede modificar un snippet, en un solo lugar. Lo usan el snippet y todo
// lo que cuelga de el, como sus tests
@Component
class SnippetAccess(
    private val repository: SnippetRepository,
    private val permissions: PermissionClient,
) {
    // el snippet si el usuario puede verlo, como owner o porque se lo compartieron
    fun readable(
        id: UUID,
        userId: String,
    ): Snippet = withAccess(id, userId).first

    // el snippet si el usuario es el owner. Con solo lectura es 403, y forbidden dice que quiso hacer
    fun owned(
        id: UUID,
        userId: String,
        forbidden: String,
    ): Snippet {
        val (snippet, access) = withAccess(id, userId)
        if (access != Access.OWNER) throw ForbiddenException(forbidden)
        return snippet
    }

    fun visibleIds(
        userId: String,
        relation: Relation,
    ): List<UUID> = permissions.snippetIds(userId, relation)

    fun grantOwner(
        id: UUID,
        userId: String,
    ) = permissions.grantOwner(id, userId)

    fun revokeAll(id: UUID) = permissions.deleteAll(id)

    // sin ningun permiso es 404 y no 403: no se le confirma a nadie que el snippet existe.
    // El owner esta en la fila: no hace falta preguntarle al permission-service
    private fun withAccess(
        id: UUID,
        userId: String,
    ): Pair<Snippet, Access> {
        val snippet = repository.findByIdOrNull(id) ?: throw SnippetNotFoundException(id)
        val access = if (snippet.ownerId == userId) Access.OWNER else permissions.check(snippet.id, userId)
        if (access == Access.NONE) throw SnippetNotFoundException(id)
        return snippet to access
    }
}
