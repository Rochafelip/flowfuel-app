package com.flowfuel.app.feature.auth.data

import android.net.Uri
import com.flowfuel.app.BuildConfig
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.media.ImagePickerHelper
import com.flowfuel.app.feature.auth.data.remote.ProfileApi
import com.flowfuel.app.feature.auth.data.remote.dto.UploadResponseDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileRepositoryImplTest {

    private val api: ProfileApi = mockk()
    private val imagePickerHelper: ImagePickerHelper = mockk()
    private val repository = ProfileRepositoryImpl(api, imagePickerHelper)

    private val photoUri: Uri = Uri.parse("content://media/test/photo.jpg")

    @Test
    fun `uploadProfilePicture prefixes internalUrl with API_BASE_URL and cache-busts it when signedUrl is absent`() = runTest {
        coEvery { imagePickerHelper.compressToJpeg(photoUri) } returns ByteArray(1)
        coEvery { api.uploadProfilePicture("1", any()) } returns
            UploadResponseDto(internalUrl = "/auth/1/profile-picture", signedUrl = null)

        val result = repository.uploadProfilePicture("1", photoUri) as AppResult.Success

        val expectedBase = BuildConfig.API_BASE_URL.trimEnd('/') + "/auth/1/profile-picture"
        assertTrue(
            "URL deveria começar com a base absoluta, mas foi: ${result.value}",
            result.value.startsWith("$expectedBase?cb="),
        )
    }

    @Test
    fun `uploadProfilePicture uses signedUrl as-is with cache-busting when present`() = runTest {
        coEvery { imagePickerHelper.compressToJpeg(photoUri) } returns ByteArray(1)
        coEvery { api.uploadProfilePicture("1", any()) } returns
            UploadResponseDto(internalUrl = "/auth/1/profile-picture", signedUrl = "https://s3.example.com/signed")

        val result = repository.uploadProfilePicture("1", photoUri) as AppResult.Success

        assertTrue(result.value.startsWith("https://s3.example.com/signed?cb="))
    }

    @Test
    fun `uploadProfilePicture called twice returns URLs with different cache-bust tokens`() = runTest {
        coEvery { imagePickerHelper.compressToJpeg(photoUri) } returns ByteArray(1)
        coEvery { api.uploadProfilePicture("1", any()) } returns
            UploadResponseDto(internalUrl = "/auth/1/profile-picture", signedUrl = null)

        val first = (repository.uploadProfilePicture("1", photoUri) as AppResult.Success).value
        Thread.sleep(2)
        val second = (repository.uploadProfilePicture("1", photoUri) as AppResult.Success).value

        assertTrue("URLs consecutivas não deveriam ser idênticas (cache-busting)", first != second)
    }
}
