package com.flowfuel.app.core.media

import kotlin.math.max
import kotlin.math.roundToInt

/** Região quadrada (em pixels da imagem original) visível dentro do viewport circular de recorte. */
data class CropRect(val left: Int, val top: Int, val size: Int)

/**
 * Matemática pura (sem dependência de Android/Compose) por trás do [PhotoCropDialog]:
 * converte estado de pan/zoom em tela para a região da imagem original a recortar.
 */
object CropMath {

    /** Menor escala (imagem-px -> tela-px) que garante que o bitmap cubra todo o viewport, sem espaços vazios. */
    fun minScale(bitmapWidthPx: Int, bitmapHeightPx: Int, viewportSizePx: Int): Float =
        max(
            viewportSizePx.toFloat() / bitmapWidthPx.toFloat(),
            viewportSizePx.toFloat() / bitmapHeightPx.toFloat(),
        )

    /** Limita o pan (em px de tela) para que a imagem nunca deixe espaço vazio dentro do viewport. */
    fun clampOffset(
        bitmapWidthPx: Int,
        bitmapHeightPx: Int,
        viewportSizePx: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): Pair<Float, Float> {
        val displayedW = bitmapWidthPx * scale
        val displayedH = bitmapHeightPx * scale
        val maxOffsetX = ((displayedW - viewportSizePx) / 2f).coerceAtLeast(0f)
        val maxOffsetY = ((displayedH - viewportSizePx) / 2f).coerceAtLeast(0f)
        return offsetX.coerceIn(-maxOffsetX, maxOffsetX) to offsetY.coerceIn(-maxOffsetY, maxOffsetY)
    }

    /** Converte a transform de tela (escala + pan) na região quadrada (px da imagem original) visível no viewport. */
    fun computeCropRect(
        bitmapWidthPx: Int,
        bitmapHeightPx: Int,
        viewportSizePx: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): CropRect {
        val displayedW = bitmapWidthPx * scale
        val displayedH = bitmapHeightPx * scale
        val imageLeftOnScreen = viewportSizePx / 2f - displayedW / 2f + offsetX
        val imageTopOnScreen = viewportSizePx / 2f - displayedH / 2f + offsetY

        val cropSize = (viewportSizePx / scale).roundToInt()
            .coerceIn(1, minOf(bitmapWidthPx, bitmapHeightPx))
        val left = (-imageLeftOnScreen / scale).roundToInt()
            .coerceIn(0, bitmapWidthPx - cropSize)
        val top = (-imageTopOnScreen / scale).roundToInt()
            .coerceIn(0, bitmapHeightPx - cropSize)

        return CropRect(left, top, cropSize)
    }
}
