package com.github.reygnn.chiaroscuro.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Thin Android adapter around [AmoledTransform] and [ImageGeometry].
 *
 * Converts between Bitmap and IntArray, delegates the logic to the
 * pure kernels, wraps the result back into a Bitmap.
 *
 * ─── Testing policy ──────────────────────────────────────────────
 *
 * This file is intentionally NOT covered by unit tests.
 *
 * Rationale: Every function here is a three-step adapter:
 *   1. Bitmap → IntArray  (via Bitmap.getPixels / Bitmap.copy)
 *   2. Delegate to AmoledTransform / ImageGeometry  (fully unit-tested)
 *   3. IntArray → Bitmap  (via Bitmap.setPixels)
 *
 * Steps 1 and 3 are pure Android framework calls whose correctness is
 * guaranteed by the platform. Testing them would require Robolectric
 * or instrumented tests — both of which this project avoids in favor
 * of a fast JVM-only test loop.
 *
 * The invariant "adapter logic stays trivial" is enforced by review:
 * any non-trivial transformation added here must be extracted into
 * AmoledTransform or ImageGeometry (where it becomes unit-testable)
 * before the PR is merged.
 */
internal object ImageProcessing {

    /** Returns a new bitmap with near-black pixels replaced by black. Source not mutated. */
    fun applyAmoledCorrection(
        source: Bitmap,
        threshold: Int,
        warmMode: Boolean,
    ): Bitmap {
        val pixels = source.toArgbArray()
        val transformed = AmoledTransform.applyCorrection(pixels, threshold, warmMode)
        return source.copiedWith(transformed)
    }

    /** Returns overlay bitmap (near-black marked red) + near-black count. Source not mutated. */
    fun analyzeAmoled(
        source: Bitmap,
        threshold: Int,
        warmMode: Boolean,
    ): AmoledAnalysisBitmap {
        val pixels = source.toArgbArray()
        val analysis = AmoledTransform.analyze(pixels, threshold, warmMode)
        return AmoledAnalysisBitmap(
            bitmap = source.copiedWith(analysis.pixels),
            nearBlackCount = analysis.nearBlackCount,
        )
    }

    /** Returns a new bitmap with pure-black pixels replaced by fully transparent. Source not mutated. */
    fun blackToTransparent(source: Bitmap): Bitmap {
        val pixels = source.toArgbArray()
        val transformed = AmoledTransform.blackToTransparent(pixels)
        return source.copiedWith(transformed)
    }

    /**
     * Draws a filled black rectangle onto [bitmap] at the given image
     * coordinates. Mutates [bitmap] in place — caller owns a working copy.
     */
    fun drawBlackRect(
        bitmap: Bitmap,
        imgX: Int,
        imgY: Int,
        rectWidth: Int,
        rectHeight: Int,
    ) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = AmoledTransform.COLOR_BLACK }
        canvas.drawRect(
            imgX.toFloat(),
            imgY.toFloat(),
            (imgX + rectWidth).toFloat(),
            (imgY + rectHeight).toFloat(),
            paint,
        )
    }

    private fun Bitmap.toArgbArray(): IntArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }

    private fun Bitmap.copiedWith(pixels: IntArray): Bitmap {
        val result = copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}

/** Return type for [ImageProcessing.analyzeAmoled]: bitmap + count. */
internal class AmoledAnalysisBitmap(
    val bitmap: Bitmap,
    val nearBlackCount: Int,
)