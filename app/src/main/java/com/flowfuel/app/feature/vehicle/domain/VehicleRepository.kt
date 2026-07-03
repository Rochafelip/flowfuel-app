package com.flowfuel.app.feature.vehicle.domain

import android.net.Uri
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.PagedVehicles
import com.flowfuel.app.feature.vehicle.domain.model.UpdateVehicleRequest
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType

interface VehicleRepository {
    suspend fun getVehicles(): AppResult<List<Vehicle>>

    suspend fun getVehiclesPage(page: Int): AppResult<PagedVehicles>

    /** Marca o veículo como ativo no servidor. Retorna sucesso mesmo para 204 sem corpo. */
    suspend fun setActiveVehicle(id: Int): AppResult<Unit>

    /** Remove o veículo permanentemente. Retorna sucesso mesmo para 204 sem corpo. */
    suspend fun deleteVehicle(id: Int): AppResult<Unit>

    suspend fun getVehicleById(id: Int): AppResult<Vehicle>

    suspend fun createVehicle(
        brand: String,
        model: String,
        manufactureYear: Int,
        modelYear: Int,
        licensePlate: String,
        color: String?,
        type: VehicleType,
        energyType: EnergyType,
        fuelType: FuelType?,
        odometerKm: Int,
        tankCapacityL: Double?,
        batteryCapacityKwh: Double?,
    ): AppResult<Vehicle>

    suspend fun updateVehicle(id: Int, request: UpdateVehicleRequest): AppResult<Vehicle>

    /** Atualiza apenas o odômetro. Retorna sucesso mesmo para 204 sem corpo. */
    suspend fun updateOdometer(vehicleId: Int, newKm: Int): AppResult<Unit>

    /** Envia a foto do veículo recém-criado. Comprime a imagem antes do upload. */
    suspend fun uploadVehiclePhoto(vehicleId: Int, uri: Uri): AppResult<String>
}