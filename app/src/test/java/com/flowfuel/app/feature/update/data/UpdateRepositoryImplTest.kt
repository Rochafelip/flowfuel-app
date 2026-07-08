package com.flowfuel.app.feature.update.data

import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.update.data.remote.GithubReleasesApi
import com.flowfuel.app.feature.update.data.remote.dto.GithubReleaseAssetDto
import com.flowfuel.app.feature.update.data.remote.dto.GithubReleaseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateRepositoryImplTest {

    private val githubReleasesApi: GithubReleasesApi = mockk()
    private val updatePrefsStore: UpdatePrefsStore = mockk()
    private lateinit var repository: UpdateRepositoryImpl

    @Before
    fun setUp() {
        repository = UpdateRepositoryImpl(
            context = ApplicationProvider.getApplicationContext(),
            githubReleasesApi = githubReleasesApi,
            updatePrefsStore = updatePrefsStore,
        )
    }

    private fun releaseDto(tagName: String, assetName: String = "flowfuel-app.apk") = GithubReleaseDto(
        tagName = tagName,
        body = "Notas da versão $tagName",
        assets = listOf(GithubReleaseAssetDto(name = assetName, downloadUrl = "https://example.com/$assetName")),
    )

    @Test
    fun `checkForUpdate returns UpdateInfo when remote tag is newer than the installed version`() = runTest {
        coEvery { githubReleasesApi.getLatestRelease() } returns releaseDto("v999.0.0")
        coEvery { updatePrefsStore.dismissedVersion() } returns null

        val result = repository.checkForUpdate()

        assertEquals("v999.0.0", result?.tag)
        assertEquals("999.0.0", result?.versionLabel)
        assertEquals("Notas da versão v999.0.0", result?.releaseNotes)
        assertEquals("https://example.com/flowfuel-app.apk", result?.downloadUrl)
    }

    @Test
    fun `checkForUpdate returns null when remote tag is not newer than the installed version`() = runTest {
        coEvery { githubReleasesApi.getLatestRelease() } returns releaseDto("v0.0.1")
        coEvery { updatePrefsStore.dismissedVersion() } returns null

        assertNull(repository.checkForUpdate())
    }

    @Test
    fun `checkForUpdate returns null when the release has no apk asset`() = runTest {
        coEvery { githubReleasesApi.getLatestRelease() } returns releaseDto("v999.0.0", assetName = "other-file.txt")
        coEvery { updatePrefsStore.dismissedVersion() } returns null

        assertNull(repository.checkForUpdate())
    }

    @Test
    fun `checkForUpdate returns null when the newer tag was already dismissed`() = runTest {
        coEvery { githubReleasesApi.getLatestRelease() } returns releaseDto("v999.0.0")
        coEvery { updatePrefsStore.dismissedVersion() } returns "v999.0.0"

        assertNull(repository.checkForUpdate())
    }

    @Test
    fun `checkForUpdate returns null and does not throw when the network call fails`() = runTest {
        coEvery { githubReleasesApi.getLatestRelease() } throws IOException("sem conexão")

        assertNull(repository.checkForUpdate())
    }

    @Test
    fun `dismiss delegates to UpdatePrefsStore with the given tag`() = runTest {
        coEvery { updatePrefsStore.dismissVersion("v1.2.0") } returns Unit

        repository.dismiss("v1.2.0")

        coVerify { updatePrefsStore.dismissVersion("v1.2.0") }
    }
}
