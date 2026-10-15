package com.flowfuel.app.feature.flexcalculator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlexFuelTest {

    @Test
    fun `parseDecimal accepts comma and dot as decimal separator`() {
        assertEquals(4.29, FlexFuel.parseDecimal("4,29")!!, 1e-9)
        assertEquals(4.29, FlexFuel.parseDecimal("4.29")!!, 1e-9)
        assertEquals(12.0, FlexFuel.parseDecimal(" 12 ")!!, 1e-9)
    }

    @Test
    fun `parseDecimal rejects empty, non numeric, zero and negative`() {
        assertNull(FlexFuel.parseDecimal(""))
        assertNull(FlexFuel.parseDecimal("   "))
        assertNull(FlexFuel.parseDecimal("abc"))
        assertNull(FlexFuel.parseDecimal("0"))
        assertNull(FlexFuel.parseDecimal("-1"))
        assertNull(FlexFuel.parseDecimal("1,2,3"))
    }

    @Test
    fun `ratioRule picks ethanol below 70 percent`() {
        val result = FlexFuel.ratioRule(ethanolPrice = 4.29, gasolinePrice = 6.19)
        assertEquals(FuelWinner.Ethanol, result.winner)
        assertEquals(0.693, result.ratio, 0.001)
    }

    @Test
    fun `ratioRule picks gasoline above 70 percent`() {
        val result = FlexFuel.ratioRule(ethanolPrice = 4.50, gasolinePrice = 6.19)
        assertEquals(FuelWinner.Gasoline, result.winner)
        assertEquals(0.727, result.ratio, 0.001)
    }

    @Test
    fun `ratioRule ties at exactly 70 percent despite floating point error`() {
        // 4,13 / 5,90 = 0,7000000000000001 em double.
        assertEquals(FuelWinner.Tie, FlexFuel.ratioRule(ethanolPrice = 4.13, gasolinePrice = 5.90).winner)
        assertEquals(FuelWinner.Tie, FlexFuel.ratioRule(ethanolPrice = 7.0, gasolinePrice = 10.0).winner)
    }

    @Test
    fun `exactCost compares cost per km and computes savings`() {
        val result = FlexFuel.exactCost(
            ethanolPrice = 4.29,
            gasolinePrice = 6.19,
            ethanolKmPerLiter = 8.5,
            gasolineKmPerLiter = 12.0,
        )
        assertEquals(0.505, result.ethanolCostPerKm, 0.001)
        assertEquals(0.516, result.gasolineCostPerKm, 0.001)
        assertEquals(FuelWinner.Ethanol, result.winner)
        assertEquals(2.2, result.savingsPercent, 0.05)
    }

    @Test
    fun `exactCost picks gasoline when it is cheaper per km`() {
        val result = FlexFuel.exactCost(
            ethanolPrice = 4.50,
            gasolinePrice = 6.19,
            ethanolKmPerLiter = 8.0,
            gasolineKmPerLiter = 12.0,
        )
        assertEquals(FuelWinner.Gasoline, result.winner)
    }

    @Test
    fun `exactCost ties when cost per km is equal`() {
        val result = FlexFuel.exactCost(
            ethanolPrice = 4.0,
            gasolinePrice = 6.0,
            ethanolKmPerLiter = 8.0,
            gasolineKmPerLiter = 12.0,
        )
        assertEquals(FuelWinner.Tie, result.winner)
        assertEquals(0.0, result.savingsPercent, 1e-9)
    }
}
