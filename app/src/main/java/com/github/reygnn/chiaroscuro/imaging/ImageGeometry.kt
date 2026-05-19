package com.github.reygnn.chiaroscuro.imaging

import kotlin.math.roundToInt

/**
 * Pure geometric math for mapping the fixed-center preview rectangle
 * from screen space back to image-pixel space.
 *
 * Context: the editor canvas draws the source bitmap centered with an
 * aspect-fit baseScale, then applies a user-controlled zoom transform
 * (scale around canvas center + translation). The preview rectangle
 * itself is drawn OUTSIDE that transform, fixed at canvas center — the
 * user pans the image under a stationary rectangle. At export time
 * we reverse the transform to find where the user aimed the rectangle
 * in the original image.
 *
 * All inputs are primitive floats/ints so the function is trivially
 * JVM-testable without pulling in Compose geometry types.
 */
internal object ImageGeometry {

    /**
     * Compute the top-left corner of the preview rectangle in original
     * image pixel coordinates.
     *
     * Conventions match the Compose canvas: origin top-left, y-axis
     * pointing down, zoom scale applied around canvas center, zoom
     * translation applied after scale.
     *
     * Returns integer coordinates via [roundToInt]: the export pipeline
     * draws at integer image positions, so the fractional component has
     * to collapse somewhere. We round rather than truncate to keep the
     * worst-case error symmetric (±0.5px instead of −1..0px systematic
     * bias toward the top-left). The difference is invisible for the
     * primary watermark-cover workflow but is the right default; if a
     * future feature needs sub-pixel placement it should change the
     * return type, not the rounding mode.
     */
    fun computeRectOriginInImage(
        imageWidth: Int,
        imageHeight: Int,
        canvasWidth: Float,
        canvasHeight: Float,
        rectWidth: Int,
        rectHeight: Int,
        zoomScale: Float,
        zoomOffsetX: Float,
        zoomOffsetY: Float,
    ): RectOrigin {
        val baseScale = minOf(canvasWidth / imageWidth, canvasHeight / imageHeight)
        val baseOffX = (canvasWidth - imageWidth * baseScale) / 2f
        val baseOffY = (canvasHeight - imageHeight * baseScale) / 2f

        val rectScreenW = rectWidth * baseScale * zoomScale
        val rectScreenH = rectHeight * baseScale * zoomScale
        val screenX = canvasWidth / 2f - rectScreenW / 2f
        val screenY = canvasHeight / 2f - rectScreenH / 2f

        val canvasX = (screenX - zoomOffsetX - canvasWidth / 2f) / zoomScale + canvasWidth / 2f
        val canvasY = (screenY - zoomOffsetY - canvasHeight / 2f) / zoomScale + canvasHeight / 2f

        return RectOrigin(
            x = ((canvasX - baseOffX) / baseScale).roundToInt(),
            y = ((canvasY - baseOffY) / baseScale).roundToInt(),
        )
    }

    /**
     * Inverse of [computeRectOriginInImage]: compute the zoom offset that
     * positions the canvas-centered preview rectangle so its top-left lands
     * on the requested image-space coordinates [targetImageX], [targetImageY].
     *
     * Used by the editor to "jump" the rectangle onto a remembered sparkle
     * position (rectX/rectY in UserPreferences) when the user re-enables the
     * cover-up.
     *
     * Round-trip invariant: feeding the returned offset back into
     * [computeRectOriginInImage] with the same other arguments recovers the
     * supplied target (modulo the `roundToInt()` rounding in the forward path).
     */
    fun computeZoomOffsetForRectAt(
        imageWidth: Int,
        imageHeight: Int,
        canvasWidth: Float,
        canvasHeight: Float,
        rectWidth: Int,
        rectHeight: Int,
        targetImageX: Float,
        targetImageY: Float,
        zoomScale: Float,
    ): ZoomOffset {
        val baseScale = minOf(canvasWidth / imageWidth, canvasHeight / imageHeight)
        val baseOffX = (canvasWidth - imageWidth * baseScale) / 2f
        val baseOffY = (canvasHeight - imageHeight * baseScale) / 2f
        val offsetX = zoomScale *
            (canvasWidth / 2f - baseOffX - baseScale * (targetImageX + rectWidth / 2f))
        val offsetY = zoomScale *
            (canvasHeight / 2f - baseOffY - baseScale * (targetImageY + rectHeight / 2f))
        return ZoomOffset(offsetX, offsetY)
    }
}

internal data class RectOrigin(val x: Int, val y: Int)

internal data class ZoomOffset(val x: Float, val y: Float)