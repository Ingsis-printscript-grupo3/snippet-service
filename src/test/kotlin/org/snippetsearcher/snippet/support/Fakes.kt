package org.snippetsearcher.snippet.support

import org.snippetsearcher.snippet.common.DownstreamException
import org.snippetsearcher.snippet.language.LanguageRegistry
import org.snippetsearcher.snippet.language.LanguageService
import org.snippetsearcher.snippet.language.LintResult
import org.snippetsearcher.snippet.language.LintWarning
import org.snippetsearcher.snippet.language.PRINTSCRIPT
import org.snippetsearcher.snippet.language.RuleDefinition
import org.snippetsearcher.snippet.language.RuleType
import org.snippetsearcher.snippet.language.RuleValueType
import org.snippetsearcher.snippet.language.SnippetError
import org.snippetsearcher.snippet.language.ValidationResult
import org.snippetsearcher.snippet.permission.Access
import org.snippetsearcher.snippet.permission.PermissionClient
import org.snippetsearcher.snippet.permission.Relation
import org.snippetsearcher.snippet.storage.ContentStore
import org.snippetsearcher.snippet.streams.StreamProducer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.stream.RecordId
import java.util.UUID

const val OTHER_LANGUAGE = "otrolenguaje"

// reemplazan a los tres servicios de afuera en los tests: los de controller corren sin red

// un snippet es invalido si contiene la palabra "error", y el error siempre esta en la linea 2, columna 11.
// No cumple las reglas de linteo si tiene un "_": la regla predeterminada de PrintScript pide camelCase
class FakeLanguageService : LanguageService {
    var failLint = false

    override fun lint(
        version: String,
        content: String,
    ): LintResult {
        if (failLint) throw DownstreamException("printscript-service")
        val warnings =
            if (content.contains(
                    "_",
                )
            ) {
                listOf(LintWarning("Identifier must be camel case", 1, 5))
            } else {
                emptyList()
            }
        return LintResult(warnings, emptyList())
    }

    override fun validate(
        version: String,
        content: String,
    ): ValidationResult =
        if (content.contains("error")) {
            ValidationResult(false, listOf(SnippetError("Expected ';'.", 2, 11, 2, 11)))
        } else {
            ValidationResult(true, emptyList())
        }

    override fun rules(type: RuleType): List<RuleDefinition> =
        when (type) {
            RuleType.FORMAT ->
                listOf(
                    RuleDefinition(
                        "enforce-spacing-around-equals",
                        RuleType.FORMAT,
                        RuleValueType.BOOLEAN,
                        false,
                        exclusiveWith = listOf("enforce-no-spacing-around-equals"),
                    ),
                    RuleDefinition(
                        "enforce-no-spacing-around-equals",
                        RuleType.FORMAT,
                        RuleValueType.BOOLEAN,
                        false,
                        exclusiveWith = listOf("enforce-spacing-around-equals"),
                    ),
                    RuleDefinition(
                        "line-breaks-after-println",
                        RuleType.FORMAT,
                        RuleValueType.INTEGER,
                        false,
                        "0",
                        0,
                        2,
                    ),
                    RuleDefinition("indent-inside-if", RuleType.FORMAT, RuleValueType.INTEGER, false, "0", 0, null),
                    RuleDefinition(
                        "if-brace-same-line",
                        RuleType.FORMAT,
                        RuleValueType.BOOLEAN,
                        false,
                        exclusiveWith = listOf("if-brace-below-line"),
                    ),
                    RuleDefinition(
                        "if-brace-below-line",
                        RuleType.FORMAT,
                        RuleValueType.BOOLEAN,
                        false,
                        exclusiveWith = listOf("if-brace-same-line"),
                    ),
                )
            RuleType.LINT ->
                listOf(
                    RuleDefinition(
                        "identifier_format",
                        RuleType.LINT,
                        RuleValueType.ENUM,
                        true,
                        "camel case",
                        allowedValues = listOf("camel case", "snake case"),
                    ),
                    RuleDefinition(
                        "mandatory-variable-or-literal-in-println",
                        RuleType.LINT,
                        RuleValueType.BOOLEAN,
                        true,
                    ),
                    RuleDefinition(
                        "mandatory-variable-or-literal-in-readInput",
                        RuleType.LINT,
                        RuleValueType.BOOLEAN,
                        true,
                        onlyVersions = listOf("1.1"),
                    ),
                )
        }
}

class FakePermissionClient : PermissionClient {
    private val permissions = mutableMapOf<Pair<UUID, String>, Access>()
    var failGrant = false

    fun share(
        snippetId: UUID,
        userId: String,
    ) {
        permissions[snippetId to userId] = Access.READ
    }

    fun accessOf(
        snippetId: UUID,
        userId: String,
    ): Access = permissions[snippetId to userId] ?: Access.NONE

    fun reset() {
        permissions.clear()
        failGrant = false
    }

    override fun grantOwner(
        snippetId: UUID,
        userId: String,
    ) {
        if (failGrant) throw DownstreamException("permission-service")
        permissions[snippetId to userId] = Access.OWNER
    }

    override fun check(
        snippetId: UUID,
        userId: String,
    ): Access = accessOf(snippetId, userId)

    override fun snippetIds(
        userId: String,
        relation: Relation,
    ): List<UUID> =
        permissions
            .filter { (key, access) ->
                key.second == userId &&
                    when (relation) {
                        Relation.OWNED -> access == Access.OWNER
                        Relation.SHARED -> access == Access.READ
                        Relation.ALL -> true
                    }
            }.map { (key, _) -> key.first }

    override fun deleteAll(snippetId: UUID) {
        permissions.keys.removeIf { it.first == snippetId }
    }
}

class FakeContentStore : ContentStore {
    val contents = mutableMapOf<String, String>()

    override fun put(
        key: String,
        content: String,
    ) {
        contents[key] = content
    }

    override fun get(key: String): String = contents[key] ?: throw DownstreamException("asset-service")

    override fun delete(key: String) {
        contents.remove(key)
    }
}

class FakeStreamProducer : StreamProducer {
    val published = mutableListOf<Pair<String, Any>>()

    override fun publish(
        stream: String,
        payload: Any,
    ): RecordId {
        published.add(stream to payload)
        return RecordId.of("1-0")
    }

    fun reset() {
        published.clear()
    }
}

@TestConfiguration
class FakeCollaborators {
    @Bean
    @Primary
    fun fakeStreamProducer(): FakeStreamProducer = FakeStreamProducer()

    @Bean
    fun fakeLanguageService(): FakeLanguageService = FakeLanguageService()

    @Bean
    @Primary
    fun fakeLanguageRegistry(language: FakeLanguageService): LanguageRegistry =
        // un segundo lenguaje, solo para poder probar el orden por lenguaje del listado
        LanguageRegistry(mapOf(PRINTSCRIPT to language, OTHER_LANGUAGE to language))

    @Bean
    @Primary
    fun fakePermissionClient(): FakePermissionClient = FakePermissionClient()

    @Bean
    @Primary
    fun fakeContentStore(): FakeContentStore = FakeContentStore()
}
