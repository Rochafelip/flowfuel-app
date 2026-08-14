package com.flowfuel.app.feature.vehicleevent.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import javax.inject.Inject

/**
 * Busca todos os eventos de um veículo, percorrendo a paginação no client
 * (mesmo padrão de GetVehicleEventsTotalUseCase) — a API não expõe um
 * endpoint que já retorne a lista completa.
 */
class GetVehicleEventsUseCase @Inject constructor(
    private val repository: VehicleEventRepository,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<List<VehicleEvent>> {
        val allItems = mutableListOf<VehicleEvent>()
        var page = 0
        while (true) {
            when (val result = repository.getEventsByVehicle(vehicleId, page, category = null)) {
                is AppResult.Success -> {
                    allItems += result.value.items
                    if (!result.value.hasMore) return AppResult.Success(allItems)
                    page++
                }
                is AppResult.Failure -> return result
            }
        }
    }
}
