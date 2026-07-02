package com.flowfuel.app.feature.station.presentation.list

import com.flowfuel.app.feature.station.domain.model.StationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `fuel type badge shows Combustivel label and preserves the existing content description`() {
        val content = StationType.Fuel.badgeContent()
        assertEquals("Combustível", content.label)
        assertEquals("Posto de combustível", content.contentDescription)
    }

    @Test
    fun `electric type badge shows Eletrico label and preserves the existing content description`() {
        val content = StationType.Electric.badgeContent()
        assertEquals("Elétrico", content.label)
        assertEquals("Estação de recarga elétrica", content.contentDescription)
    }

    @Test
    fun `formats rating with one decimal using pt-BR comma separator`() {
        assertEquals("4,8", formatRating(4.8))
        assertEquals("5,0", formatRating(5.0))
        assertEquals("3,7", formatRating(3.7))
    }

    @Test
    fun `formatAddress combines street and house number with a comma`() {
        assertEquals("Avenida Alfredo Lisboa, 173", formatAddress("Avenida Alfredo Lisboa", "173"))
    }

    @Test
    fun `formatAddress returns only the street when house number is null`() {
        assertEquals("Avenida Conde da Boa Vista", formatAddress("Avenida Conde da Boa Vista", null))
    }

    @Test
    fun `formatAddress returns null when street is null even if house number is present`() {
        assertNull(formatAddress(null, "173"))
    }

    @Test
    fun `formatAddress returns null when both street and house number are null`() {
        assertNull(formatAddress(null, null))
    }

    @Test
    fun `formatAddress treats a blank street as absent`() {
        assertNull(formatAddress("", "173"))
    }
}
