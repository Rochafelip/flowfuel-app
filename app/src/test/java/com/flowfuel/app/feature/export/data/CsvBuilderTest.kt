package com.flowfuel.app.feature.export.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvBuilderTest {

    private val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    @Test
    fun `buildCsvBytes prefixes content with UTF-8 BOM`() {
        val bytes = buildCsvBytes(header = listOf("Data", "Preço"), rows = emptyList())

        assertArrayEquals(bom, bytes.copyOfRange(0, 3))
    }

    @Test
    fun `buildCsvBytes joins header and rows with semicolon preserving accents`() {
        val bytes = buildCsvBytes(
            header = listOf("Data", "Preço/unidade", "Tanque cheio"),
            rows = listOf(listOf("2026-01-05", "5,50", "Sim")),
        )

        val text = String(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8)

        assertEquals("Data;Preço/unidade;Tanque cheio\n2026-01-05;5,50;Sim\n", text)
    }

    @Test
    fun `buildCsvBytes escapes fields containing semicolon, quotes or newline`() {
        val bytes = buildCsvBytes(
            header = listOf("Título"),
            rows = listOf(
                listOf("Troca de óleo; revisão"),
                listOf("Nota com \"aspas\""),
            ),
        )

        val text = String(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8)

        assertEquals(
            "Título\n\"Troca de óleo; revisão\"\n\"Nota com \"\"aspas\"\"\"\n",
            text,
        )
    }
}
