package com.flowfuel.app.feature.station.domain

import com.flowfuel.app.core.common.Clock
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.station.domain.model.DEFAULT_STATION_RADIUS_METERS
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.flowfuel.app.feature.station.domain.model.Station
import com.flowfuel.app.feature.station.domain.model.StationType
import com.flowfuel.app.feature.station.domain.model.stationDistanceBand
import com.flowfuel.app.feature.station.domain.usecase.GetNearbyStationsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyStationsPrefetcherTest {

    private class FakeClock(var millis: Long = 0L) : Clock {
        override fun nowMillis(): Long = millis
    }

    private val getNearbyStations: GetNearbyStationsUseCase = mockk()
    private val locationProvider: LocationProvider = mockk()
    private val clock = FakeClock()
    private val location = GeoLocation(latitude = -8.05, longitude = -34.90)

    // Prefetch busca sempre a faixa do preset padrão — o raio enviado à API é o teto da
    // faixa (próximo preset - 1), não o valor do preset em si.
    private val defaultBandMaxMeters = stationDistanceBand(DEFAULT_STATION_RADIUS_METERS).maxMeters

    private fun station(id: String, distanceMeters: Int = DEFAULT_STATION_RADIUS_METERS + 500) = Station(
        placeId = id, name = "Posto $id", type = StationType.Fuel,
        distanceMeters = distanceMeters, rating = null, latitude = -8.05, longitude = -34.90,
    )

    private fun buildPrefetcher() =
        NearbyStationsPrefetcher(getNearbyStations, locationProvider, clock, UnconfinedTestDispatcher())

    @Test
    fun `prefetch on success stores the result with the clock's timestamp`() {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        clock.millis = 1_000L
        val prefetcher = buildPrefetcher()

        prefetcher.prefetch()

        assertEquals(listOf(station("a")), prefetcher.freshCachedStations())
    }

    @Test
    fun `prefetch filters out stations below the default band's lower bound`() {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(
            listOf(station("a", distanceMeters = DEFAULT_STATION_RADIUS_METERS - 1), station("b"))
        )
        val prefetcher = buildPrefetcher()

        prefetcher.prefetch()

        assertEquals(listOf(station("b")), prefetcher.freshCachedStations())
    }

    @Test
    fun `prefetch with PermissionDenied does not touch an existing cache`() {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location) andThen LocationResult.PermissionDenied
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        val prefetcher = buildPrefetcher()
        prefetcher.prefetch()

        prefetcher.prefetch()

        assertEquals(listOf(station("a")), prefetcher.freshCachedStations())
    }

    @Test
    fun `prefetch with Unavailable does not touch an existing cache`() {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location) andThen LocationResult.Unavailable
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        val prefetcher = buildPrefetcher()
        prefetcher.prefetch()

        prefetcher.prefetch()

        assertEquals(listOf(station("a")), prefetcher.freshCachedStations())
    }

    @Test
    fun `prefetch with an API failure does not touch an existing cache`() {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a"))) andThen AppResult.Failure(AppError.Network)
        val prefetcher = buildPrefetcher()
        prefetcher.prefetch()

        prefetcher.prefetch()

        assertEquals(listOf(station("a")), prefetcher.freshCachedStations())
    }

    @Test
    fun `freshCachedStations returns the list when within the TTL`() {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        clock.millis = 0L
        val prefetcher = buildPrefetcher()
        prefetcher.prefetch()

        clock.millis = 300_000L // exactly 5 minutes later, still within TTL (inclusive)

        assertEquals(listOf(station("a")), prefetcher.freshCachedStations())
    }

    @Test
    fun `freshCachedStations returns null once the clock passes the TTL`() {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a")))
        clock.millis = 0L
        val prefetcher = buildPrefetcher()
        prefetcher.prefetch()

        clock.millis = 300_001L

        assertNull(prefetcher.freshCachedStations())
    }

    @Test
    fun `two sequential prefetch calls overwrite the cache with the second result`() {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Available(location)
        coEvery { getNearbyStations(location, defaultBandMaxMeters) } returns AppResult.Success(listOf(station("a"))) andThen AppResult.Success(listOf(station("b")))
        val prefetcher = buildPrefetcher()

        prefetcher.prefetch()
        prefetcher.prefetch()

        assertEquals(listOf(station("b")), prefetcher.freshCachedStations())
    }

    @Test
    fun `updateCache stores the given stations with the clock's timestamp`() {
        clock.millis = 42L
        val prefetcher = buildPrefetcher()

        prefetcher.updateCache(listOf(station("a")))

        assertEquals(listOf(station("a")), prefetcher.freshCachedStations())
    }
}
