package com.flowfuel.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class CropMathTest {

    @Test
    fun `minScale covers the viewport when bitmap is wider than tall`() {
        val scale = CropMath.minScale(bitmapWidthPx = 200, bitmapHeightPx = 100, viewportSizePx = 100)
        assertEquals(1.0f, scale, 0.001f)
    }

    @Test
    fun `minScale covers the viewport when bitmap is taller than wide`() {
        val scale = CropMath.minScale(bitmapWidthPx = 100, bitmapHeightPx = 200, viewportSizePx = 100)
        assertEquals(1.0f, scale, 0.001f)
    }

    @Test
    fun `minScale scales up a bitmap smaller than the viewport`() {
        val scale = CropMath.minScale(bitmapWidthPx = 50, bitmapHeightPx = 50, viewportSizePx = 200)
        assertEquals(4.0f, scale, 0.001f)
    }

    @Test
    fun `computeCropRect covers the full bitmap when scale is exactly cover-fit and there is no pan`() {
        val rect = CropMath.computeCropRect(
            bitmapWidthPx = 1000,
            bitmapHeightPx = 1000,
            viewportSizePx = 100,
            scale = 0.1f,
            offsetX = 0f,
            offsetY = 0f,
        )
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(1000, rect.size)
    }

    @Test
    fun `computeCropRect shrinks and centers the crop when zoomed in without pan`() {
        val rect = CropMath.computeCropRect(
            bitmapWidthPx = 1000,
            bitmapHeightPx = 1000,
            viewportSizePx = 100,
            scale = 0.2f,
            offsetX = 0f,
            offsetY = 0f,
        )
        assertEquals(250, rect.left)
        assertEquals(250, rect.top)
        assertEquals(500, rect.size)
    }

    @Test
    fun `computeCropRect moves the crop window opposite to a positive pan`() {
        val rect = CropMath.computeCropRect(
            bitmapWidthPx = 1000,
            bitmapHeightPx = 1000,
            viewportSizePx = 100,
            scale = 0.2f,
            offsetX = 50f,
            offsetY = 50f,
        )
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(500, rect.size)
    }

    @Test
    fun `computeCropRect clamps an out-of-range crop origin back into bitmap bounds`() {
        val rect = CropMath.computeCropRect(
            bitmapWidthPx = 1000,
            bitmapHeightPx = 1000,
            viewportSizePx = 100,
            scale = 0.2f,
            offsetX = 999_999f,
            offsetY = -999_999f,
        )
        assertEquals(0, rect.left)
        assertEquals(500, rect.top)
        assertEquals(500, rect.size)
    }

    @Test
    fun `clampOffset forbids any pan when the image exactly fills the viewport`() {
        val (x, y) = CropMath.clampOffset(
            bitmapWidthPx = 1000,
            bitmapHeightPx = 1000,
            viewportSizePx = 100,
            scale = 0.1f,
            offsetX = 40f,
            offsetY = 40f,
        )
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
    }

    @Test
    fun `clampOffset allows pan up to half the extra displayed size when zoomed in`() {
        val (x, y) = CropMath.clampOffset(
            bitmapWidthPx = 1000,
            bitmapHeightPx = 1000,
            viewportSizePx = 100,
            scale = 0.2f,
            offsetX = 999f,
            offsetY = -999f,
        )
        assertEquals(50f, x, 0.001f)
        assertEquals(-50f, y, 0.001f)
    }
}
