package com.flowfuel.app.feature.export.data

import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent

data class RefuelsSummary(
    val totalSpent: Double,
    val totalEnergy: Double,
    val averageConsumption: Double?,
    val count: Int,
)

fun buildRefuelsSummary(items: List<RefuelItem>): RefuelsSummary {
    val consumptions = items.mapNotNull { it.consumption }
    return RefuelsSummary(
        totalSpent = items.sumOf { it.totalPrice },
        totalEnergy = items.sumOf { it.energyAmount },
        averageConsumption = if (consumptions.isEmpty()) null else consumptions.average(),
        count = items.size,
    )
}

data class EventsSummary(
    val totalSpent: Double,
    val countByCategory: List<Pair<EventCategory, Int>>,
    val count: Int,
)

fun buildEventsSummary(items: List<VehicleEvent>): EventsSummary {
    val countByCategory = items.groupingBy { it.category }.eachCount()
        .toList()
        .sortedWith(compareBy<Pair<EventCategory, Int>> { -it.second }.thenBy { it.first.name })
    return EventsSummary(
        totalSpent = items.sumOf { it.amount ?: 0.0 },
        countByCategory = countByCategory,
        count = items.size,
    )
}
