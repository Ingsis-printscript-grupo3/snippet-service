package org.snippetsearcher.snippet.language

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// lo consume la UI para armar la lista de lenguajes al crear un snippet. Es publico del sistema, no de un usuario:
// no lleva @CurrentUser
@RestController
class FileTypeController(
    private val languages: LanguageRegistry,
) {
    @GetMapping("/file-types")
    fun fileTypes(): List<FileType> = languages.fileTypes()
}
