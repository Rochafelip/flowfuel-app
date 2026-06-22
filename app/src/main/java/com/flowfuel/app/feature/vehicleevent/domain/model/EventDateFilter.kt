package com.flowfuel.app.feature.vehicleevent.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed interface EventDateFilter {
    data object All : EventDateFilter
    data object Last30Days : EventDateFilter
    data object Last3Months : EventDateFilter
    data object ThisYear : EventDateFilter
    data class Custom(val from: LocalDate, val to: LocalDate) : EventDateFilter
}

private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE

fun EventDateFilter.toDateRange(): Pair<String?, String?> = when (this) {
    EventDateFilter.All -> null to null
    EventDateFilter.Last30Days -> LocalDate.now().minusDays(30).format(isoFmt) to null
    EventDateFilter.Last3Months -> LocalDate.now().minusMonths(3).format(isoFmt) to null
    EventDateFilter.ThisYear -> LocalDate.of(LocalDate.now().year, 1, 1).format(isoFmt) to null
    is EventDateFilter.Custom -> from.format(isoFmt) to to.format(isoFmt)
}

fun EventDateFilter.chipLabel(): String = when (this) {
    EventDateFilter.All -> "Tudo"
    EventDateFilter.Last30Days -> "30 dias"
    EventDateFilter.Last3Months -> "3 meses"
    EventDateFilter.ThisYear -> "Este ano"
    is EventDateFilter.Custom -> {
        val fmt = DateTimeFormatter.ofPattern("dd/MM/yy")
        "${from.format(fmt)}–${to.format(fmt)}"
    }
}
