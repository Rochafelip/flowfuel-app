package com.flowfuel.app.feature.export.data.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.flowfuel.app.feature.export.data.EventsSummary
import com.flowfuel.app.feature.export.data.RefuelsSummary
import java.io.ByteArrayOutputStream
import javax.inject.Inject

private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val LINE_HEIGHT = 16f

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
        val colWidth = contentWidth / tableHeader.size

        var page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create()
        )
        var canvas = page.canvas
        var y = MARGIN

        fun drawTableHeaderRow() {
            tableHeader.forEachIndexed { i, text -> canvas.drawText(text, MARGIN + i * colWidth, y, headerPaint) }
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
            row.forEachIndexed { i, text -> canvas.drawText(text, MARGIN + i * colWidth, y, cellPaint) }
            y += LINE_HEIGHT
        }

        document.finishPage(page)

        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    private fun Double.formatDecimal() = "%.2f".format(this).replace('.', ',')
}
