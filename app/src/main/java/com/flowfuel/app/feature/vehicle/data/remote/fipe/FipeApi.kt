package com.flowfuel.app.feature.vehicle.data.remote.fipe

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

@Serializable
data class FipeBrandDto(
    val codigo: String,
    val nome: String,
)

@Serializable
data class FipeModelDto(
    val codigo: Int,
    val nome: String,
)

@Serializable
data class FipeModelsResponseDto(
    val modelos: List<FipeModelDto> = emptyList(),
)

@Serializable
data class FipeYearDto(
    val codigo: String,
    val nome: String,
)

/**
 * API pública e gratuita da tabela FIPE (sem autenticação, sem chave).
 * baseUrl configurada em NetworkModule: https://parallelum.com.br/fipe/api/v1/
 */
interface FipeApi {
    @GET("{tipo}/marcas")
    suspend fun getBrands(@Path("tipo") tipo: String): List<FipeBrandDto>

    @GET("{tipo}/marcas/{marcaCodigo}/modelos")
    suspend fun getModels(
        @Path("tipo") tipo: String,
        @Path("marcaCodigo") marcaCodigo: String,
    ): FipeModelsResponseDto

    @GET("{tipo}/marcas/{marcaCodigo}/modelos/{modeloCodigo}/anos")
    suspend fun getYears(
        @Path("tipo") tipo: String,
        @Path("marcaCodigo") marcaCodigo: String,
        @Path("modeloCodigo") modeloCodigo: String,
    ): List<FipeYearDto>
}
