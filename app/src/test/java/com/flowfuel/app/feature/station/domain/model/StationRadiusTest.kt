package com.flowfuel.app.feature.station.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StationRadiusTest {

    @Test
    fun `first preset band starts at zero so the closest stations are never excluded`() {
        val band = stationDistanceBand(1_000)

        assertEquals(0, band.minMeters)
        assertEquals(2_999, band.maxMeters)
    }

    @Test
    fun `middle preset band starts at the preset value and ends just before the next preset`() {
        assertEquals(StationDistanceBand(minMeters = 3_000, maxMeters = 4_999), stationDistanceBand(3_000))
        assertEquals(StationDistanceBand(minMeters = 5_000, maxMeters = 9_999), stationDistanceBand(5_000))
    }

    @Test
    fun `last preset band has no next step, so it caps the query radius instead`() {
        val band = stationDistanceBand(10_000)

        assertEquals(10_000, band.minMeters)
        assertEquals(20_000, band.maxMeters)
    }
}
