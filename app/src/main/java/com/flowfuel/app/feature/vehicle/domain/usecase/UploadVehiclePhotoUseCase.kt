package com.flowfuel.app.feature.vehicle.domain.usecase

import android.net.Uri
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import javax.inject.Inject

class UploadVehiclePhotoUseCase @Inject constructor(
    private val repository: VehicleRepository,
) {
    suspend operator fun invoke(vehicleId: Int, uri: Uri): AppResult<String> =
        repository.uploadVehiclePhoto(vehicleId, uri)
}
