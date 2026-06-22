package com.flowfuel.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImagePickerHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun compressToJpeg(uri: Uri, maxWidthPx: Int = 512, qualityPercent: Int = 75): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        val sampleSize = calculateInSampleSize(bounds.outWidth, maxWidthPx)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val sampled = context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IllegalArgumentException("Não foi possível decodificar a imagem: $uri")

        val targetHeight = if (sampled.width > 0)
            (sampled.height.toFloat() * maxWidthPx / sampled.width).toInt().coerceAtLeast(1)
        else
            maxWidthPx
        val scaled = Bitmap.createScaledBitmap(sampled, maxWidthPx, targetHeight, true)

        return ByteArrayOutputStream().also { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, qualityPercent, out)
        }.toByteArray()
    }

    private fun calculateInSampleSize(outWidth: Int, maxWidth: Int): Int {
        if (outWidth <= maxWidth) return 1
        var sampleSize = 1
        var halfWidth = outWidth / 2
        while (halfWidth >= maxWidth) {
            sampleSize *= 2
            halfWidth /= 2
        }
        return sampleSize
    }
}
