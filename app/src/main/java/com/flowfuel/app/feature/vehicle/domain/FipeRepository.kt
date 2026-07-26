package com.flowfuel.app.feature.vehicle.domain

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType

interface FipeRepository {
    suspend fun getBrands(vehicleType: VehicleType): AppResult<List<FipeOption>>
    suspend fun getModels(vehicleType: VehicleType, brandCode: String): AppResult<List<FipeOption>>
    suspend fun getYears(vehicleType: VehicleType, brandCode: String, modelCode: String): AppResult<List<FipeOption>>
}
