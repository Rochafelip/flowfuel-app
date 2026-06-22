package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

class DeleteVehicleUseCase @Inject constructor(
    private val repository: VehicleRepository,
    private val sessionStore: SessionStore,
) {
    /**
     * Remove o veículo no servidor e, se ele era o veículo ativo salvo localmente,
     * limpa o ID do DataStore para evitar referência a um veículo inexistente.
     */
    suspend operator fun invoke(vehicleId: Int): AppResult<Unit> {
        val result = repository.deleteVehicle(vehicleId)
        if (result is AppResult.Success) {
            val storedId = sessionStore.activeVehicleIdFlow.first()
            if (storedId == vehicleId) {
                sessionStore.clearActiveVehicleId()
                Timber.d("DeleteVehicle($vehicleId): era o ativo — ID local limpo")
            }
        }
        return result
    }
}
