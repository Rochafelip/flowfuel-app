package com.flowfuel.app.feature.export.data

import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.export.data.pdf.PdfReportWriter
import com.flowfuel.app.feature.export.domain.ExportFormat
import com.flowfuel.app.feature.history.domain.HistoryRepository
import com.flowfuel.app.feature.history.domain.model.RefuelItem
import com.flowfuel.app.feature.history.domain.model.RefuelPage
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import com.flowfuel.app.feature.vehicle.domain.model.EnergyType
import com.flowfuel.app.feature.vehicle.domain.model.FuelType
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// androidx.core.content.FileProvider.SimplePathStrategy.belongsToRoot usa um
// separador "/" fixo; em JVM host Windows, File.getCanonicalPath() retorna
// paths com "\", então a checagem falha sempre sob Robolectric nesta
// plataforma (funciona normalmente em device real e em CI Linux). Guard
// apenas para os testes que chegam em saveFile()/FileProvider.
private val isWindows = System.getProperty("os.name")?.contains("Windows", ignoreCase = true) == true

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExportRepositoryImplTest {

    private val historyRepository: HistoryRepository = mockk()
    private val eventRepository: VehicleEventRepository = mockk()
    private val vehicleRepository: VehicleRepository = mockk()
    private val pdfReportWriter: PdfReportWriter = mockk()
    private lateinit var repository: ExportRepositoryImpl

    @Before
    fun setUp() {
        repository = ExportRepositoryImpl(
            historyRepository = historyRepository,
            eventRepository = eventRepository,
            vehicleRepository = vehicleRepository,
            pdfReportWriter = pdfReportWriter,
            context = ApplicationProvider.getApplicationContext(),
        )
    }

    private fun vehicle() = Vehicle(
        id = 1, brand = "Toyota", model = "Corolla", manufactureYear = 2022, modelYear = 2022,
        licensePlate = "ABC1D23", color = "Prata", type = VehicleType.Car, energyType = EnergyType.Combustion,
        fuelType = FuelType.Gasoline, odometerKm = 10000, tankCapacityL = 50.0, batteryCapacityKwh = null,
        isActive = true,
    )

    private fun refuelItem() = RefuelItem(
        id = 1, date = "2026-01-05", energyAmount = 40.0, pricePerUnit = 5.5, totalPrice = 220.0,
        fullTank = true, refuelType = "FUEL", odometer = 10500.0, trip = 400.0, consumption = 10.0,
    )

    @Test
    fun `exportRefuels CSV does not fetch vehicle`() = runTest {
        assumeFalse("saveFile()/FileProvider Robolectric bug on Windows JVM host", isWindows)
        coEvery { historyRepository.getRefuelHistory(1, 0, 100, null, null) } returns
            AppResult.Success(RefuelPage(items = listOf(refuelItem()), hasMore = false, currentPage = 0))

        val result = repository.exportRefuels(vehicleId = 1, format = ExportFormat.CSV)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 0) { vehicleRepository.getVehicleById(any()) }
    }

    @Test
    fun `exportRefuels PDF fetches vehicle and calls pdf writer`() = runTest {
        assumeFalse("saveFile()/FileProvider Robolectric bug on Windows JVM host", isWindows)
        coEvery { historyRepository.getRefuelHistory(1, 0, 100, null, null) } returns
            AppResult.Success(RefuelPage(items = listOf(refuelItem()), hasMore = false, currentPage = 0))
        coEvery { vehicleRepository.getVehicleById(1) } returns AppResult.Success(vehicle())
        every {
            pdfReportWriter.writeRefuelsReport(
                vehicleLabel = any(), periodLabel = any(), summary = any(),
                energyUnit = any(), consumptionUnit = any(), tableHeader = any(), tableRows = any(),
            )
        } returns ByteArray(0)

        val result = repository.exportRefuels(vehicleId = 1, format = ExportFormat.PDF)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { vehicleRepository.getVehicleById(1) }
    }

    @Test
    fun `exportRefuels PDF propagates vehicle fetch failure`() = runTest {
        coEvery { historyRepository.getRefuelHistory(1, 0, 100, null, null) } returns
            AppResult.Success(RefuelPage(items = emptyList(), hasMore = false, currentPage = 0))
        coEvery { vehicleRepository.getVehicleById(1) } returns AppResult.Failure(AppError.Network)

        val result = repository.exportRefuels(vehicleId = 1, format = ExportFormat.PDF)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Network)
    }
}
