package com.flowfuel.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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

    /**
     * Decodifica [uri] já com a orientação EXIF corrigida, para uso no
     * [PhotoCropDialog] — sem essa correção, o usuário posicionaria a imagem
     * olhando para pixels rotacionados de forma diferente do recorte final.
     */
    fun loadForCropping(uri: Uri, maxDimensionPx: Int = 1024): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        val sampleSize = calculateInSampleSize(maxOf(bounds.outWidth, bounds.outHeight), maxDimensionPx)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IllegalArgumentException("Não foi possível decodificar a imagem: $uri")

        val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = orientationMatrix(orientation) ?: return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    /**
     * Recorta [bitmap] em [cropRect] (região quadrada já calculada por
     * [CropMath.computeCropRect]), redimensiona para [outputSizePx] e grava
     * como JPEG num arquivo de cache, apagando o crop anterior — evita
     * acumular arquivos temporários em uso prolongado do app.
     */
    fun cropToCache(bitmap: Bitmap, cropRect: CropRect, outputSizePx: Int = 800): Uri {
        val cropped = Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.size, cropRect.size)
        val scaled = Bitmap.createScaledBitmap(cropped, outputSizePx, outputSizePx, true)

        val dir = File(context.cacheDir, "photo_crops").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File.createTempFile("crop_", ".jpg", dir)
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return Uri.fromFile(file)
    }

    /**
     * Gera uma foto padrão (silhueta simples de carro/moto sobre fundo sólido,
     * mesma paleta do fallback de [com.flowfuel.app.core.designsystem.components.VehiclePhotoAvatar])
     * para quando o usuário pula a escolha de foto no cadastro — mantém a regra
     * "todo veículo tem foto" sem depender de asset externo. Salva em cache
     * com o mesmo padrão de [cropToCache], apagando o template anterior.
     */
    fun createTemplatePhoto(vehicleType: VehicleType, outputSizePx: Int = 800): Uri {
        val size = outputSizePx.toFloat()
        val bitmap = Bitmap.createBitmap(outputSizePx, outputSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(TEMPLATE_BACKGROUND_COLOR)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEMPLATE_SILHOUETTE_COLOR
            style = Paint.Style.FILL
        }
        if (vehicleType == VehicleType.Motorcycle) {
            drawMotorcycleSilhouette(canvas, paint, size)
        } else {
            drawCarSilhouette(canvas, paint, size)
        }

        val dir = File(context.cacheDir, "photo_templates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File.createTempFile("template_", ".jpg", dir)
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        return Uri.fromFile(file)
    }

    private fun drawCarSilhouette(canvas: Canvas, paint: Paint, size: Float) {
        val cabin = RectF(size * 0.30f, size * 0.28f, size * 0.70f, size * 0.46f)
        canvas.drawRoundRect(cabin, size * 0.05f, size * 0.05f, paint)
        val body = RectF(size * 0.14f, size * 0.42f, size * 0.86f, size * 0.62f)
        canvas.drawRoundRect(body, size * 0.06f, size * 0.06f, paint)
        canvas.drawCircle(size * 0.28f, size * 0.64f, size * 0.09f, paint)
        canvas.drawCircle(size * 0.72f, size * 0.64f, size * 0.09f, paint)
    }

    private fun drawMotorcycleSilhouette(canvas: Canvas, paint: Paint, size: Float) {
        canvas.drawCircle(size * 0.25f, size * 0.62f, size * 0.11f, paint)
        canvas.drawCircle(size * 0.75f, size * 0.62f, size * 0.11f, paint)
        val seat = RectF(size * 0.40f, size * 0.36f, size * 0.64f, size * 0.44f)
        canvas.drawRoundRect(seat, size * 0.03f, size * 0.03f, paint)

        val frame = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.035f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(size * 0.25f, size * 0.62f, size * 0.44f, size * 0.40f, frame)
        canvas.drawLine(size * 0.60f, size * 0.40f, size * 0.75f, size * 0.62f, frame)
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

    companion object {
        // Mesma paleta do fallback de VehiclePhotoAvatar (FFColors.PrimaryContainerLight / OnPrimaryContainerLight).
        private const val TEMPLATE_BACKGROUND_COLOR = 0xFFECFDF5.toInt()
        private const val TEMPLATE_SILHOUETTE_COLOR = 0xFF064E3B.toInt()

        /** Cobre os 8 valores de [ExifInterface.TAG_ORIENTATION] — TRANSPOSE/TRANSVERSE são comuns em selfies de câmera frontal. */
        internal fun orientationMatrix(orientation: Int): Matrix? = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
            ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(270f) }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply {
                setRotate(180f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply {
                setRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply {
                setRotate(270f)
                postScale(-1f, 1f)
            }
            else -> null
        }
    }
}
