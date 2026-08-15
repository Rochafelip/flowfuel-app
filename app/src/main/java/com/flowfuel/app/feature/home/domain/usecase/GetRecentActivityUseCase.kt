package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import javax.inject.Inject

private const val RECENT_ACTIVITY_LIMIT = 3

/**
 * Combina abastecimentos e eventos num único timeline ordenado por data,
 * mesmo padrão de [com.flowfuel.app.feature.vehicleevent.presentation.list.VehicleEventsViewModel.buildTimeline],
 * truncado para os itens mais recentes.
 *
 * O abastecimento mais recente é excluído da mistura: ele já aparece em
 * [com.flowfuel.app.feature.home.presentation.components.LastRefuelDetailCard]
 * (spec seção 06/07), então buscamos um a mais e descartamos o primeiro.
 */
class GetRecentActivityUseCase @Inject constructor(
    private val getRefuelHistory: GetRefuelHistoryUseCase,
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<List<VehicleTimelineItem>> {
        val refuelsResult = getRefuelHistory(vehicleId, 0, RECENT_ACTIVITY_LIMIT + 1)
        if (refuelsResult is AppResult.Failure) return refuelsResult
        val refuels = (refuelsResult as AppResult.Success).value.items.drop(1)

        val eventsResult = getVehicleEventsPage(vehicleId, 0, null)
        if (eventsResult is AppResult.Failure) return eventsResult
        val events = (eventsResult as AppResult.Success).value.items

        val timeline = (refuels.map { VehicleTimelineItem.RefuelEntry(it) } +
            events.map { VehicleTimelineItem.EventEntry(it) })
            .sortedByDescending { it.sortDate }
            .take(RECENT_ACTIVITY_LIMIT)

        return AppResult.Success(timeline)
    }
}
