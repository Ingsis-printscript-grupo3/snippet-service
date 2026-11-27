package org.snippetsearcher.snippet.language

import org.junit.jupiter.api.Test
import org.snippetsearcher.snippet.support.FakeLanguageService
import kotlin.test.assertEquals

class LanguageRegistryTest {
    // solo cambia la extension: el resto se delega, asi un metodo nuevo en LanguageService no rompe este test
    private class WithExtension(
        override val extension: String,
    ) : LanguageService by FakeLanguageService()

    // dos extensiones distintas: con la misma no se notaria que un lenguaje quedo con la extension de otro
    @Test
    fun `pairs every language with its own extension`() {
        val registry = LanguageRegistry(mapOf(PRINTSCRIPT to WithExtension("ps"), "otro" to WithExtension("ot")))

        val expected = setOf(FileType(PRINTSCRIPT, "ps"), FileType("otro", "ot"))
        assertEquals(expected, registry.fileTypes().toSet())
    }
}
