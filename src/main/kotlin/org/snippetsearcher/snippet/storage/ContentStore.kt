package org.snippetsearcher.snippet.storage

// donde vive el codigo de los snippets. La base solo guarda la metadata
interface ContentStore {
    // crea o pisa
    fun put(
        key: String,
        content: String,
    )

    fun get(key: String): String

    // si no existia no es un error: lo que se queria es que no este
    fun delete(key: String)
}
