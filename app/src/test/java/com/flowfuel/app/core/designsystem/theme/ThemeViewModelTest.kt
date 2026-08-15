package com.flowfuel.app.core.designsystem.theme

import com.flowfuel.app.core.datastore.ThemePrefsStore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val themePrefsStore: ThemePrefsStore = mockk()
    private val storedMode = MutableStateFlow(ThemeMode.SYSTEM)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { themePrefsStore.themeModeFlow() } returns storedMode
        coEvery { themePrefsStore.setThemeMode(any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `themeMode reflects the store's current value`() = runTest {
        val viewModel = ThemeViewModel(themePrefsStore)

        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)

        storedMode.value = ThemeMode.DARK

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `setThemeMode persists the chosen mode via the store`() = runTest {
        val viewModel = ThemeViewModel(themePrefsStore)

        viewModel.setThemeMode(ThemeMode.LIGHT)

        coVerify { themePrefsStore.setThemeMode(ThemeMode.LIGHT) }
    }
}
