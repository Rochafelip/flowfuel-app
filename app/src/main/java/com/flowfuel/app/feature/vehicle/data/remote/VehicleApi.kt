package com.flowfuel.app.feature.vehicle.data.remote

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Part
import retrofit2.http.Query

// ─── Paginação ────────────────────────────────────────────────────────────────

/** Envelope de resposta paginada retornado pelo backend. */
@Serializable
data class PagedResponseDto<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Int = 0,
    val totalPages: Int = 0,
)

// ─── DTOs de veículo ──────────────────────────────────────────────────────────

@Serializable
data class CreateVehicleRequestDto(
    val brand: String,
    val model: String,
    val manufactureYear: Int,
    val modelYear: Int,
    val licensePlate: String,
    val color: String? = null,
    val type: String,
    val energyType: String,
    val fuelSubType: String? = null,
    val currentKm: Int,
    val capacity: Double? = null,
)

@Serializable
data class UpdateVehicleRequestDto(
    val brand: String? = null,
    val model: String? = null,
    val manufactureYear: Int? = null,
    val modelYear: Int? = null,
    val licensePlate: String? = null,
    val color: String? = null,
    val type: String? = null,
    val energyType: String? = null,
    val fuelSubType: String? = null,
    val currentKm: Int? = null,
    val capacity: Double? = null,
)

@Serializable
data class VehicleResponseDto(
    val id: Int,
    val brand: String,
    val model: String,
    val manufactureYear: Int? = null,
    val modelYear: Int? = null,
    val licensePlate: String? = null,
    val color: String? = null,
    val type: String,
    val energyType: String,
    val fuelSubType: String? = null,
    val currentKm: Int,
    val capacity: Double? = null,
    val photo: String? = null,
    val isActive: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class VehiclePhotoResponseDto(
    val internalUrl: String? = null,
)

// ─── Interface Retrofit ───────────────────────────────────────────────────────

interface VehicleApi {
    @POST("vehicles")
    suspend fun createVehicle(@Body body: CreateVehicleRequestDto): VehicleResponseDto

    @GET("vehicles")
    suspend fun getVehicles(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PagedResponseDto<VehicleResponseDto>

    /** Retorna o veículo marcado como ativo para o usuário autenticado. */
    @GET("vehicles/active")
    suspend fun getActiveVehicle(): VehicleResponseDto

    /**
     * Marca o veículo como ativo (último usado).
     * Usa [ResponseBody]? para funcionar tanto com 200+JSON quanto com 204 sem corpo.
     */
    @PUT("vehicles/{id}/active")
    suspend fun setActiveVehicle(@Path("id") id: Int): ResponseBody?

    /**
     * Remove o veículo permanentemente.
     * Usa [ResponseBody]? para funcionar tanto com 200+JSON quanto com 204 sem corpo.
     */
    @DELETE("vehicles/{id}")
    suspend fun deleteVehicle(@Path("id") id: Int): ResponseBody?

    @GET("vehicles/{id}")
    suspend fun getVehicleById(@Path("id") id: Int): VehicleResponseDto

    @PUT("vehicles/{id}")
    suspend fun updateVehicle(
        @Path("id") id: Int,
        @Body body: UpdateVehicleRequestDto,
    ): VehicleResponseDto

    /**
     * Atualiza apenas o odômetro do veículo.
     * O backend espera o novo valor como query param `currentKm` — sem corpo
     * (ver openapi.yaml: PUT /vehicles/{id}/odometer, nome de parâmetro
     * enganoso, mas é o único parâmetro do endpoint além do id).
     */
    @PUT("vehicles/{id}/odometer")
    suspend fun updateOdometer(
        @Path("id") id: Int,
        @Query("currentKm") newKm: Int,
    ): VehicleResponseDto

    /**
     * Envia a foto do veículo (multipart). Chamado logo após a criação do
     * veículo (ver [createVehicle]) — o endpoint espera o veículo já existir.
     * Resposta traz só `internalUrl` (sem `signedUrl`), conforme confirmado
     * pela implementação real do backend (ver docs/upload-foto-veiculo.md).
     */
    @Multipart
    @POST("vehicles/{id}/photo")
    suspend fun uploadVehiclePhoto(
        @Path("id") id: Int,
        @Part file: MultipartBody.Part,
    ): VehiclePhotoResponseDto
}