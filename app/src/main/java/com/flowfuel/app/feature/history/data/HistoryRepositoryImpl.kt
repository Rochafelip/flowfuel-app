package com.flowfuel.app.feature.history.data

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.domain.map
import com.flowfuel.app.core.network.apiCall
import com.flowfuel.app.feature.history.data.remote.HistoryApi
import com.flowfuel.app.feature.history.data.remote.RefuelItemDto
import com.flowfuel.app.feature.history.data.remote.UpdateRefuelRequestDto
import com.flowfuel.app.feature.history.domain.HistoryRepository
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.model.RefuelPage
import com.flowfuel.app.feature.history.domain.model.UpdateRefuelRequest
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val api: HistoryApi,
) : HistoryRepository {

    override suspend fun getRefuelHistory(
        vehicleId: Int,
        page: Int,
        size: Int,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): AppResult<RefuelPage> {
        Timber.d("HistoryRepo › GET refuels/vehicle/$vehicleId page=$page size=$size startDate=$startDate endDate=$endDate")

        return apiCall {
            api.getRefuelHistory(vehicleId, page, size, startDate?.toString(), endDate?.toString())
        }.map { pageDto ->
            Timber.d(
                "HistoryRepo › resposta recebida — " +
                "content.size=${pageDto.content.size}, " +
                "totalElements=${pageDto.totalElements}, " +
                "totalPages=${pageDto.totalPages}"
            )

            val items = pageDto.content
                .also { dtos ->
                    dtos.forEachIndexed { i, dto ->
                        Timber.v(
                            "HistoryRepo › item[$i] id=${dto.id} " +
                            "effectiveDate='${dto.effectiveDate}' " +
                            "energyAmount=${dto.energyAmount} " +
                            "pricePerUnit=${dto.pricePerUnit} " +
                            "totalAmount=${dto.totalAmount} " +
                            "refuelType=${dto.refuelType} " +
                            "kmSinceLastRefuel=${dto.kmSinceLastRefuel} " +
                            "consumption=${dto.consumption}"
                        )
                    }
                }
                .map { dto ->
                    val energy = dto.energyAmount ?: 0.0
                    val price  = dto.pricePerUnit  ?: 0.0
                    val tripKm = dto.kmSinceLastRefuel
                    val consumption = dto.consumption
                        ?: if (energy > 0.0 && tripKm != null && tripKm > 0.0) tripKm / energy else null

                    RefuelItem(
                        id           = dto.id,
                        date         = dto.effectiveDate ?: "",
                        energyAmount = energy,
                        pricePerUnit = price,
                        totalPrice   = dto.totalAmount ?: (energy * price),
                        fullTank     = dto.fullTank,
                        refuelType   = dto.refuelType,
                        odometer     = dto.odometer,
                        trip         = tripKm,
                        consumption  = consumption,
                    )
                }
                .sortedByDescending { it.date }

            RefuelPage(
                items = items,
                hasMore = page + 1 < pageDto.totalPages,
                currentPage = page,
                totalElements = pageDto.totalElements,
            )
        }
    }

    override suspend fun getRefuelDetails(id: Int): AppResult<RefuelItem> {
        Timber.d("HistoryRepo › GET refuels/$id")
        return apiCall { api.getRefuelById(id) }.map { dto -> dto.toDomain() }
    }

    override suspend fun updateRefuel(id: Int, request: UpdateRefuelRequest): AppResult<RefuelItem> {
        Timber.d("HistoryRepo › PUT refuels/$id")
        val liters = request.liters
        val pricePerUnit = if (liters > 0.0) request.totalPrice / liters else 0.0
        return apiCall {
            api.updateRefuel(
                id,
                UpdateRefuelRequestDto(
                    vehicleId    = request.vehicleId,
                    odometer     = request.odometer,
                    energyAmount = liters,
                    pricePerUnit = pricePerUnit,
                    fullTank     = request.fullTank,
                    refuelType   = request.refuelType,
                ),
            )
        }.map { dto -> dto.toDomain() }
    }

    override suspend fun deleteRefuel(id: Int): AppResult<Unit> {
        Timber.d("HistoryRepo › DELETE refuels/$id")
        return apiCall { api.deleteRefuel(id) }
    }

    private fun RefuelItemDto.toDomain(): RefuelItem {
        val energy  = energyAmount ?: 0.0
        val price   = pricePerUnit  ?: 0.0
        val tripKm  = kmSinceLastRefuel
        val consumption = consumption
            ?: if (energy > 0.0 && tripKm != null && tripKm > 0.0) tripKm / energy else null
        return RefuelItem(
            id           = id,
            date         = effectiveDate ?: "",
            energyAmount = energy,
            pricePerUnit = price,
            totalPrice   = totalAmount ?: (energy * price),
            fullTank     = fullTank,
            refuelType   = refuelType,
            odometer     = odometer,
            trip         = tripKm,
            consumption  = consumption,
        )
    }
}
