package com.flowfuel.app.feature.vehicle.data

import android.net.Uri
import com.flowfuel.app.BuildConfig
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.map
import com.flowfuel.app.core.media.ImagePickerHelper
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.feature.vehicle.data.remote.CreateVehicleRequestDto
import com.flowfuel.app.feature.vehicle.data.remote.UpdateVehicleRequestDto
import com.flowfuel.app.feature.vehicle.data.remote.VehicleApi
import com.flowfuel.app.feature.vehicle.data.remote.VehicleResponseDto
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.PagedVehicles
import com.flowfuel.app.feature.vehicle.domain.model.UpdateVehicleRequest
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val api: VehicleApi,
    private val imagePickerHelper: ImagePickerHelper,
) : VehicleRepository {

    // ─── Listagem (paginada) ──────────────────────────────────────────────────

    override suspend fun getVehicles(): AppResult<List<Vehicle>> =
        apiCall { api.getVehicles() }.map { paged ->
            paged.content.map { dto -> dto.toDomain() }
        }

    override suspend fun getVehiclesPage(page: Int): AppResult<PagedVehicles> =
        apiCall { api.getVehicles(page = page, size = 20) }.map { paged ->
            PagedVehicles(
                items = paged.content.map { dto -> dto.toDomain() },
                currentPage = paged.page,
                totalPages = paged.totalPages,
                totalElements = paged.totalElements,
            )
        }

    // ─── Detalhes ─────────────────────────────────────────────────────────────

    override suspend fun getVehicleById(id: Int): AppResult<Vehicle> =
        apiCall { api.getVehicleById(id) }.map { it.toDomain() }

    // ─── Ativação ─────────────────────────────────────────────────────────────

    /**
     * Chama PUT /vehicles/{id}/active.
     * Usa try-catch manual porque [ResponseBody]? não passa pelo conversor JSON
     * e funciona tanto para 200+corpo quanto para 204 sem corpo.
     */
    override suspend fun setActiveVehicle(id: Int): AppResult<Unit> = try {
        api.setActiveVehicle(id)?.close()   // fecha o body (evita leak); null em 204
        AppResult.Success(Unit)
    } catch (e: HttpException) {
        Timber.w("setActiveVehicle: HTTP ${e.code()}")
        if (e.code() == 401) AppResult.Failure(AppError.Unauthorized)
        else AppResult.Failure(AppError.Api("HTTP_${e.code()}", e.message()))
    } catch (e: IOException) {
        Timber.w(e, "setActiveVehicle: network error")
        AppResult.Failure(AppError.Network)
    } catch (e: Throwable) {
        Timber.e(e, "setActiveVehicle: unexpected error")
        AppResult.Failure(AppError.Unknown(e))
    }

    // ─── Exclusão ─────────────────────────────────────────────────────────────

    /**
     * Chama DELETE /vehicles/{id}.
     * Usa try-catch manual (mesmo padrão de [setActiveVehicle]) pois [ResponseBody]?
     * não passa pelo conversor JSON e funciona tanto com 200 quanto com 204.
     */
    override suspend fun deleteVehicle(id: Int): AppResult<Unit> = try {
        api.deleteVehicle(id)?.close()
        AppResult.Success(Unit)
    } catch (e: HttpException) {
        Timber.w("deleteVehicle: HTTP ${e.code()}")
        if (e.code() == 401) AppResult.Failure(AppError.Unauthorized)
        else AppResult.Failure(AppError.Api("HTTP_${e.code()}", e.message()))
    } catch (e: IOException) {
        Timber.w(e, "deleteVehicle: network error")
        AppResult.Failure(AppError.Network)
    } catch (e: Throwable) {
        Timber.e(e, "deleteVehicle: unexpected error")
        AppResult.Failure(AppError.Unknown(e))
    }

    // ─── Odômetro ─────────────────────────────────────────────────────────────

    /** Chama PUT /vehicles/{id}/odometer com o novo valor no query param `currentKm`. */
    override suspend fun updateOdometer(vehicleId: Int, newKm: Int): AppResult<Unit> =
        apiCall { api.updateOdometer(vehicleId, newKm) }.map {}

    // ─── Criação ──────────────────────────────────────────────────────────────

    override suspend fun createVehicle(
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
    ): AppResult<Vehicle> {
        val body = CreateVehicleRequestDto(
            brand = brand.trim(),
            model = model.trim(),
            manufactureYear = manufactureYear,
            modelYear = modelYear,
            licensePlate = licensePlate.uppercase().replace("-", ""),
            color = color?.trim()?.takeIf { it.isNotBlank() },
            type = type.apiValue,
            energyType = energyType.apiValue,
            fuelSubType = fuelType?.apiValue,
            currentKm = odometerKm,
            capacity = tankCapacityL ?: batteryCapacityKwh,
        )

        return apiCall { api.createVehicle(body) }.map { dto -> dto.toDomain() }
    }

    // ─── Edição ───────────────────────────────────────────────────────────────

    override suspend fun updateVehicle(id: Int, request: UpdateVehicleRequest): AppResult<Vehicle> {
        val body = UpdateVehicleRequestDto(
            brand = request.brand.trim(),
            model = request.model.trim(),
            manufactureYear = request.manufactureYear,
            modelYear = request.modelYear,
            licensePlate = request.licensePlate?.uppercase()?.replace("-", ""),
            color = request.color?.trim()?.takeIf { it.isNotBlank() },
            type = request.type.apiValue,
            energyType = request.energyType.apiValue,
            fuelSubType = request.fuelType?.apiValue,
            currentKm = request.odometerKm,
            capacity = request.tankCapacityL ?: request.batteryCapacityKwh,
        )
        return apiCall { api.updateVehicle(id, body) }.map { it.toDomain() }
    }

    // ─── Foto ─────────────────────────────────────────────────────────────────

    override suspend fun uploadVehiclePhoto(vehicleId: Int, uri: Uri): AppResult<String> = try {
        val compressed = imagePickerHelper.compressToJpeg(uri)
        val requestBody = compressed.toRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "vehicle.jpg", requestBody)
        apiCall { api.uploadVehiclePhoto(vehicleId, part) }.map { it.internalUrl.orEmpty() }
    } catch (e: Throwable) {
        Timber.e(e, "VehicleRepo › erro ao comprimir imagem")
        AppResult.Failure(AppError.Unknown(e))
    }

    /**
     * Chama DELETE /vehicles/{id}/photo.
     * Mesmo padrão try-catch manual de [deleteVehicle]/[setActiveVehicle].
     */
    override suspend fun deletePhoto(vehicleId: Int): AppResult<Unit> = try {
        api.deleteVehiclePhoto(vehicleId)?.close()
        AppResult.Success(Unit)
    } catch (e: HttpException) {
        Timber.w("deletePhoto: HTTP ${e.code()}")
        if (e.code() == 401) AppResult.Failure(AppError.Unauthorized)
        else AppResult.Failure(AppError.Api("HTTP_${e.code()}", e.message()))
    } catch (e: IOException) {
        Timber.w(e, "deletePhoto: network error")
        AppResult.Failure(AppError.Network)
    } catch (e: Throwable) {
        Timber.e(e, "deletePhoto: unexpected error")
        AppResult.Failure(AppError.Unknown(e))
    }

    private fun VehicleResponseDto.toDomain(): Vehicle {
        val resolvedEnergyType = EnergyType.entries
            .firstOrNull { it.apiValue == energyType } ?: EnergyType.Combustion

        return Vehicle(
            id = id,
            brand = brand,
            model = model,
            manufactureYear = manufactureYear,
            modelYear = modelYear,
            licensePlate = licensePlate,
            color = color,
            type = VehicleType.entries.firstOrNull { it.apiValue == type } ?: VehicleType.Car,
            energyType = resolvedEnergyType,
            fuelType = fuelSubType?.let { v -> FuelType.entries.firstOrNull { it.apiValue == v } },
            odometerKm = currentKm,
            tankCapacityL = capacity.takeIf {
                resolvedEnergyType == EnergyType.Combustion || resolvedEnergyType == EnergyType.Hybrid
            },
            batteryCapacityKwh = capacity.takeIf {
                resolvedEnergyType == EnergyType.Electric || resolvedEnergyType == EnergyType.Hybrid
            },
            isActive = isActive,
            photoUrl = photo?.let { BuildConfig.API_BASE_URL.trimEnd('/') + it },
        )
    }
}