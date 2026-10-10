package org.snippetsearcher.snippet.permission

import java.util.UUID

// lo que el snippet-service necesita del permission-service, siempre sobre recursos de tipo snippet
interface PermissionClient {
    fun grantOwner(
        snippetId: UUID,
        userId: String,
    )

    fun check(
        snippetId: UUID,
        userId: String,
    ): Access

    // los ids de los snippets que el usuario puede ver, segun su relacion con ellos (US5)
    fun snippetIds(
        userId: String,
        relation: Relation,
    ): List<UUID>

    fun deleteAll(snippetId: UUID)
}

// OWNER incluye todo lo que puede READ
enum class Access {
    OWNER,
    READ,
    NONE,
}

enum class Relation {
    OWNED,
    SHARED,
    ALL,
}
