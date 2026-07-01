package com.flowfuel.app.feature.export.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.export.data.pdf.PdfReportWriter
import com.flowfuel.app.feature.export.domain.ExportFormat
import com.flowfuel.app.feature.export.domain.ExportRepository
import com.flowfuel.app.feature.history.domain.HistoryRepository
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val REFUELS_TABLE_HEADER = listOf(
    "Data", "Tipo", "Quantidade", "Preço/unidade", "Total (R$)",
    "Tanque cheio", "Odômetro (km)", "Km percorridos", "Consumo",
)

private val EVENTS_TABLE_HEADER = listOf(
    "Data", "Categoria", "Título", "Descrição", "Valor (R$)", "Odômetro (km)", "Notas",
)

@Singleton
class ExportRepositoryImpl @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val eventRepository: VehicleEventRepository,
    private val vehicleRepository: VehicleRepository,
    private val pdfReportWriter: PdfReportWriter,
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
        val items = (fetchResult as AppResult.Success).value

        return when (format) {
            ExportFormat.CSV -> runCatching {
                val bytes = buildCsvBytes(REFUELS_TABLE_HEADER, items.map(::refuelsTableRow))
                AppResult.Success(saveFile(bytes, "flowfuel-abastecimentos.csv"))
            }.getOrElse { e -> Timber.e(e, "export save failure"); AppResult.Failure(AppError.Unknown(e)) }

            ExportFormat.PDF -> {
                val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
                if (vehicleResult is AppResult.Failure) return vehicleResult
                val vehicle = (vehicleResult as AppResult.Success).value
                runCatching {
                    val bytes = pdfReportWriter.writeRefuelsReport(
                        vehicleLabel = vehicleLabel(vehicle),
                        periodLabel = periodLabel(startDate, endDate),
                        summary = buildRefuelsSummary(items),
                        energyUnit = energyUnit(vehicle),
                        consumptionUnit = consumptionUnit(vehicle),
                        tableHeader = REFUELS_TABLE_HEADER,
                        tableRows = items.map(::refuelsTableRowPdf),
                    )
                    AppResult.Success(saveFile(bytes, "flowfuel-abastecimentos.pdf"))
                }.getOrElse { e -> Timber.e(e, "export save failure"); AppResult.Failure(AppError.Unknown(e)) }
            }
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
        val items = (fetchResult as AppResult.Success).value

        return when (format) {
            ExportFormat.CSV -> runCatching {
                val bytes = buildCsvBytes(EVENTS_TABLE_HEADER, items.map(::eventsTableRow))
                AppResult.Success(saveFile(bytes, "flowfuel-eventos.csv"))
            }.getOrElse { e -> Timber.e(e, "export save failure"); AppResult.Failure(AppError.Unknown(e)) }

            ExportFormat.PDF -> {
                val vehicleResult = vehicleRepository.getVehicleById(vehicleId)
                if (vehicleResult is AppResult.Failure) return vehicleResult
                val vehicle = (vehicleResult as AppResult.Success).value
                runCatching {
                    val bytes = pdfReportWriter.writeEventsReport(
                        vehicleLabel = vehicleLabel(vehicle),
                        periodLabel = periodLabel(startDate, endDate),
                        summary = buildEventsSummary(items),
                        tableHeader = EVENTS_TABLE_HEADER,
                        tableRows = items.map(::eventsTableRowPdf),
                    )
                    AppResult.Success(saveFile(bytes, "flowfuel-eventos.pdf"))
                }.getOrElse { e -> Timber.e(e, "export save failure"); AppResult.Failure(AppError.Unknown(e)) }
            }
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

    private fun refuelsTableRow(item: RefuelItem): List<String> = listOf(
        item.date,
        item.refuelType ?: "FUEL",
        item.energyAmount.csvDecimal(),
        item.pricePerUnit.csvDecimal(),
        item.totalPrice.csvDecimal(),
        if (item.fullTank) "Sim" else "Não",
        item.odometer?.csvDecimal() ?: "",
        item.trip?.csvDecimal() ?: "",
        item.consumption?.csvDecimal() ?: "",
    )

    private fun eventsTableRow(item: VehicleEvent): List<String> = listOf(
        item.eventDate,
        item.category.label,
        item.title,
        item.description ?: "",
        item.amount?.csvDecimal() ?: "",
        item.odometerKm?.toString() ?: "",
        item.notes ?: "",
    )

    private fun refuelsTableRowPdf(item: RefuelItem): List<String> =
        refuelsTableRow(item).toMutableList().also { it[0] = pdfDate(item.date) }

    private fun eventsTableRowPdf(item: VehicleEvent): List<String> =
        eventsTableRow(item).toMutableList().also { it[0] = pdfDate(item.eventDate) }

    private fun saveFile(bytes: ByteArray, filename: String): Uri {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val file = File(dir, filename)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun Double.csvDecimal() = "%.2f".format(this).replace('.', ',')
}
