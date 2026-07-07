package com.flowfuel.app.feature.home.presentation.components

import java.text.NumberFormat
import java.util.Locale

private val brlFormat: NumberFormat
    get() = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

private val kmFormat: NumberFormat
    get() = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }

internal fun formatBrl(amount: Double): String = brlFormat.format(amount)

internal fun formatKm(km: Double): String = kmFormat.format(km)

/** Converte uma data ISO-8601 (ex: "2024-01-15T10:30:00") para "15/01/2024". */
internal fun formatDate(iso: String): String {
    val datePart = iso.take(10)
    val parts = datePart.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else datePart
}
