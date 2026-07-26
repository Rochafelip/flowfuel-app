package com.flowfuel.app.feature.vehicle.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.map
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeApi
import com.flowfuel.app.feature.vehicle.domain.FipeRepository
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FipeRepositoryImpl @Inject constructor(
    private val api: FipeApi,
) : FipeRepository {

    override suspend fun getBrands(vehicleType: VehicleType): AppResult<List<FipeOption>> =
        apiCall { api.getBrands(vehicleType.toFipePath()) }
            .map { dtos -> dtos.map { FipeOption(it.codigo, it.nome) } }

    override suspend fun getModels(vehicleType: VehicleType, brandCode: String): AppResult<List<FipeOption>> =
        apiCall { api.getModels(vehicleType.toFipePath(), brandCode) }
            .map { response -> response.modelos.map { FipeOption(it.codigo.toString(), it.nome) } }

    override suspend fun getYears(vehicleType: VehicleType, brandCode: String, modelCode: String): AppResult<List<FipeOption>> =
        apiCall { api.getYears(vehicleType.toFipePath(), brandCode, modelCode) }
            .map { dtos -> dtos.map { FipeOption(it.codigo, it.nome) } }

    private fun VehicleType.toFipePath(): String = when (this) {
        VehicleType.Car        -> "carros"
        VehicleType.Motorcycle -> "motos"
    }
}
