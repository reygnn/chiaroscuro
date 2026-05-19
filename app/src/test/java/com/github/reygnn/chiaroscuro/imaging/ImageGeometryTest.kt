package com.github.reygnn.chiaroscuro.imaging

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageGeometryTest {

    @Test
    fun `square image filling square canvas at identity zoom centers the rect`() {
        // 100x100 image fills 200x200 canvas at baseScale 2, no pan, no zoom.
        // Rect 20x20 at canvas center maps to image center minus half-rect.
        val origin = ImageGeometry.computeRectOriginInImage(
            imageWidth = 100, imageHeight = 100,
            canvasWidth = 200f, canvasHeight = 200f,
            rectWidth = 20, rectHeight = 20,
            zoomScale = 1f, zoomOffsetX = 0f, zoomOffsetY = 0f,
        )
        assertEquals(RectOrigin(40, 40), origin)
    }

    @Test
    fun `letterboxed non-square image is mapped through the vertical offset`() {
        // 200x100 image in 200x200 canvas at baseScale 1, letterboxed top/bottom 50px each.
        // Rect 20x20 ends up horizontally centered in image, vertically centered too.
        val origin = ImageGeometry.computeRectOriginInImage(
            imageWidth = 200, imageHeight = 100,
            canvasWidth = 200f, canvasHeight = 200f,
            rectWidth = 20, rectHeight = 20,
            zoomScale = 1f, zoomOffsetX = 0f, zoomOffsetY = 0f,
        )
        assertEquals(RectOrigin(90, 40), origin)
    }

    @Test
    fun `panning the image right moves the rect position left in image space`() {
        // 100x100 image, 200x200 canvas, baseScale 2, rect 20x20.
        // Pan offset +50px right → stationary rect sits further LEFT in image.
        val origin = ImageGeometry.computeRectOriginInImage(
            imageWidth = 100, imageHeight = 100,
            canvasWidth = 200f, canvasHeight = 200f,
            rectWidth = 20, rectHeight = 20,
            zoomScale = 1f, zoomOffsetX = 50f, zoomOffsetY = 0f,
        )
        assertEquals(RectOrigin(15, 40), origin)
    }

    @Test
    fun `zoom around center without pan leaves the rect position unchanged`() {
        // Zoom scales around canvas center; without pan, the center point
        // in image space is invariant under zoom. Rect is stationary at
        // canvas center, so image-space origin should match the identity case.
        val baseline = ImageGeometry.computeRectOriginInImage(
            imageWidth = 100, imageHeight = 100,
            canvasWidth = 200f, canvasHeight = 200f,
            rectWidth = 20, rectHeight = 20,
            zoomScale = 1f, zoomOffsetX = 0f, zoomOffsetY = 0f,
        )
        val zoomedIn = ImageGeometry.computeRectOriginInImage(
            imageWidth = 100, imageHeight = 100,
            canvasWidth = 200f, canvasHeight = 200f,
            rectWidth = 20, rectHeight = 20,
            zoomScale = 2f, zoomOffsetX = 0f, zoomOffsetY = 0f,
        )
        val zoomedOut = ImageGeometry.computeRectOriginInImage(
            imageWidth = 100, imageHeight = 100,
            canvasWidth = 200f, canvasHeight = 200f,
            rectWidth = 20, rectHeight = 20,
            zoomScale = 0.5f, zoomOffsetX = 0f, zoomOffsetY = 0f,
        )
        assertEquals(baseline, zoomedIn)
        assertEquals(baseline, zoomedOut)
    }

    @Test
    fun `zoom combined with pan composes both effects`() {
        // At zoom 2x, a pan of 40px in SCREEN space corresponds to 20px in
        // image space (half, because zoom is doubled). Pan left (negative)
        // moves the stationary rect to a larger x in image space.
        val origin = ImageGeometry.computeRectOriginInImage(
            imageWidth = 100, imageHeight = 100,
            canvasWidth = 200f, canvasHeight = 200f,
            rectWidth = 20, rectHeight = 20,
            zoomScale = 2f, zoomOffsetX = -40f, zoomOffsetY = 0f,
        )
        assertEquals(RectOrigin(50, 40), origin)
    }

    @Test
    fun `inverse zero offset corresponds to rect at image center`() {
        // A 100x100 image rendered into a 200x200 canvas centers it; with
        // a 20x20 rect and zoomScale=1, the canvas-center rect lands at
        // image-space (40, 40). Asking the inverse to land the rect THERE
        // must yield a zero offset.
        val zo = ImageGeometry.computeZoomOffsetForRectAt(
            imageWidth = 100, imageHeight = 100,
            canvasWidth = 200f, canvasHeight = 200f,
            rectWidth = 20, rectHeight = 20,
            targetImageX = 40f, targetImageY = 40f,
            zoomScale = 1f,
        )
        assertEquals(0f, zo.x)
        assertEquals(0f, zo.y)
    }

    @Test
    fun `forward then inverse round-trips at identity zoom`() {
        // Pick an arbitrary target inside the image; the offset returned
        // by the inverse must, when fed back through the forward function,
        // recover the same target (up to the forward path's roundToInt()).
        val target = Pair(73f, 12f)
        val zo = ImageGeometry.computeZoomOffsetForRectAt(
            imageWidth = 200, imageHeight = 100,
            canvasWidth = 400f, canvasHeight = 400f,
            rectWidth = 25, rectHeight = 25,
            targetImageX = target.first, targetImageY = target.second,
            zoomScale = 1f,
        )
        val origin = ImageGeometry.computeRectOriginInImage(
            imageWidth = 200, imageHeight = 100,
            canvasWidth = 400f, canvasHeight = 400f,
            rectWidth = 25, rectHeight = 25,
            zoomScale = 1f, zoomOffsetX = zo.x, zoomOffsetY = zo.y,
        )
        assertEquals(target.first.toInt(), origin.x)
        assertEquals(target.second.toInt(), origin.y)
    }

    @Test
    fun `forward then inverse round-trips under zoom`() {
        // Same round-trip under a non-identity zoom.
        val zo = ImageGeometry.computeZoomOffsetForRectAt(
            imageWidth = 300, imageHeight = 300,
            canvasWidth = 600f, canvasHeight = 600f,
            rectWidth = 30, rectHeight = 30,
            targetImageX = 200f, targetImageY = 50f,
            zoomScale = 2.5f,
        )
        val origin = ImageGeometry.computeRectOriginInImage(
            imageWidth = 300, imageHeight = 300,
            canvasWidth = 600f, canvasHeight = 600f,
            rectWidth = 30, rectHeight = 30,
            zoomScale = 2.5f, zoomOffsetX = zo.x, zoomOffsetY = zo.y,
        )
        assertEquals(200, origin.x)
        assertEquals(50, origin.y)
    }

    @Test
    fun `fractional position is rounded to nearest pixel not truncated`() {
        // Introduce a clearly fractional canvasX via a sub-integer pan offset.
        // 100x100 image in 200x200 canvas at baseScale=2 (exact). Rect 20x20.
        // zoomOffsetX = 0.6 → canvasX = 80 - 0.6 - 100 + 100 = 79.4.
        // Image-space x = 79.4 / 2 = 39.7.
        //
        // Truncation: 39  (one pixel TOO FAR LEFT — the original bug)
        // Rounding:   40  (correct nearest pixel)
        //
        // The fractional component is far from 0.5 so Float precision
        // jitter cannot flip the outcome — the test pins behavior, not
        // arithmetic.
        val origin = ImageGeometry.computeRectOriginInImage(
            imageWidth = 100, imageHeight = 100,
            canvasWidth = 200f, canvasHeight = 200f,
            rectWidth = 20, rectHeight = 20,
            zoomScale = 1f, zoomOffsetX = 0.6f, zoomOffsetY = 0f,
        )
        assertEquals(40, origin.x)
    }
}