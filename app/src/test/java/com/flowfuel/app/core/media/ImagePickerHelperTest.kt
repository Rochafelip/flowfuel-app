package com.flowfuel.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImagePickerHelperTest {

    private lateinit var helper: ImagePickerHelper
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        helper = ImagePickerHelper(context)
    }

    private fun writeJpeg(width: Int, height: Int, color: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = File.createTempFile("crop_test", ".jpg", context.cacheDir)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        return file
    }

    @Test
    fun `loadForCropping rotates the bitmap according to EXIF orientation`() {
        val file = writeJpeg(width = 4, height = 2, color = Color.RED)
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val result = helper.loadForCropping(Uri.fromFile(file))

        assertEquals(2, result.width)
        assertEquals(4, result.height)
    }

    @Test
    fun `loadForCropping keeps original orientation when there is no EXIF rotation`() {
        val file = writeJpeg(width = 4, height = 2, color = Color.BLUE)

        val result = helper.loadForCropping(Uri.fromFile(file))

        assertEquals(4, result.width)
        assertEquals(2, result.height)
    }
}
