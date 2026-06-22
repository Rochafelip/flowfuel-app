package com.flowfuel.app.core.common

object DateFormatter {
    fun formatBr(iso: String): String {
        val datePart = iso.take(10)
        val parts = datePart.split("-")
        return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else datePart
    }
}
