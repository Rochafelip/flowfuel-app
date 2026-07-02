package com.flowfuel.app.feature.station.data

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.data.remote.StationApi
import com.flowfuel.app.feature.station.data.remote.StationResponseDto
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.StationType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StationRepositoryImplTest {

    private val api: StationApi = mockk()
    private val repository = StationRepositoryImpl(api)
    private val location = GeoLocation(latitude = -8.05, longitude = -34.90)

    @Test
    fun `maps dtos to domain and sorts by distance`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } returns listOf(
            StationResponseDto(
                placeId = "b", name = "Ipiranga", type = "FUEL",
                distanceMeters = 650, rating = null, latitude = -8.05, longitude = -34.90,
            ),
            StationResponseDto(
                placeId = "a", name = "Shell Boa Viagem", type = "FUEL",
                distanceMeters = 420, rating = 4.6, latitude = -8.05, longitude = -34.91,
            ),
            StationResponseDto(
                placeId = "c", name = "Estação Volta", type = "ELECTRIC",
                distanceMeters = 900, rating = null, latitude = -8.06, longitude = -34.92,
            ),
        )

        val result = repository.getNearbyStations(location, radiusMeters = 5000)

        assertTrue(result is AppResult.Success)
        val stations = (result as AppResult.Success).value
        assertEquals(listOf("Shell Boa Viagem", "Ipiranga", "Estação Volta"), stations.map { it.name })
        assertEquals(StationType.Fuel, stations[0].type)
        assertEquals(StationType.Electric, stations[2].type)
    }

    @Test
    fun `returns empty list when backend has no nearby stations`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } returns emptyList()

        val result = repository.getNearbyStations(location, radiusMeters = 5000)

        assertTrue(result is AppResult.Success)
        assertEquals(emptyList<Any>(), (result as AppResult.Success).value)
    }

    @Test
    fun `maps network failure to AppError-Network`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } throws IOException("no network")

        val result = repository.getNearbyStations(location, radiusMeters = 5000)

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.Network, (result as AppResult.Failure).error)
    }

    @Test
    fun `forwards radiusMeters to the API unchanged`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } returns emptyList()

        repository.getNearbyStations(location, radiusMeters = 3000)

        coVerify { api.getNearbyStations(lat = location.latitude, lng = location.longitude, radiusMeters = 3000) }
    }

    @Test
    fun `propagates street and houseNumber from dto to domain unchanged`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } returns listOf(
            StationResponseDto(
                placeId = "a", name = "Shell Boa Viagem", type = "FUEL",
                distanceMeters = 420, rating = 4.6, latitude = -8.05, longitude = -34.91,
                street = "Avenida Alfredo Lisboa", houseNumber = "173",
            ),
        )

        val result = repository.getNearbyStations(location, radiusMeters = 5000)

        assertTrue(result is AppResult.Success)
        val station = (result as AppResult.Success).value.single()
        assertEquals("Avenida Alfredo Lisboa", station.street)
        assertEquals("173", station.houseNumber)
    }

    @Test
    fun `defaults street and houseNumber to null when the dto omits them`() = runTest {
        coEvery { api.getNearbyStations(any(), any(), any()) } returns listOf(
            StationResponseDto(
                placeId = "b", name = "Ipiranga", type = "FUEL",
                distanceMeters = 650, rating = null, latitude = -8.05, longitude = -34.90,
            ),
        )

        val result = repository.getNearbyStations(location, radiusMeters = 5000)

        assertTrue(result is AppResult.Success)
        val station = (result as AppResult.Success).value.single()
        assertNull(station.street)
        assertNull(station.houseNumber)
    }
}
