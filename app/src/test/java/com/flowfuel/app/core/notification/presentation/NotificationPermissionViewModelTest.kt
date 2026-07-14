package com.flowfuel.app.core.notification.presentation

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.notification.data.NotificationPrefsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()

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
        val prefsStore = NotificationPrefsStore(context)

        val vm = NotificationPermissionViewModel(context, prefsStore)
        advanceUntilIdle()

        assertEquals(NotificationPermissionUiState.ShowRationale, vm.state.value)
    }

    @Test
    fun `stays idle when notifications are already enabled`() = runTest {
        setNotificationsEnabled(true)
        val prefsStore = NotificationPrefsStore(context)

        val vm = NotificationPermissionViewModel(context, prefsStore)

        assertEquals(NotificationPermissionUiState.Idle, vm.state.value)
    }

    @Test
    fun `stays idle when the rationale was already shown before`() = runTest {
        setNotificationsEnabled(false)
        val prefsStore = NotificationPrefsStore(context)
        prefsStore.markRationaleShown()

        val vm = NotificationPermissionViewModel(context, prefsStore)

        assertEquals(NotificationPermissionUiState.Idle, vm.state.value)
    }

    @Test
    @Config(sdk = [30])
    fun `stays idle below Android 13 regardless of notification state`() = runTest {
        setNotificationsEnabled(false)
        val prefsStore = NotificationPrefsStore(context)

        val vm = NotificationPermissionViewModel(context, prefsStore)

        assertEquals(NotificationPermissionUiState.Idle, vm.state.value)
    }

    @Test
    fun `onRationaleShown transitions to Idle and persists the flag`() = runTest {
        setNotificationsEnabled(false)
        val prefsStore = NotificationPrefsStore(context)
        val vm = NotificationPermissionViewModel(context, prefsStore)
        advanceUntilIdle()

        vm.onRationaleShown()
        advanceUntilIdle()

        assertEquals(NotificationPermissionUiState.Idle, vm.state.value)
        assertTrue(prefsStore.hasShownRationale())
    }
}
