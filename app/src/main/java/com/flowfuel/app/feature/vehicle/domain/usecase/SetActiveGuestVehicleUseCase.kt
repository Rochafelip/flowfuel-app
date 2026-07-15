package com.flowfuel.app.feature.vehicle.domain.usecase

import com.flowfuel.app.core.datastore.SessionStore
import javax.inject.Inject

/**
 * Marca [vehicleId] (um veículo emprestado, não do usuário) como ativo — só
 * localmente. Diferente de [SetActiveVehicleUseCase], nunca chama
 * PUT /vehicles/{id}/active: esse endpoint é exclusivo do dono, o backend
 * escopa `isActive` via `findByUserId`, e o convidado não tem posse do veículo.
 */
class SetActiveGuestVehicleUseCase @Inject constructor(
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(vehicleId: Int) {
        sessionStore.saveActiveVehicleId(vehicleId, isGuest = true)
    }
}
