package com.flowfuel.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vehicleMaintenanceDataStore by preferencesDataStore(name = "flowfuel_vehicle_maintenance")

/**
 * Lembretes de manutenção (ver GetUpcomingMaintenanceUseCase) dependem de dois dados
 * que não existem no backend — este repositório contém só o cliente Android, o
 * backend é um serviço separado — então ficam só neste dispositivo, sem sincronizar
 * entre aparelhos/reinstalações:
 *
 * - Data de licenciamento definida manualmente pelo usuário, por veículo.
 * - Âncora de km usada só enquanto não existe nenhum VehicleEvent real da categoria
 *   (óleo/pneus): o odômetro do veículo no momento em que o lembrete foi calculado
 *   por falta de histórico, gravada uma única vez para a contagem não "resetar" a
 *   cada abastecimento.
 */
@Singleton
class VehicleMaintenancePrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun licensingDueDateFlow(vehicleId: Int): Flow<String?> =
        context.vehicleMaintenanceDataStore.data.map { it[licensingDueDateKey(vehicleId)] }

    suspend fun saveLicensingDueDate(vehicleId: Int, isoDate: String) {
        context.vehicleMaintenanceDataStore.edit { it[licensingDueDateKey(vehicleId)] = isoDate }
    }

    fun anchorKmFlow(vehicleId: Int, category: EventCategory): Flow<Int?> =
        context.vehicleMaintenanceDataStore.data.map { it[anchorKmKey(vehicleId, category)] }

    suspend fun saveAnchorKm(vehicleId: Int, category: EventCategory, km: Int) {
        context.vehicleMaintenanceDataStore.edit { it[anchorKmKey(vehicleId, category)] = km }
    }

    private fun licensingDueDateKey(vehicleId: Int) = stringPreferencesKey("licensing_due_$vehicleId")

    private fun anchorKmKey(vehicleId: Int, category: EventCategory) =
        intPreferencesKey("anchor_${category.name}_$vehicleId")
}
