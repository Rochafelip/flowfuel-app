package com.flowfuel.app.feature.update.presentation

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.update.domain.UpdateRepository
import com.flowfuel.app.feature.update.domain.model.UpdateInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
class UpdateViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val updateRepository: UpdateRepository = mockk()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun updateInfo(tag: String = "v9.9.9") = UpdateInfo(
        tag = tag,
        versionLabel = tag.removePrefix("v"),
        releaseNotes = "notas",
        downloadUrl = "https://example.com/a.apk",
    )

    private fun buildViewModel(isDebugBuild: Boolean = false) =
        UpdateViewModel(updateRepository, context, isDebugBuild)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts checking for an update and shows Available when a newer release exists`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()

        val vm = buildViewModel()

        assertEquals(UpdateUiState.Available(updateInfo()), vm.state.value)
    }

    @Test
    fun `stays Idle when there is no update available`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns null

        val vm = buildViewModel()

        assertEquals(UpdateUiState.Idle, vm.state.value)
    }

    @Test
    fun `never checks for an update on a debug build`() = runTest {
        val vm = buildViewModel(isDebugBuild = true)

        assertEquals(UpdateUiState.Idle, vm.state.value)
        coVerify(inverse = true) { updateRepository.checkForUpdate() }
    }

    @Test
    fun `onDismiss persists the dismissed tag and returns to Idle`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        coEvery { updateRepository.dismiss("v9.9.9") } returns Unit
        val vm = buildViewModel()

        vm.onDismiss()

        assertEquals(UpdateUiState.Idle, vm.state.value)
        coVerify { updateRepository.dismiss("v9.9.9") }
    }

    @Test
    fun `onUpdateClick starts a download directly when install permission is already granted`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns true
        every { updateRepository.enqueueDownload(updateInfo()) } returns 42L
        val vm = buildViewModel()

        vm.onUpdateClick()

        assertEquals(UpdateUiState.Downloading(updateInfo()), vm.state.value)
    }

    @Test
    fun `onUpdateClick asks for install permission first when it is not granted`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns false
        val vm = buildViewModel()

        vm.onUpdateClick()

        assertEquals(UpdateUiState.RequestingInstallPermission(updateInfo()), vm.state.value)
    }

    @Test
    fun `onInstallPermissionResumed starts the download once permission is granted`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returnsMany listOf(false, true)
        every { updateRepository.enqueueDownload(updateInfo()) } returns 42L
        val vm = buildViewModel()
        vm.onUpdateClick()

        vm.onInstallPermissionResumed()

        assertEquals(UpdateUiState.Downloading(updateInfo()), vm.state.value)
    }

    @Test
    fun `onInstallPermissionResumed goes back to Available when permission is still missing`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns false
        val vm = buildViewModel()
        vm.onUpdateClick()

        vm.onInstallPermissionResumed()

        assertEquals(UpdateUiState.Available(updateInfo()), vm.state.value)
    }

    @Test
    fun `transitions to ReadyToInstall when the tracked download completes successfully`() = runTest {
        val installUri = Uri.parse("content://com.flowfuel.app.debug.provider/downloads/flowfuel-update.apk")
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns true
        every { updateRepository.enqueueDownload(updateInfo()) } returns 42L
        every { updateRepository.isDownloadComplete(42L) } returns true
        every { updateRepository.installUri(42L) } returns installUri
        val vm = buildViewModel()
        vm.onUpdateClick()

        context.sendBroadcast(
            Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 42L)
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(UpdateUiState.ReadyToInstall(installUri), vm.state.value)
    }

    @Test
    fun `goes back to Available when the tracked download fails`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns true
        every { updateRepository.enqueueDownload(updateInfo()) } returns 42L
        every { updateRepository.isDownloadComplete(42L) } returns false
        val vm = buildViewModel()
        vm.onUpdateClick()

        context.sendBroadcast(
            Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 42L)
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(UpdateUiState.Available(updateInfo()), vm.state.value)
    }

    @Test
    fun `ignores a download-complete broadcast for a different downloadId`() = runTest {
        coEvery { updateRepository.checkForUpdate() } returns updateInfo()
        every { updateRepository.canRequestPackageInstalls() } returns true
        every { updateRepository.enqueueDownload(updateInfo()) } returns 42L
        val vm = buildViewModel()
        vm.onUpdateClick()

        context.sendBroadcast(
            Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 999L)
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(UpdateUiState.Downloading(updateInfo()), vm.state.value)
    }
}
