package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.history.domain.usecase.GetRefuelHistoryUseCase
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleTimelineItem
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import javax.inject.Inject

private const val RECENT_ACTIVITY_LIMIT = 4

/**
 * Combina abastecimentos e eventos num único timeline ordenado por data,
 * mesmo padrão de [com.flowfuel.app.feature.vehicleevent.presentation.list.VehicleEventsViewModel.buildTimeline],
 * truncado para os itens mais recentes.
 */
class GetRecentActivityUseCase @Inject constructor(
    private val getRefuelHistory: GetRefuelHistoryUseCase,
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase,
) {
    suspend operator fun invoke(vehicleId: Int): AppResult<List<VehicleTimelineItem>> {
        val refuelsResult = getRefuelHistory(vehicleId, 0, RECENT_ACTIVITY_LIMIT)
        if (refuelsResult is AppResult.Failure) return refuelsResult
        val refuels = (refuelsResult as AppResult.Success).value.items

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
