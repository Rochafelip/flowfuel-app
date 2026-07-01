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
}
