package com.flowfuel.app.feature.history.domain.model

import java.time.LocalDate

data class RefuelItem(
    val id: Int,
    val date: String,           // ISO-8601
    val energyAmount: Double,   // litros ou kWh
    val pricePerUnit: Double,   // R$/L ou R$/kWh
    val totalPrice: Double,
    val fullTank: Boolean,
    val refuelType: String?,    // "FUEL" | "ELECTRIC" | null
    val odometer: Double?,      // leitura absoluta do odômetro em km
    val trip: Double?,          // km desde o último abastecimento
    val consumption: Double?,   // km/L ou km/kWh
)

data class UpdateRefuelRequest(
    val id: Int,
    val vehicleId: Int,
    val odometer: Double,
    val liters: Double,
    val totalPrice: Double,
    val fullTank: Boolean,
    val refuelType: String?,
)

data class RefuelPage(
    val items: List<RefuelItem>,
    val hasMore: Boolean,
    val currentPage: Int,
    val totalElements: Int = 0,
)

enum class FilterPreset { LAST_30_DAYS, LAST_3_MONTHS, THIS_YEAR, CUSTOM }

data class HistoryFilter(
    val preset: FilterPreset? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
) {
    val isActive: Boolean get() = preset != null
}
