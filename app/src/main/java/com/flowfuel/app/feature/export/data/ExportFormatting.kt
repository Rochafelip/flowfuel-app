package com.flowfuel.app.feature.export.data

import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val PERIOD_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun vehicleLabel(vehicle: Vehicle): String {
    val plate = vehicle.licensePlate?.trim()?.takeIf { it.isNotEmpty() }
    return if (plate != null) "${vehicle.brand} ${vehicle.model} — $plate" else "${vehicle.brand} ${vehicle.model}"
}

fun periodLabel(startDate: String?, endDate: String?): String {
    if (startDate == null || endDate == null) return "Todo o histórico"
    val start = LocalDate.parse(startDate).format(PERIOD_FORMATTER)
    val end = LocalDate.parse(endDate).format(PERIOD_FORMATTER)
    return "$start – $end"
}

fun energyUnit(vehicle: Vehicle): String = if (vehicle.energyType == EnergyType.Electric) "kWh" else "L"

fun consumptionUnit(vehicle: Vehicle): String = if (vehicle.energyType == EnergyType.Electric) "km/kWh" else "km/L"

fun pdfDate(iso: String): String {
    if (iso.isBlank()) return "-"
    return runCatching {
        LocalDateTime.parse(iso.take(19)).format(PERIOD_FORMATTER)
    }.getOrElse {
        runCatching { LocalDate.parse(iso.take(10)).format(PERIOD_FORMATTER) }.getOrDefault(iso.take(10))
    }
}
