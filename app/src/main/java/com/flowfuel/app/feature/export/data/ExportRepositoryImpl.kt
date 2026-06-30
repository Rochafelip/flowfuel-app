package com.flowfuel.app.feature.export.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.export.domain.ExportFormat
import com.flowfuel.app.feature.export.domain.ExportRepository
import com.flowfuel.app.feature.history.domain.HistoryRepository
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportRepositoryImpl @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val eventRepository: VehicleEventRepository,
    @ApplicationContext private val context: Context,
) : ExportRepository {

    override suspend fun exportRefuels(
        vehicleId: Int,
        format: ExportFormat,
        startDate: String?,
        endDate: String?,
    ): AppResult<Uri> {
        val fetchResult = fetchAllRefuels(vehicleId, startDate, endDate)
        if (fetchResult is AppResult.Failure) return fetchResult
        return runCatching {
            val uri = saveFile(
                buildRefuelsCsv((fetchResult as AppResult.Success).value).toByteArray(Charsets.UTF_8),
                "flowfuel-abastecimentos.csv",
            )
            AppResult.Success(uri)
        }.getOrElse { e ->
            Timber.e(e, "export save failure")
            AppResult.Failure(AppError.Unknown(e))
        }
    }

    override suspend fun exportEvents(
        vehicleId: Int,
        format: ExportFormat,
        type: String?,
        startDate: String?,
        endDate: String?,
    ): AppResult<Uri> {
        val category = type?.let { t -> EventCategory.entries.find { it.apiValue == t } }
        val fetchResult = fetchAllEvents(vehicleId, category, startDate, endDate)
        if (fetchResult is AppResult.Failure) return fetchResult
        return runCatching {
            val uri = saveFile(
                buildEventsCsv((fetchResult as AppResult.Success).value).toByteArray(Charsets.UTF_8),
                "flowfuel-eventos.csv",
            )
            AppResult.Success(uri)
        }.getOrElse { e ->
            Timber.e(e, "export save failure")
            AppResult.Failure(AppError.Unknown(e))
        }
    }

    private suspend fun fetchAllRefuels(
        vehicleId: Int,
        startDate: String?,
        endDate: String?,
    ): AppResult<List<RefuelItem>> {
        val start = startDate?.let { LocalDate.parse(it) }
        val end = endDate?.let { LocalDate.parse(it) }
        val items = mutableListOf<RefuelItem>()
        var page = 0
        while (true) {
            when (val r = historyRepository.getRefuelHistory(vehicleId, page, 100, start, end)) {
                is AppResult.Success -> {
                    items.addAll(r.value.items)
                    if (!r.value.hasMore) return AppResult.Success(items)
                    page++
                }
                is AppResult.Failure -> return r
            }
        }
    }

    private suspend fun fetchAllEvents(
        vehicleId: Int,
        category: EventCategory?,
        startDate: String?,
        endDate: String?,
    ): AppResult<List<VehicleEvent>> {
        val items = mutableListOf<VehicleEvent>()
        var page = 0
        while (true) {
            when (val r = eventRepository.getEventsByVehicle(vehicleId, page, category, startDate, endDate)) {
                is AppResult.Success -> {
                    items.addAll(r.value.items)
                    if (!r.value.hasMore) return AppResult.Success(items)
                    page++
                }
                is AppResult.Failure -> return r
            }
        }
    }

    private fun buildRefuelsCsv(items: List<RefuelItem>): String = buildString {
        appendLine("Data;Tipo;Quantidade;Preço/unidade;Total (R\$);Tanque cheio;Odômetro (km);Km percorridos;Consumo")
        items.forEach { item ->
            appendLine(csvRow(
                item.date,
                item.refuelType ?: "FUEL",
                item.energyAmount.csvDecimal(),
                item.pricePerUnit.csvDecimal(),
                item.totalPrice.csvDecimal(),
                if (item.fullTank) "Sim" else "Não",
                item.odometer?.csvDecimal() ?: "",
                item.trip?.csvDecimal() ?: "",
                item.consumption?.csvDecimal() ?: "",
            ))
        }
    }

    private fun buildEventsCsv(items: List<VehicleEvent>): String = buildString {
        appendLine("Data;Categoria;Título;Descrição;Valor (R\$);Odômetro (km);Notas")
        items.forEach { item ->
            appendLine(csvRow(
                item.eventDate,
                item.category.label,
                item.title.csvEscape(),
                item.description?.csvEscape() ?: "",
                item.amount?.csvDecimal() ?: "",
                item.odometerKm?.toString() ?: "",
                item.notes?.csvEscape() ?: "",
            ))
        }
    }

    private fun saveFile(bytes: ByteArray, filename: String): Uri {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val file = File(dir, filename)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun csvRow(vararg fields: String) = fields.joinToString(";")

    private fun Double.csvDecimal() = "%.2f".format(this).replace('.', ',')

    private fun String.csvEscape() =
        if (contains(';') || contains('"') || contains('\n')) "\"${replace("\"", "\"\"")}\""
        else this
}
