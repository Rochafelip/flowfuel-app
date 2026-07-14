package com.flowfuel.app.core.notification.presentation

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.notification.data.NotificationPrefsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationPermissionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefsStore: NotificationPrefsStore = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setNotificationsEnabled(enabled: Boolean) {
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationsEnabled(enabled)
    }

    @Test
    fun `shows rationale when notifications are disabled and never shown before`() = runTest {
        setNotificationsEnabled(false)
        coEvery { prefsStore.hasShownRationale() } returns false

        val vm = NotificationPermissionViewModel(context, prefsStore)

        assertEquals(NotificationPermissionUiState.ShowRationale, vm.state.value)
    }

    @Test
    fun `stays idle when notifications are already enabled`() = runTest {
        setNotificationsEnabled(true)

        val vm = NotificationPermissionViewModel(context, prefsStore)

        assertEquals(NotificationPermissionUiState.Idle, vm.state.value)
    }

    @Test
    fun `stays idle when the rationale was already shown before`() = runTest {
        setNotificationsEnabled(false)
        coEvery { prefsStore.hasShownRationale() } returns true

        val vm = NotificationPermissionViewModel(context, prefsStore)

        assertEquals(NotificationPermissionUiState.Idle, vm.state.value)
    }

    @Test
    @Config(sdk = [30])
    fun `stays idle below Android 13 regardless of notification state`() = runTest {
        setNotificationsEnabled(false)

        val vm = NotificationPermissionViewModel(context, prefsStore)

        assertEquals(NotificationPermissionUiState.Idle, vm.state.value)
    }

    @Test
    fun `onRationaleShown transitions to Idle and persists the flag`() = runTest {
        setNotificationsEnabled(false)
        coEvery { prefsStore.hasShownRationale() } returns false
        coEvery { prefsStore.markRationaleShown() } returns Unit
        val vm = NotificationPermissionViewModel(context, prefsStore)

        vm.onRationaleShown()

        assertEquals(NotificationPermissionUiState.Idle, vm.state.value)
        coVerify { prefsStore.markRationaleShown() }
    }
}
