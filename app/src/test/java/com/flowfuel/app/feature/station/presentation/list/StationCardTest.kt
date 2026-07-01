package com.flowfuel.app.feature.station.presentation.list

import org.junit.Assert.assertEquals
import org.junit.Test

class StationCardTest {

    @Test
    fun `formats distances under 1000m in meters`() {
        assertEquals("420 m", formatDistance(420))
        assertEquals("0 m", formatDistance(0))
        assertEquals("999 m", formatDistance(999))
    }

    @Test
    fun `formats distances of 1000m or more in kilometers with one decimal`() {
        assertEquals("1,0 km", formatDistance(1000))
        assertEquals("1,2 km", formatDistance(1200))
        assertEquals("12,0 km", formatDistance(12000))
    }
}
