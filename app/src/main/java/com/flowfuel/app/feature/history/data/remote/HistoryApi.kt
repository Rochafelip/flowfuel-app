package com.flowfuel.app.feature.history.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// ─── DTO de item de abastecimento ─────────────────────────────────────────────

@Serializable
data class RefuelItemDto(
    val id: Int,
    val vehicleId: Int? = null,
    // ── Campos com nomes reais do backend ────────────────────────────────────
    /**
     * Data do abastecimento.
     * O backend pode retornar `refuelDate` ou `createdAt` dependendo da versão.
     * Usamos [refuelDate] como primário e [createdAt] como alias de fallback.
     */
    val refuelDate: String? = null,
    val createdAt: String? = null,
    /** Leitura absoluta do odômetro em km. */
    val odometer: Double? = null,
    /** Km percorridos desde o último abastecimento — usado para calcular consumo. */
    val kmSinceLastRefuel: Double? = null,
    /** Quantidade de energia: litros (combustível) ou kWh (elétrico). */
    val energyAmount: Double? = null,
    val pricePerUnit: Double? = null,
    /** Valor total pago (energyAmount × pricePerUnit). */
    val totalAmount: Double? = null,
    val fullTank: Boolean = false,
    /** "FUEL" | "ELECTRIC" | null */
    val refuelType: String? = null,
    /** Consumo calculado pelo backend (km/L ou km/kWh); null se dados insuficientes. */
    val consumption: Double? = null,
) {
    /** Data efetiva: prefere refuelDate, cai em createdAt se necessário. */
    val effectiveDate: String? get() = refuelDate ?: createdAt
}

// ─── Envelope paginado (mesmo padrão de PagedResponseDto do feature vehicle) ──

/**
 * O backend (Spring Boot) retorna os itens dentro de um envelope paginado.
 * Todos os campos têm default para absorver variações de resposta do servidor.
 *
 * Spring Boot usa `number` para o número da página atual (não `page`),
 * mas como só usamos `content`, isso não afeta o funcionamento.
 */
@Serializable
data class RefuelHistoryPageDto(
    val content: List<RefuelItemDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Int = 0,
    val totalPages: Int = 0,
)

// ─── DTO de atualização de abastecimento ──────────────────────────────────────

@Serializable
data class UpdateRefuelRequestDto(
    val vehicleId: Int,
    val odometer: Double,
    val energyAmount: Double,
    val pricePerUnit: Double,
    val fullTank: Boolean,
    val refuelType: String?,
)

// ─── Interface Retrofit ───────────────────────────────────────────────────────

interface HistoryApi {
    @GET("refuels/vehicle/{vehicleId}")
    suspend fun getRefuelHistory(
        @Path("vehicleId") vehicleId: Int,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
    ): RefuelHistoryPageDto

    @GET("refuels/{id}")
    suspend fun getRefuelById(@Path("id") id: Int): RefuelItemDto

    @PUT("refuels/{id}")
    suspend fun updateRefuel(
        @Path("id") id: Int,
        @Body body: UpdateRefuelRequestDto,
    ): RefuelItemDto

    @DELETE("refuels/{id}")
    suspend fun deleteRefuel(@Path("id") id: Int)
}