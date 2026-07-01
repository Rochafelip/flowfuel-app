package com.flowfuel.app.feature.export.data.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.flowfuel.app.feature.export.data.EventsSummary
import com.flowfuel.app.feature.export.data.RefuelsSummary
import java.io.ByteArrayOutputStream
import javax.inject.Inject

private const val PAGE_WIDTH = 842
private const val PAGE_HEIGHT = 595
private const val MARGIN = 40f
private const val LINE_HEIGHT = 16f
private const val CELL_PADDING = 6f
private const val MIN_COL_WIDTH = 28f
private const val MAX_DESIRED_COL_WIDTH = 160f

internal fun distributeColumnWidths(desired: List<Float>, availableWidth: Float, minWidth: Float): List<Float> {
    if (desired.isEmpty()) return desired
    val total = desired.sum()
    if (total <= availableWidth) return desired
    val scale = availableWidth / total
    return desired.map { maxOf(it * scale, minWidth) }
}

class PdfReportWriter @Inject constructor() {

    fun writeRefuelsReport(
        vehicleLabel: String,
        periodLabel: String,
        summary: RefuelsSummary,
        energyUnit: String,
        consumptionUnit: String,
        tableHeader: List<String>,
        tableRows: List<List<String>>,
    ): ByteArray {
        val summaryLines = listOf(
            "Total gasto: R$ ${summary.totalSpent.formatDecimal()}",
            "Total abastecido: ${summary.totalEnergy.formatDecimal()} $energyUnit",
            "Consumo médio: ${summary.averageConsumption?.formatDecimal() ?: "-"} $consumptionUnit",
            "Abastecimentos: ${summary.count}",
        )
        return write("Relatório de Abastecimentos", vehicleLabel, periodLabel, summaryLines, tableHeader, tableRows)
    }

    fun writeEventsReport(
        vehicleLabel: String,
        periodLabel: String,
        summary: EventsSummary,
        tableHeader: List<String>,
        tableRows: List<List<String>>,
    ): ByteArray {
        val summaryLines = buildList {
            add("Total gasto: R$ ${summary.totalSpent.formatDecimal()}")
            add("Eventos: ${summary.count}")
            summary.countByCategory.forEach { (category, count) -> add("${category.label}: $count") }
        }
        return write("Relatório de Eventos", vehicleLabel, periodLabel, summaryLines, tableHeader, tableRows)
    }

    private fun write(
        title: String,
        vehicleLabel: String,
        periodLabel: String,
        summaryLines: List<String>,
        tableHeader: List<String>,
        tableRows: List<List<String>>,
    ): ByteArray {
        val document = PdfDocument()
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
        val labelPaint = Paint().apply { textSize = 12f; color = Color.DKGRAY }
        val headerPaint = Paint().apply { textSize = 10f; isFakeBoldText = true; color = Color.BLACK }
        val cellPaint = Paint().apply { textSize = 9f; color = Color.BLACK }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        val desiredColWidths = tableHeader.indices.map { i ->
            val headerWidth = headerPaint.measureText(tableHeader[i])
            val maxCellWidth = tableRows.maxOfOrNull { row -> cellPaint.measureText(row.getOrElse(i) { "" }) } ?: 0f
            minOf(maxOf(headerWidth, maxCellWidth) + CELL_PADDING, MAX_DESIRED_COL_WIDTH)
        }
        val colWidths = distributeColumnWidths(desiredColWidths, contentWidth, MIN_COL_WIDTH)
        val colOffsets = colWidths.runningFold(0f) { acc, w -> acc + w }

        var page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create()
        )
        var canvas = page.canvas
        var y = MARGIN

        fun drawRow(cells: List<String>, paint: Paint) {
            cells.forEachIndexed { i, text ->
                val maxWidth = colWidths[i] - CELL_PADDING
                canvas.drawText(paint.ellipsize(text, maxWidth), MARGIN + colOffsets[i], y, paint)
            }
        }

        fun drawTableHeaderRow() {
            drawRow(tableHeader, headerPaint)
            y += LINE_HEIGHT
            canvas.drawLine(MARGIN, y - 4f, MARGIN + contentWidth, y - 4f, linePaint)
        }

        fun newPage() {
            document.finishPage(page)
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create()
            )
            canvas = page.canvas
            y = MARGIN
        }

        canvas.drawText(title, MARGIN, y, titlePaint); y += 24f
        canvas.drawText(vehicleLabel, MARGIN, y, labelPaint); y += LINE_HEIGHT
        canvas.drawText(periodLabel, MARGIN, y, labelPaint); y += LINE_HEIGHT + 8f
        summaryLines.forEach { line -> canvas.drawText(line, MARGIN, y, labelPaint); y += LINE_HEIGHT }
        y += 8f
        drawTableHeaderRow()

        tableRows.forEach { row ->
            if (y > PAGE_HEIGHT - MARGIN) {
                newPage()
                drawTableHeaderRow()
            }
            drawRow(row, cellPaint)
            y += LINE_HEIGHT
        }

        document.finishPage(page)

        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    private fun Double.formatDecimal() = "%.2f".format(this).replace('.', ',')

    private fun Paint.ellipsize(text: String, maxWidth: Float): String {
        if (measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellipsisWidth = measureText(ellipsis)
        var end = text.length
        while (end > 0 && measureText(text, 0, end) + ellipsisWidth > maxWidth) end--
        return text.substring(0, end) + ellipsis
    }
}
