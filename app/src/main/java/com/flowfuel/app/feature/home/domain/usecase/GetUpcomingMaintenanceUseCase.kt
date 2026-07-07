package com.flowfuel.app.feature.home.domain.usecase

import com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStore
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceItem
import com.flowfuel.app.feature.home.domain.model.UpcomingMaintenanceType
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.usecase.GetVehicleEventsPageUseCase
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

private const val OIL_CHANGE_INTERVAL_KM = 10_000
private const val TIRE_ROTATION_INTERVAL_KM = 10_000

/**
 * Lembretes de manutenção (troca de óleo, rodízio de pneus, licenciamento) exibidos
 * na seção "Próximos eventos" da Home. Óleo/pneus: último [com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent]
 * da categoria correspondente + um intervalo fixo em km; sem histórico, usa uma âncora
 * local persistida ([VehicleMaintenancePrefsStore]) gravada uma única vez, para não
 * recalcular a partir do odômetro atual (que muda) a cada chamada. Licenciamento usa
 * uma data definida manualmente pelo usuário, também local.
 */
class GetUpcomingMaintenanceUseCase @Inject constructor(
    private val getVehicleEventsPage: GetVehicleEventsPageUseCase,
    private val prefsStore: VehicleMaintenancePrefsStore,
) {
    suspend operator fun invoke(vehicleId: Int, currentKm: Int): AppResult<List<UpcomingMaintenanceItem>> {
        val oilResult = kmBasedItem(
            vehicleId, currentKm, EventCategory.OIL_CHANGE,
            UpcomingMaintenanceType.OIL_CHANGE, OIL_CHANGE_INTERVAL_KM,
        )
        if (oilResult is AppResult.Failure) return oilResult

        val tiresResult = kmBasedItem(
            vehicleId, currentKm, EventCategory.TIRES,
            UpcomingMaintenanceType.TIRE_ROTATION, TIRE_ROTATION_INTERVAL_KM,
        )
        if (tiresResult is AppResult.Failure) return tiresResult

        val licensing = licensingItem(vehicleId)

        return AppResult.Success(
            listOf(
                (oilResult as AppResult.Success).value,
                (tiresResult as AppResult.Success).value,
                licensing,
            ),
        )
    }

    private suspend fun kmBasedItem(
        vehicleId: Int,
        currentKm: Int,
        category: EventCategory,
        type: UpcomingMaintenanceType,
        intervalKm: Int,
    ): AppResult<UpcomingMaintenanceItem> {
        val eventsResult = getVehicleEventsPage(vehicleId, 0, category)
        if (eventsResult is AppResult.Failure) return eventsResult
        val lastEvent = (eventsResult as AppResult.Success).value.items
            .filter { it.odometerKm != null }
            .maxByOrNull { it.eventDate }

        val dueKm = if (lastEvent != null) {
            lastEvent.odometerKm!! + intervalKm
        } else {
            val anchor = prefsStore.anchorKmFlow(vehicleId, category).first()
            if (anchor != null) {
                anchor + intervalKm
            } else {
                prefsStore.saveAnchorKm(vehicleId, category, currentKm)
                currentKm + intervalKm
            }
        }

        val remainingKm = dueKm - currentKm
        return AppResult.Success(
            UpcomingMaintenanceItem(
                type = type,
                remainingKm = remainingKm,
                isOverdue = remainingKm < 0,
            ),
        )
    }

    private suspend fun licensingItem(vehicleId: Int): UpcomingMaintenanceItem {
        val dueDateIso = prefsStore.licensingDueDateFlow(vehicleId).first()
            ?: return UpcomingMaintenanceItem(type = UpcomingMaintenanceType.LICENSING, needsSetup = true)

        val remainingDays = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(dueDateIso)).toInt()
        return UpcomingMaintenanceItem(
            type = UpcomingMaintenanceType.LICENSING,
            remainingDays = remainingDays,
            isOverdue = remainingDays < 0,
        )
    }
}
