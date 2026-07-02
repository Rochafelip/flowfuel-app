package com.flowfuel.app.feature.station.presentation.list

import org.junit.Assert.assertEquals
import org.junit.Test

class StationDistanceFilterRowTest {

    @Test
    fun `formats radius presets in whole kilometers`() {
        assertEquals("1 km", formatRadiusLabel(1000))
        assertEquals("3 km", formatRadiusLabel(3000))
        assertEquals("5 km", formatRadiusLabel(5000))
        assertEquals("10 km", formatRadiusLabel(10000))
    }
}
