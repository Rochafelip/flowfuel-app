package com.flowfuel.app.feature.home.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlySpendingBarChartTest {

    @Test
    fun `barHeightFraction e proporcional ao maior valor da serie`() {
        assertEquals(1.0f, barHeightFraction(amount = 543.0, maxAmount = 543.0), 0.001f)
        assertEquals(0.5f, barHeightFraction(amount = 210.0, maxAmount = 420.0), 0.001f)
    }

    @Test
    fun `barHeightFraction nunca fica abaixo do piso minimo, mesmo para valor 0`() {
        assertEquals(0.04f, barHeightFraction(amount = 0.0, maxAmount = 500.0), 0.001f)
        assertEquals(0.04f, barHeightFraction(amount = 1.0, maxAmount = 500.0), 0.001f)
    }

    @Test
    fun `barHeightFraction e 0 quando toda a serie e zero`() {
        assertEquals(0.0f, barHeightFraction(amount = 0.0, maxAmount = 0.0), 0.001f)
    }
}
