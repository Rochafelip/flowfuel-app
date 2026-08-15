package com.flowfuel.app.feature.home.presentation.components

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBr = Locale("pt", "BR")

private val brlFormat: NumberFormat
    get() = NumberFormat.getCurrencyInstance(ptBr)

private val integerFormat: NumberFormat
    get() = NumberFormat.getIntegerInstance(ptBr)

private val decimal2Format: NumberFormat
    get() = NumberFormat.getNumberInstance(ptBr).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBr)

internal fun formatBrl(amount: Double): String = brlFormat.format(amount)

/** Inteiro com separador de milhar, sem casas decimais (ex: odômetro). */
internal fun formatInteger(value: Int): String = integerFormat.format(value)

/** Sempre 2 casas decimais, vírgula pt-BR (ex: consumo, litros/kWh). */
internal fun formatDecimal2(value: Double): String = decimal2Format.format(value)

/** Percentual inteiro, sem casas decimais (ex: fatia do gráfico de gastos). */
internal fun formatPercent(value: Double): String = "${Math.round(value)}%"

/** Converte uma data ISO-8601 (ex: "2024-01-15T10:30:00") para "15/01/2024". */
internal fun formatDate(iso: String): String {
    val datePart = iso.take(10)
    val parts = datePart.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else datePart
}

/**
 * Mesma saída de [formatDate], mas explicitando as duas origens possíveis do
 * backend (timestamp completo vs. data pura) como no formatador equivalente do
 * dashboard web — no Kotlin nenhum dos dois ramos faz conversão de fuso, então
 * não há o bug de off-by-one que motiva a distinção em JS.
 */
internal fun formatActivityDate(iso: String): String = runCatching {
    if (iso.contains("T")) {
        LocalDateTime.parse(iso.take(19)).format(dateFormatter)
    } else {
        LocalDate.parse(iso.take(10)).format(dateFormatter)
    }
}.getOrDefault(formatDate(iso))

/** Texto relativo ao último abastecimento — [days] vem de dias corridos desde [lastRefuelDate]. */
internal fun formatLastRefuelLabel(days: Int?): String = when {
    days == null -> "Nenhum abastecimento ainda"
    days <= 0 -> "Hoje"
    days == 1 -> "Ontem"
    else -> "Há $days dias"
}
