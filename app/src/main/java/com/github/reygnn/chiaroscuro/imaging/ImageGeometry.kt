package com.github.reygnn.chiaroscuro.imaging

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
     * Returns integer coordinates: the export pipeline draws at integer
     * image positions, so fractional values would be discarded anyway.
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
            x = ((canvasX - baseOffX) / baseScale).toInt(),
            y = ((canvasY - baseOffY) / baseScale).toInt(),
        )
    }
}

internal data class RectOrigin(val x: Int, val y: Int)