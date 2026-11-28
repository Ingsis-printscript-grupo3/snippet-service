package org.snippetsearcher.snippet.snippet

import org.snippetsearcher.snippet.common.CurrentUser
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private val PLAIN_UTF8 = MediaType(MediaType.TEXT_PLAIN, Charsets.UTF_8)

// responde el archivo y no un JSON: el navegador lo guarda con el nombre de Content-Disposition
@RestController
@RequestMapping("/snippets")
class SnippetDownloadController(
    private val service: SnippetDownloadService,
) {
    // US13
    @GetMapping("/{id}/download")
    fun download(
        @CurrentUser userId: String,
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "ORIGINAL") version: DownloadVersion,
    ): ResponseEntity<ByteArray> {
        val file = service.download(userId, id, version)
        val disposition = ContentDisposition.attachment().filename(file.fileName, Charsets.UTF_8).build()
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .contentType(PLAIN_UTF8)
            .body(file.content.toByteArray(Charsets.UTF_8))
    }
}
