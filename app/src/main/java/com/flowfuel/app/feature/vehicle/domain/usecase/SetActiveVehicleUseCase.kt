package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import timber.log.Timber
import javax.inject.Inject

class SetActiveVehicleUseCase @Inject constructor(
    private val repository: VehicleRepository,
    private val sessionStore: SessionStore,
) {
    /**
     * Marca [vehicleId] como veículo ativo:
     * 1. Persiste o ID localmente (imediato, sem depender da rede).
     * 2. Chama PUT /vehicles/{id}/active no servidor (melhor esforço).
     */
    suspend operator fun invoke(vehicleId: Int) {
        sessionStore.saveActiveVehicleId(vehicleId)
        val result = repository.setActiveVehicle(vehicleId)
        Timber.d("SetActiveVehicle($vehicleId): $result")
    }
}