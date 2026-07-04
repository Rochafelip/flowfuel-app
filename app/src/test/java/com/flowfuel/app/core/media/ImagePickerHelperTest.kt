package com.flowfuel.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `cropToCache writes a square jpeg with the requested output size`() {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val cropRect = CropRect(left = 50, top = 50, size = 200)

        val resultUri = helper.cropToCache(bitmap, cropRect, outputSizePx = 300)

        val outFile = File(requireNotNull(resultUri.path))
        assertTrue(outFile.exists())
        val decoded = android.graphics.BitmapFactory.decodeFile(outFile.absolutePath)
        assertEquals(300, decoded.width)
        assertEquals(300, decoded.height)
    }

    @Test
    fun `cropToCache deletes the previously cached crop file`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val cropRect = CropRect(left = 0, top = 0, size = 100)

        val firstFile = File(requireNotNull(helper.cropToCache(bitmap, cropRect).path))
        assertTrue(firstFile.exists())

        val secondFile = File(requireNotNull(helper.cropToCache(bitmap, cropRect).path))

        assertFalse(firstFile.exists())
        assertTrue(secondFile.exists())
    }
}
