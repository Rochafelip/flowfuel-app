package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.home.domain.model.FinancialSummary
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Resumo financeiro do mês atual (até hoje) vs. mês anterior completo,
 * somando abastecimentos ([GetRefuelHistoryUseCase]) e eventos
 * ([GetVehicleEventsPageUseCase]) — as duas fontes que compõem "gasto
 * total" no app. Ambas já suportam filtro de data server-side, então cada
 * mês é buscado com uma janela de datas, sem paginar o histórico inteiro.
 */
class GetFinancialSummaryUseCase @Inject constructor(
    private val getRefuelHistory: GetRefuelHistoryUseCase,
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<FinancialSummary> {
        val today = LocalDate.now()
        val currentStart = today.withDayOfMonth(1)
        val previousMonth = today.minusMonths(1)
        val previousStart = previousMonth.withDayOfMonth(1)
        val previousEnd = previousMonth.withDayOfMonth(previousMonth.lengthOfMonth())

        val currentRefuelsResult = fetchAllRefuels(vehicleId, currentStart, today)
        if (currentRefuelsResult is AppResult.Failure) return currentRefuelsResult
        val currentRefuels = (currentRefuelsResult as AppResult.Success).value

        val currentEventsResult = fetchAllEvents(vehicleId, currentStart, today)
        if (currentEventsResult is AppResult.Failure) return currentEventsResult
        val currentEvents = (currentEventsResult as AppResult.Success).value

        val previousRefuelsResult = fetchAllRefuels(vehicleId, previousStart, previousEnd)
        if (previousRefuelsResult is AppResult.Failure) return previousRefuelsResult
        val previousRefuels = (previousRefuelsResult as AppResult.Success).value

        val previousEventsResult = fetchAllEvents(vehicleId, previousStart, previousEnd)
        if (previousEventsResult is AppResult.Failure) return previousEventsResult
        val previousEvents = (previousEventsResult as AppResult.Success).value

        val currentRefuelsTotal = currentRefuels.sumOf { it.totalPrice }
        val currentEventsTotal = currentEvents.sumOf { it.amount ?: 0.0 }
        val previousTotal = previousRefuels.sumOf { it.totalPrice } + previousEvents.sumOf { it.amount ?: 0.0 }

        val currentEnergyTotal = currentRefuels.sumOf { it.energyAmount }
        val averagePricePerUnit = if (currentEnergyTotal > 0.0) currentRefuelsTotal / currentEnergyTotal else null

        return AppResult.Success(
            FinancialSummary(
                currentMonthTotal = currentRefuelsTotal + currentEventsTotal,
                previousMonthTotal = previousTotal,
                averagePricePerUnit = averagePricePerUnit,
            )
        )
    }

    private suspend fun fetchAllRefuels(vehicleId: Int, from: LocalDate, to: LocalDate): AppResult<List<RefuelItem>> {
        val items = mutableListOf<RefuelItem>()
        var page = 0
        while (true) {
            when (val result = getRefuelHistory(vehicleId, page, 50, from, to)) {
                is AppResult.Success -> {
                    items.addAll(result.value.items)
                    if (!result.value.hasMore) return AppResult.Success(items)
                    page++
                }
                is AppResult.Failure -> return result
            }
        }
    }

    private suspend fun fetchAllEvents(vehicleId: Int, from: LocalDate, to: LocalDate): AppResult<List<VehicleEvent>> {
        val items = mutableListOf<VehicleEvent>()
        var page = 0
        val fromStr = from.format(isoFmt)
        val toStr = to.format(isoFmt)
        while (true) {
            when (val result = getVehicleEventsPage(vehicleId, page, null, fromStr, toStr)) {
                is AppResult.Success -> {
                    items.addAll(result.value.items)
                    if (!result.value.hasMore) return AppResult.Success(items)
                    page++
                }
                is AppResult.Failure -> return result
            }
        }
    }
}
