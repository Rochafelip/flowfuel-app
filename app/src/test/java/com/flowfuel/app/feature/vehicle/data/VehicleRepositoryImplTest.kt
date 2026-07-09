package com.flowfuel.app.feature.vehicle.data

import android.net.Uri
import com.flowfuel.app.BuildConfig
import com.flowfuel.app.core.domain.AppResult
import com.flowfuel.app.core.media.ImagePickerHelper
import com.flowfuel.app.feature.vehicle.data.remote.VehicleApi
import com.flowfuel.app.feature.vehicle.data.remote.VehiclePhotoResponseDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VehicleRepositoryImplTest {

    private val api: VehicleApi = mockk()
    private val imagePickerHelper: ImagePickerHelper = mockk()
    private val repository = VehicleRepositoryImpl(api, imagePickerHelper)

    private val photoUri: Uri = Uri.parse("content://media/test/photo.jpg")

    @Test
    fun `uploadVehiclePhoto prefixes internalUrl with API_BASE_URL and cache-busts it`() = runTest {
        coEvery { imagePickerHelper.compressToJpeg(photoUri) } returns ByteArray(1)
        coEvery { api.uploadVehiclePhoto(42, any()) } returns
            VehiclePhotoResponseDto(internalUrl = "/vehicles/42/photo")

        val result = repository.uploadVehiclePhoto(42, photoUri) as AppResult.Success

        val expectedBase = BuildConfig.API_BASE_URL.trimEnd('/') + "/vehicles/42/photo"
        assertTrue(
            "URL deveria começar com a base absoluta, mas foi: ${result.value}",
            result.value.startsWith("$expectedBase?cb="),
        )
    }

    @Test
    fun `uploadVehiclePhoto called twice returns URLs with different cache-bust tokens`() = runTest {
        coEvery { imagePickerHelper.compressToJpeg(photoUri) } returns ByteArray(1)
        coEvery { api.uploadVehiclePhoto(42, any()) } returns
            VehiclePhotoResponseDto(internalUrl = "/vehicles/42/photo")

        val first = (repository.uploadVehiclePhoto(42, photoUri) as AppResult.Success).value
        Thread.sleep(2)
        val second = (repository.uploadVehiclePhoto(42, photoUri) as AppResult.Success).value

        assertTrue("URLs consecutivas não deveriam ser idênticas (cache-busting)", first != second)
    }
}
