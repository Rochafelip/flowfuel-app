package com.flowfuel.app.feature.auto

import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.auto.dashboard.AutoDashboardScreen
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoSessionTest {

    private val carContext: TestCarContext
        get() = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    private fun makeSession() = AutoSession(
        sessionStore = mockk(relaxed = true),
        getActiveVehicle = mockk(),
        getDashboard = mockk(),
        createRefuel = mockk(),
    )

    @Test
    fun `null token returns AutoLoginScreen`() {
        val screen = makeSession().createInitialScreen(carContext, token = null)
        assertTrue(screen is AutoLoginScreen)
    }

    @Test
    fun `blank token returns AutoLoginScreen`() {
        val screen = makeSession().createInitialScreen(carContext, token = "   ")
        assertTrue(screen is AutoLoginScreen)
    }

    @Test
    fun `valid token returns AutoDashboardScreen`() {
        val screen = makeSession().createInitialScreen(carContext, token = "some-jwt")
        assertTrue(screen is AutoDashboardScreen)
    }
}
