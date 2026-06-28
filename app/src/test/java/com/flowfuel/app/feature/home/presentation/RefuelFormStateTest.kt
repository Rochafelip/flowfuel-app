package com.flowfuel.app.feature.home.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class RefuelFormStateTest {

    // ── Defaults ──────────────────────────────────────────────────────────────

    @Test
    fun `default odometerInputMode is TRIP`() {
        assertEquals(OdometerInputMode.TRIP, RefuelFormState().odometerInputMode)
    }

    // ── canSubmit — modo TRIP ─────────────────────────────────────────────────

    @Test
    fun `canSubmit TRIP true when tripKm valid liters and price set`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "310,6",
            liters = "21,29",
            totalPriceRaw = "14842",
        )
        assertTrue(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit TRIP false when tripKm blank`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "",
            liters = "21,29",
            totalPriceRaw = "14842",
        )
        assertFalse(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit TRIP false when tripKm is zero`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "0",
            liters = "21,29",
            totalPriceRaw = "14842",
        )
        assertFalse(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit TRIP false when tripKm is negative`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "-10",
            liters = "21,29",
            totalPriceRaw = "14842",
        )
        assertFalse(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit TRIP false when liters blank`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "310,6",
            liters = "",
            totalPriceRaw = "14842",
        )
        assertFalse(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit TRIP false when price zero`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "310,6",
            liters = "21,29",
            totalPriceRaw = "",
        )
        assertFalse(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit TRIP hybrid false when refuelType null`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "310,6",
            liters = "21,29",
            totalPriceRaw = "14842",
            refuelType = null,
        )
        assertFalse(form.canSubmit(isHybrid = true))
    }

    @Test
    fun `canSubmit TRIP hybrid true when refuelType set`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.TRIP,
            tripKm = "310,6",
            liters = "21,29",
            totalPriceRaw = "14842",
            refuelType = "FUEL",
        )
        assertTrue(form.canSubmit(isHybrid = true))
    }

    // ── canSubmit — modo ODOMETER ─────────────────────────────────────────────

    @Test
    fun `canSubmit ODOMETER true when odometer liters and price set`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.ODOMETER,
            odometer = "672700",
            liters = "21,29",
            totalPriceRaw = "14842",
        )
        assertTrue(form.canSubmit(isHybrid = false))
    }

    @Test
    fun `canSubmit ODOMETER false when odometer blank`() {
        val form = RefuelFormState(
            odometerInputMode = OdometerInputMode.ODOMETER,
            odometer = "",
            liters = "21,29",
            totalPriceRaw = "14842",
        )
        assertFalse(form.canSubmit(isHybrid = false))
    }
}
