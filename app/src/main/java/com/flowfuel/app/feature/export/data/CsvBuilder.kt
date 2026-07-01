package com.flowfuel.app.feature.export.data

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

fun buildCsvBytes(header: List<String>, rows: List<List<String>>): ByteArray {
    val text = buildString {
        appendLine(header.joinToString(",") { it.csvQuote() })
        rows.forEach { row -> appendLine(row.joinToString(",") { it.csvQuote() }) }
    }
    return UTF8_BOM + text.toByteArray(Charsets.UTF_8)
}

private fun String.csvQuote() = "\"${replace("\"", "\"\"")}\""
