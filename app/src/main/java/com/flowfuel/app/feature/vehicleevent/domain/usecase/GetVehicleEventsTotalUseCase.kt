package com.flowfuel.app.feature.vehicleevent.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import javax.inject.Inject

/**
 * Soma o valor de todos os eventos (manutenção, troca de óleo etc.) de um veículo.
 * Paginação percorrida no client pois a API não expõe um total agregado — mesmo
 * padrão usado em [com.flowfuel.app.feature.export.data.ExportRepositoryImpl].
 */
class GetVehicleEventsTotalUseCase @Inject constructor(
    private val repository: VehicleEventRepository,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<Double> {
        var total = 0.0
        var page = 0
        while (true) {
            when (val result = repository.getEventsByVehicle(vehicleId, page, category = null)) {
                is AppResult.Success -> {
                    total += result.value.items.sumOf { it.amount ?: 0.0 }
                    if (!result.value.hasMore) return AppResult.Success(total)
                    page++
                }
                is AppResult.Failure -> return result
            }
        }
    }
}
