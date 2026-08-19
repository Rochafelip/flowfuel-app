package com.flowfuel.app.feature.export.data.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfReportWriterTest {

    @Test
    fun `distributeColumnWidths returns desired widths unchanged when they fit`() {
        val result = distributeColumnWidths(desired = listOf(50f, 80f, 30f), availableWidth = 200f, minWidth = 10f)

        assertEquals(listOf(50f, 80f, 30f), result)
    }

    @Test
    fun `distributeColumnWidths scales down proportionally when desired widths overflow`() {
        val result = distributeColumnWidths(desired = listOf(100f, 100f), availableWidth = 100f, minWidth = 10f)

        assertEquals(listOf(50f, 50f), result)
    }

    @Test
    fun `distributeColumnWidths never scales a column below minWidth`() {
        val result = distributeColumnWidths(desired = listOf(10f, 1000f), availableWidth = 100f, minWidth = 10f)

        assertEquals(10f, result[0], 0.01f)
    }

    @Test
    fun `distributeColumnWidths handles empty input`() {
        assertEquals(emptyList<Float>(), distributeColumnWidths(desired = emptyList(), availableWidth = 200f, minWidth = 10f))
    }

    @Test
    fun `calculatePageCount returns one page when there are no rows`() {
        assertEquals(1, calculatePageCount(rowCount = 0, firstPageCapacity = 20, otherPageCapacity = 30))
    }

    @Test
    fun `calculatePageCount returns one page when rows exactly fill the first page`() {
        assertEquals(1, calculatePageCount(rowCount = 20, firstPageCapacity = 20, otherPageCapacity = 30))
    }

    @Test
    fun `calculatePageCount returns two pages when one row overflows the first page`() {
        assertEquals(2, calculatePageCount(rowCount = 21, firstPageCapacity = 20, otherPageCapacity = 30))
    }

    @Test
    fun `calculatePageCount does not add a blank page when rows exactly fill later pages`() {
        assertEquals(2, calculatePageCount(rowCount = 50, firstPageCapacity = 20, otherPageCapacity = 30))
    }

    @Test
    fun `calculatePageCount adds a third page for the remainder`() {
        assertEquals(3, calculatePageCount(rowCount = 51, firstPageCapacity = 20, otherPageCapacity = 30))
    }

    @Test
    fun `footerText formats generated date and page counter`() {
        assertEquals(
            "Gerado em 19/08/2026 · Página 1 de 3",
            footerText(pageIndex = 1, totalPages = 3, generatedOnLabel = "19/08/2026"),
        )
    }
}
