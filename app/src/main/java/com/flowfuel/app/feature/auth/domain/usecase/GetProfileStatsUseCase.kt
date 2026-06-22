package com.flowfuel.app.feature.auth.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.HistoryRepository
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

data class ProfileStats(
    val vehiclesCount: Int,
    val refuelsCount: Int,
    val eventsCount: Int,
)

class GetProfileStatsUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val historyRepository: HistoryRepository,
    private val eventRepository: VehicleEventRepository,
) {
    suspend operator fun invoke(): ProfileStats = coroutineScope {
        val vehicles = (vehicleRepository.getVehicles() as? AppResult.Success)?.value.orEmpty()

        val historyDeferreds = vehicles.map { v ->
            async {
                (historyRepository.getRefuelHistory(v.id, 0, 1) as? AppResult.Success)
                    ?.value?.totalElements ?: 0
            }
        }
        val eventDeferreds = vehicles.map { v ->
            async {
                (eventRepository.getEventsByVehicle(v.id, 0, null) as? AppResult.Success)
                    ?.value?.totalElements ?: 0
            }
        }

        ProfileStats(
            vehiclesCount = vehicles.size,
            refuelsCount = historyDeferreds.awaitAll().sum(),
            eventsCount = eventDeferreds.awaitAll().sum(),
        )
    }
}
