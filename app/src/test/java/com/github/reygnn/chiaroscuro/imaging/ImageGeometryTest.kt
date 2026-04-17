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
}