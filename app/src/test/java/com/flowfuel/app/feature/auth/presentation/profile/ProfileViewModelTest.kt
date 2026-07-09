package com.flowfuel.app.feature.auth.presentation.profile

import android.net.Uri
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.feature.auth.domain.model.UserProfile
import com.flowfuel.app.feature.auth.domain.usecase.DeleteAccountUseCase
import com.flowfuel.app.feature.auth.domain.usecase.DeleteProfilePictureUseCase
import com.flowfuel.app.feature.auth.domain.usecase.GetProfileStatsUseCase
import com.flowfuel.app.feature.auth.domain.usecase.GetProfileUseCase
import com.flowfuel.app.feature.auth.domain.usecase.LogoutUseCase
import com.flowfuel.app.feature.auth.domain.usecase.ProfileStats
import com.flowfuel.app.feature.auth.domain.usecase.UploadProfilePictureUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val getProfile: GetProfileUseCase = mockk()
    private val getProfileStats: GetProfileStatsUseCase = mockk()
    private val logoutUseCase: LogoutUseCase = mockk(relaxed = true)
    private val sessionStore: SessionStore = mockk(relaxed = true)
    private val uploadProfilePicture: UploadProfilePictureUseCase = mockk()
    private val deleteProfilePicture: DeleteProfilePictureUseCase = mockk()
    private val deleteAccount: DeleteAccountUseCase = mockk(relaxed = true)

    private val photoUri: Uri = Uri.parse("content://media/test/photo.jpg")

    private val fixtureProfile = UserProfile(
        id = 1L,
        email = "user@example.com",
        name = "Fulano",
        phone = null,
        profilePictureUrl = "https://api.example.com/auth/1/profile-picture?cb=1000",
        createdAt = null,
    )

    private fun createViewModel(): ProfileViewModel {
        coEvery { getProfile() } returns AppResult.Success(fixtureProfile)
        coEvery { getProfileStats() } returns ProfileStats(vehiclesCount = 0, refuelsCount = 0, eventsCount = 0)
        return ProfileViewModel(
            getProfile,
            getProfileStats,
            logoutUseCase,
            sessionStore,
            uploadProfilePicture,
            deleteProfilePicture,
            deleteAccount,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onPickImage success updates profilePictureUrl with the freshly uploaded URL, not a reloaded one`() {
        val viewModel = createViewModel()
        val bustedUrl = "https://api.example.com/auth/1/profile-picture?cb=999999"
        coEvery { uploadProfilePicture(photoUri) } returns AppResult.Success(bustedUrl)

        viewModel.onPickImage(photoUri)

        val state = viewModel.state.value as ProfileUiState.Content
        assertEquals(bustedUrl, state.profile.profilePictureUrl)
        assertFalse(state.isUploadingPhoto)
    }
}
