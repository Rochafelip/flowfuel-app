package com.flowfuel.app.feature.vehicle.data

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeApi
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeBrandDto
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeModelDto
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeModelsResponseDto
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeYearDto
import com.flowfuel.app.feature.vehicle.domain.model.FipeOption
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FipeRepositoryImplTest {

    private val api: FipeApi = mockk()
    private val repository = FipeRepositoryImpl(api)

    @Test
    fun `getBrands maps dto list to FipeOption and uses carros path for Car`() = runTest {
        coEvery { api.getBrands("carros") } returns listOf(FipeBrandDto("21", "Fiat"))

        val result = repository.getBrands(VehicleType.Car) as AppResult.Success

        assertEquals(listOf(FipeOption("21", "Fiat")), result.value)
    }

    @Test
    fun `getBrands uses motos path for Motorcycle`() = runTest {
        coEvery { api.getBrands("motos") } returns listOf(FipeBrandDto("80", "Honda"))

        repository.getBrands(VehicleType.Motorcycle)

        coVerify(exactly = 1) { api.getBrands("motos") }
    }

    @Test
    fun `getModels maps nested modelos list to FipeOption`() = runTest {
        coEvery { api.getModels("carros", "21") } returns
            FipeModelsResponseDto(modelos = listOf(FipeModelDto(1004, "Uno")))

        val result = repository.getModels(VehicleType.Car, "21") as AppResult.Success

        assertEquals(listOf(FipeOption("1004", "Uno")), result.value)
    }

    @Test
    fun `getYears maps dto list to FipeOption`() = runTest {
        coEvery { api.getYears("carros", "21", "1004") } returns
            listOf(FipeYearDto("2016-1", "2016 Gasolina"))

        val result = repository.getYears(VehicleType.Car, "21", "1004") as AppResult.Success

        assertEquals(listOf(FipeOption("2016-1", "2016 Gasolina")), result.value)
    }

    @Test
    fun `getBrands network failure returns AppError Network`() = runTest {
        coEvery { api.getBrands("carros") } throws IOException("timeout")

        val result = repository.getBrands(VehicleType.Car) as AppResult.Failure

        assertEquals(AppError.Network, result.error)
    }
}
