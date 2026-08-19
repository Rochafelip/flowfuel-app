package com.flowfuel.app.feature.export.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.ContextCompat
import com.flowfuel.app.R
import com.flowfuel.app.feature.export.data.EventsSummary
import com.flowfuel.app.feature.export.data.RefuelsSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.max

private const val PAGE_WIDTH = 842
private const val PAGE_HEIGHT = 595
private const val MARGIN = 40f
private const val LINE_HEIGHT = 16f
private const val CELL_PADDING = 6f
private const val MIN_COL_WIDTH = 28f
private const val MAX_DESIRED_COL_WIDTH = 160f

private const val HEADER_BAND_HEIGHT = 70f
private const val SECTION_GAP = 12f
private const val SUMMARY_BOX_PADDING = 12f
private const val SUMMARY_VALUE_SIZE = 20f
private const val SUMMARY_VALUE_LINE_HEIGHT = 24f
private const val SUMMARY_LABEL_LINE_HEIGHT = 14f
private const val TABLE_HEADER_HEIGHT = 22f
private const val FOOTER_RESERVED_HEIGHT = 26f
private const val LOGO_SIZE = 20f

private val COLOR_BAND = Color.parseColor("#334155")
private val COLOR_ON_BAND = Color.WHITE
private val COLOR_ON_BAND_MUTED = Color.parseColor("#CBD5E1")
private val COLOR_SUMMARY_BG = Color.parseColor("#ECFDF5")
private val COLOR_SUMMARY_VALUE = Color.parseColor("#0B6E4F")
private val COLOR_ZEBRA = Color.parseColor("#F1F5F9")
private val COLOR_FOOTER = Color.parseColor("#64748B")

private val FOOTER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

internal fun distributeColumnWidths(desired: List<Float>, availableWidth: Float, minWidth: Float): List<Float> {
    if (desired.isEmpty()) return desired
    val total = desired.sum()
    if (total <= availableWidth) return desired
    val scale = availableWidth / total
    return desired.map { maxOf(it * scale, minWidth) }
}

internal fun calculatePageCount(rowCount: Int, firstPageCapacity: Int, otherPageCapacity: Int): Int {
    if (rowCount <= firstPageCapacity) return 1
    val remaining = rowCount - firstPageCapacity
    val extraPages = ceil(remaining.toDouble() / otherPageCapacity).toInt()
    return 1 + extraPages
}

internal fun footerText(pageIndex: Int, totalPages: Int, generatedOnLabel: String): String =
    "Gerado em $generatedOnLabel · Página $pageIndex de $totalPages"

class PdfReportWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun writeRefuelsReport(
        vehicleLabel: String,
        periodLabel: String,
        summary: RefuelsSummary,
        energyUnit: String,
        consumptionUnit: String,
        tableHeader: List<String>,
        tableRows: List<List<String>>,
    ): ByteArray {
        val detailLine = buildString {
            append("${summary.totalEnergy.formatDecimal()} $energyUnit abastecidos")
            summary.averageConsumption?.let { append(" · ${it.formatDecimal()} $consumptionUnit médio") }
            append(" · ${summary.count} abastecimento${if (summary.count == 1) "" else "s"}")
        }
        return write(
            title = "Relatório de Abastecimentos",
            vehicleLabel = vehicleLabel,
            periodLabel = periodLabel,
            totalLabel = "Total gasto no período",
            totalValue = "R$ ${summary.totalSpent.formatDecimal()}",
            detailLines = listOf(detailLine),
            tableHeader = tableHeader,
            tableRows = tableRows,
        )
    }

    fun writeEventsReport(
        vehicleLabel: String,
        periodLabel: String,
        summary: EventsSummary,
        tableHeader: List<String>,
        tableRows: List<List<String>>,
    ): ByteArray {
        val detailLines = buildList {
            add("${summary.count} evento${if (summary.count == 1) "" else "s"} no período")
            summary.countByCategory.forEach { (category, count) -> add("${category.label}: $count") }
        }
        return write(
            title = "Relatório de Eventos",
            vehicleLabel = vehicleLabel,
            periodLabel = periodLabel,
            totalLabel = "Total gasto no período",
            totalValue = "R$ ${summary.totalSpent.formatDecimal()}",
            detailLines = detailLines,
            tableHeader = tableHeader,
            tableRows = tableRows,
        )
    }

    private fun write(
        title: String,
        vehicleLabel: String,
        periodLabel: String,
        totalLabel: String,
        totalValue: String,
        detailLines: List<String>,
        tableHeader: List<String>,
        tableRows: List<List<String>>,
    ): ByteArray {
        val document = PdfDocument()

        val wordmarkPaint = Paint().apply { textSize = 13f; isFakeBoldText = true; isAntiAlias = true; color = COLOR_ON_BAND }
        val titlePaint = Paint().apply { textSize = 15f; isFakeBoldText = true; isAntiAlias = true; color = COLOR_ON_BAND }
        val subtitlePaint = Paint().apply { textSize = 10f; isAntiAlias = true; color = COLOR_ON_BAND_MUTED }
        val summaryValuePaint =
            Paint().apply { textSize = SUMMARY_VALUE_SIZE; isFakeBoldText = true; isAntiAlias = true; color = COLOR_SUMMARY_VALUE }
        val summaryLabelPaint = Paint().apply { textSize = 10f; isAntiAlias = true; color = Color.DKGRAY }
        val summaryDetailPaint = Paint().apply { textSize = 9.5f; isAntiAlias = true; color = Color.DKGRAY }
        val headerPaint = Paint().apply { textSize = 9.5f; isFakeBoldText = true; isAntiAlias = true; color = COLOR_ON_BAND }
        val cellPaint = Paint().apply { textSize = 9f; isAntiAlias = true; color = Color.BLACK }
        val footerPaint =
            Paint().apply { textSize = 8f; isAntiAlias = true; color = COLOR_FOOTER; textAlign = Paint.Align.CENTER }
        val fillPaint = Paint()

        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        val desiredColWidths = tableHeader.indices.map { i ->
            val headerWidth = headerPaint.measureText(tableHeader[i])
            val maxCellWidth = tableRows.maxOfOrNull { row -> cellPaint.measureText(row.getOrElse(i) { "" }) } ?: 0f
            minOf(maxOf(headerWidth, maxCellWidth) + CELL_PADDING, MAX_DESIRED_COL_WIDTH)
        }
        val colWidths = distributeColumnWidths(desiredColWidths, contentWidth, MIN_COL_WIDTH)
        val colOffsets = colWidths.runningFold(0f) { acc, w -> acc + w }

        val summaryBoxHeight = SUMMARY_BOX_PADDING * 2 + SUMMARY_VALUE_LINE_HEIGHT + SUMMARY_LABEL_LINE_HEIGHT +
            detailLines.size * LINE_HEIGHT
        val firstPageTableTop = HEADER_BAND_HEIGHT + SECTION_GAP + summaryBoxHeight + SECTION_GAP

        val firstPageCapacity = max(
            0,
            ((PAGE_HEIGHT - FOOTER_RESERVED_HEIGHT - firstPageTableTop - TABLE_HEADER_HEIGHT) / LINE_HEIGHT).toInt(),
        )
        val otherPageCapacity = max(
            1,
            ((PAGE_HEIGHT - FOOTER_RESERVED_HEIGHT - MARGIN - TABLE_HEADER_HEIGHT) / LINE_HEIGHT).toInt(),
        )
        val totalPages = calculatePageCount(tableRows.size, firstPageCapacity, otherPageCapacity)
        val generatedOnLabel = LocalDate.now().format(FOOTER_DATE_FORMAT)
        val logoBitmap = loadLogoBitmap()

        var pageIndex = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create())
        var canvas = page.canvas

        fun drawHeaderBand() {
            fillPaint.color = COLOR_BAND
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), HEADER_BAND_HEIGHT, fillPaint)
            var bandY = 20f
            logoBitmap?.let { canvas.drawBitmap(it, MARGIN, bandY - LOGO_SIZE + 4f, null) }
            val wordmarkX = if (logoBitmap != null) MARGIN + LOGO_SIZE + 6f else MARGIN
            canvas.drawText("FlowFuel", wordmarkX, bandY, wordmarkPaint)
            bandY += 18f
            canvas.drawText(title, MARGIN, bandY, titlePaint)
            bandY += 16f
            canvas.drawText("$vehicleLabel · $periodLabel", MARGIN, bandY, subtitlePaint)
        }

        fun drawSummaryBox(top: Float) {
            fillPaint.color = COLOR_SUMMARY_BG
            canvas.drawRoundRect(MARGIN, top, PAGE_WIDTH - MARGIN, top + summaryBoxHeight, 6f, 6f, fillPaint)
            var y = top + SUMMARY_BOX_PADDING + SUMMARY_VALUE_SIZE
            canvas.drawText(totalValue, MARGIN + SUMMARY_BOX_PADDING, y, summaryValuePaint)
            y += SUMMARY_LABEL_LINE_HEIGHT
            canvas.drawText(totalLabel, MARGIN + SUMMARY_BOX_PADDING, y, summaryLabelPaint)
            detailLines.forEach { line ->
                y += LINE_HEIGHT
                canvas.drawText(line, MARGIN + SUMMARY_BOX_PADDING, y, summaryDetailPaint)
            }
        }

        fun drawTableHeaderRow(top: Float): Float {
            fillPaint.color = COLOR_BAND
            canvas.drawRect(MARGIN, top, PAGE_WIDTH - MARGIN, top + TABLE_HEADER_HEIGHT, fillPaint)
            val textBaseline = top + TABLE_HEADER_HEIGHT - 7f
            tableHeader.forEachIndexed { i, text ->
                val maxWidth = colWidths[i] - CELL_PADDING
                canvas.drawText(
                    headerPaint.ellipsize(text, maxWidth),
                    MARGIN + colOffsets[i] + CELL_PADDING / 2,
                    textBaseline,
                    headerPaint,
                )
            }
            return top + TABLE_HEADER_HEIGHT
        }

        fun drawFooter() {
            canvas.drawText(footerText(pageIndex, totalPages, generatedOnLabel), PAGE_WIDTH / 2f, PAGE_HEIGHT - 12f, footerPaint)
        }

        drawHeaderBand()
        drawSummaryBox(HEADER_BAND_HEIGHT + SECTION_GAP)
        var y = drawTableHeaderRow(firstPageTableTop)

        tableRows.forEachIndexed { rowIndex, row ->
            if (y + LINE_HEIGHT > PAGE_HEIGHT - FOOTER_RESERVED_HEIGHT) {
                drawFooter()
                document.finishPage(page)
                pageIndex++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create())
                canvas = page.canvas
                y = drawTableHeaderRow(MARGIN)
            }
            if (rowIndex % 2 == 1) {
                fillPaint.color = COLOR_ZEBRA
                canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + LINE_HEIGHT, fillPaint)
            }
            row.forEachIndexed { i, text ->
                val maxWidth = colWidths[i] - CELL_PADDING
                canvas.drawText(
                    cellPaint.ellipsize(text, maxWidth),
                    MARGIN + colOffsets[i] + CELL_PADDING / 2,
                    y + LINE_HEIGHT - 4f,
                    cellPaint,
                )
            }
            y += LINE_HEIGHT
        }

        drawFooter()
        document.finishPage(page)

        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    private fun loadLogoBitmap(): Bitmap? = runCatching {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground) ?: return@runCatching null
        val size = LOGO_SIZE.toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bitmap
    }.getOrNull()

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
