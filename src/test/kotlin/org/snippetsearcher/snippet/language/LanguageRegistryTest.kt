package org.snippetsearcher.snippet.language

import org.junit.jupiter.api.Test
import org.snippetsearcher.snippet.support.FakeLanguageService
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals

class LanguageRegistryTest {
    // solo cambia lo que declara el lenguaje: el resto se delega, asi un metodo nuevo en LanguageService
    // no rompe este test
    private class Declaring(
        override val extension: String,
        override val defaultVersion: String = "1.1",
    ) : LanguageService by FakeLanguageService()

    // dos extensiones distintas: con la misma no se notaria que un lenguaje quedo con la extension de otro
    @Test
    fun `pairs every language with its own extension`() {
        val registry = LanguageRegistry(mapOf(PRINTSCRIPT to Declaring("ps"), "otro" to Declaring("ot")))

        val expected = setOf(FileType(PRINTSCRIPT, "ps"), FileType("otro", "ot"))
        assertEquals(expected, registry.fileTypes().toSet())
    }

    @Test
    fun `a missing version is replaced by the default of that language`() {
        val registry = LanguageRegistry(mapOf("otro" to Declaring("ot", "7.0")))

        assertEquals("7.0", registry.versionOrDefault("otro", null))
        assertEquals("1.0", registry.versionOrDefault("otro", "1.0"))
    }

    @Test
    fun `printscript defaults to version 1_1`() {
        assertEquals("1.1", PrintScriptLanguageService(RestClient.create()).defaultVersion)
    }
}
