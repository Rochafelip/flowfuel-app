package com.flowfuel.app.feature.auth.presentation.profile

import android.net.Uri
import app.cash.turbine.test
import com.flowfuel.app.core.datastore.SessionStore
import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.notification.domain.DeviceTokenRepository
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShareStatus
import com.flowfuel.app.core.vehicleshare.domain.usecase.GetPendingVehicleSharesUseCase
import com.flowfuel.app.feature.auth.domain.model.UserProfile
import com.flowfuel.app.feature.auth.domain.usecase.DeleteAccountUseCase
import com.flowfuel.app.feature.auth.domain.usecase.DeleteProfilePictureUseCase
import com.flowfuel.app.feature.auth.domain.usecase.GetProfileStatsUseCase
import com.flowfuel.app.feature.auth.domain.usecase.GetProfileUseCase
import com.flowfuel.app.feature.auth.domain.usecase.LogoutUseCase
import com.flowfuel.app.feature.auth.domain.usecase.ProfileStats
import com.flowfuel.app.feature.auth.domain.usecase.UploadProfilePictureUseCase
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
    private val deviceTokenRepository: DeviceTokenRepository = mockk(relaxed = true)
    private val getPendingVehicleShares: GetPendingVehicleSharesUseCase = mockk()
    private val firebaseMessaging: FirebaseMessaging = mockk()

    private val photoUri: Uri = Uri.parse("content://media/test/photo.jpg")

    private val fixtureProfile = UserProfile(
        id = 1L,
        email = "user@example.com",
        name = "Fulano",
        phone = null,
        profilePictureUrl = "https://api.example.com/auth/1/profile-picture?cb=1000",
        createdAt = null,
    )

    private val fixtureShare = VehicleShare(
        id = 7,
        vehicleId = 1,
        vehicleBrand = "Fiat",
        vehicleModel = "Argo",
        ownerId = 2,
        ownerName = "Dono",
        guestId = 1,
        guestName = "Fulano",
        status = VehicleShareStatus.PENDING,
        createdAt = null,
        respondedAt = null,
        expiresAt = null,
    )

    /**
     * `getPendingVehicleShares()` é chamado a partir de `init { load() }`, então
     * precisa de stub antes de qualquer `createViewModel()`. Testes que não se
     * importam com convites pendentes usam este default (sem convites); os que
     * se importam (ver `load_comConvitesPendentes_expoePendingShareCount`)
     * registram o próprio `coEvery` antes de chamar `createViewModel()` — como o
     * MockK usa o stub mais recentemente registrado, o `coEvery` do teste
     * (registrado por último) prevalece sobre este default.
     */
    private fun stubNoPendingShares() {
        coEvery { getPendingVehicleShares() } returns AppResult.Success(emptyList())
    }

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
            deviceTokenRepository,
            getPendingVehicleShares,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // FirebaseMessaging.getInstance().token.await() is invoked internally by
        // ProfileViewModel.logout() (not injected) — see Task 8 brief. It's not mockable via
        // a constructor-injected mock, so we stub the static directly; this is scaffolding
        // not specified verbatim in the brief, added because the plain call throws in this
        // unit test environment and gets swallowed by runCatching, silently preventing
        // deviceTokenRepository.unregisterToken from ever being invoked.
        // Tasks.forResult() returns an already-completed Task, which the real (unmocked)
        // kotlinx.coroutines.tasks.await() extension resolves immediately.
        mockkStatic(FirebaseMessaging::class)
        every { FirebaseMessaging.getInstance() } returns firebaseMessaging
        every { firebaseMessaging.token } returns Tasks.forResult("fcm-token-abc")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseMessaging::class)
    }

    @Test
    fun `onPickImage success updates profilePictureUrl with the freshly uploaded URL, not a reloaded one`() {
        stubNoPendingShares()
        val viewModel = createViewModel()
        val bustedUrl = "https://api.example.com/auth/1/profile-picture?cb=999999"
        coEvery { uploadProfilePicture(photoUri) } returns AppResult.Success(bustedUrl)

        viewModel.onPickImage(photoUri)

        val state = viewModel.state.value as ProfileUiState.Content
        assertEquals(bustedUrl, state.profile.profilePictureUrl)
        assertFalse(state.isUploadingPhoto)
    }

    @Test
    fun `logout unregisters device token before clearing the session`() {
        stubNoPendingShares()
        val viewModel = createViewModel()

        viewModel.logout()

        coVerifyOrder {
            deviceTokenRepository.unregisterToken(any())
            sessionStore.clear()
        }
    }

    @Test
    fun load_comConvitesPendentes_expoePendingShareCount() {
        coEvery { getProfile() } returns AppResult.Success(fixtureProfile)
        coEvery { getProfileStats() } returns ProfileStats(vehiclesCount = 0, refuelsCount = 0, eventsCount = 0)
        coEvery { getPendingVehicleShares() } returns AppResult.Success(listOf(fixtureShare))

        val viewModel = createViewModel()

        val content = viewModel.state.value as ProfileUiState.Content
        assertEquals(1, content.pendingShareCount)
    }

    @Test
    fun load_semConvitesPendentes_mantemPendingShareCountZero() {
        stubNoPendingShares()

        val viewModel = createViewModel()

        val content = viewModel.state.value as ProfileUiState.Content
        assertEquals(0, content.pendingShareCount)
    }

    @Test
    fun load_falhaAoBuscarConvitesPendentes_mantemPendingShareCountZero() {
        coEvery { getPendingVehicleShares() } returns AppResult.Failure(AppError.Network)

        val viewModel = createViewModel()

        val content = viewModel.state.value as ProfileUiState.Content
        assertEquals(0, content.pendingShareCount)
    }

    @Test
    fun onPendingSharesClicked_comConvitePendente_emiteNavigateToShareInviteComIdDoPrimeiroItem() = runTest {
        coEvery { getPendingVehicleShares() } returns AppResult.Success(listOf(fixtureShare))
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onPendingSharesClicked()
            assertEquals(ProfileEffect.NavigateToShareInvite(fixtureShare.id), awaitItem())
        }
    }
}
