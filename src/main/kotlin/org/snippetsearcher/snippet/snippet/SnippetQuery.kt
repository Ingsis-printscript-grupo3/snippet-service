package org.snippetsearcher.snippet.snippet

import org.snippetsearcher.snippet.permission.Relation

// US5: que snippets ve el usuario y en que orden. Los filtros en null no filtran
data class SnippetQuery(
    val relation: Relation = Relation.ALL,
    val name: String? = null,
    val language: String? = null,
    val lintStatus: LintStatus? = null,
    val sort: SnippetSort = SnippetSort.NAME,
    val direction: SortDirection = SortDirection.ASC,
) {
    fun matches(snippet: Snippet): Boolean =
        (name.isNullOrBlank() || snippet.name.contains(name, ignoreCase = true)) &&
            (language.isNullOrBlank() || snippet.language.equals(language, ignoreCase = true)) &&
            (lintStatus == null || snippet.lintStatus == lintStatus)

    // a igual valor desempata el nombre, asi el orden es siempre el mismo entre pedidos
    fun comparator(): Comparator<Snippet> {
        val primary = if (direction == SortDirection.ASC) sort.comparator else sort.comparator.reversed()
        return primary.then(SnippetSort.NAME.comparator)
    }
}

// los campos por los que se puede ordenar el listado
enum class SnippetSort(
    val comparator: Comparator<Snippet>,
) {
    NAME(compareBy { it.name.lowercase() }),
    LANGUAGE(compareBy { it.language.lowercase() }),
    LINT_STATUS(compareBy { it.lintStatus }),
}

enum class SortDirection {
    ASC,
    DESC,
}
