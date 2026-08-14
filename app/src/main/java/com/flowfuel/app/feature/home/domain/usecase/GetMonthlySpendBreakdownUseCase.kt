package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.home.domain.model.SpendBreakdown
import com.flowfuel.app.feature.home.domain.model.buildSpendBreakdown
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Composição de gastos do mês atual (até hoje) por categoria — mesma
 * janela de datas de GetFinancialSummaryUseCase.currentMonthTotal, mas
 * com o detalhamento por categoria em vez de só a soma. Duplica a
 * paginação por data que GetFinancialSummaryUseCase já faz — decisão
 * consciente, mesmo padrão já adotado em GetVehicleEventsUseCase (ver
 * docs/superpowers/specs/2026-08-14-spend-breakdown-donut-design.md):
 * evitar mexer em código já em produção só por reuso.
 */
class GetMonthlySpendBreakdownUseCase @Inject constructor(
    private val getRefuelHistory: GetRefuelHistoryUseCase,
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<SpendBreakdown> {
        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1)

        val refuelsResult = fetchAllRefuels(vehicleId, monthStart, today)
        if (refuelsResult is AppResult.Failure) return refuelsResult
        val refuels = (refuelsResult as AppResult.Success).value

        val eventsResult = fetchAllEvents(vehicleId, monthStart, today)
        if (eventsResult is AppResult.Failure) return eventsResult
        val events = (eventsResult as AppResult.Success).value

        val monthFuelSpent = refuels.sumOf { it.totalPrice }
        return AppResult.Success(buildSpendBreakdown(monthFuelSpent, events))
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
