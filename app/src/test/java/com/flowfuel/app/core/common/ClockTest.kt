package com.flowfuel.app.core.common

import org.junit.Assert.assertTrue
import org.junit.Test

class ClockTest {

    @Test
    fun `SystemClock returns the current wall-clock time in millis`() {
        val before = System.currentTimeMillis()
        val clock = SystemClock()
        val now = clock.nowMillis()
        val after = System.currentTimeMillis()

        assertTrue(now in before..after)
    }
}
