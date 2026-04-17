package com.github.reygnn.chiaroscuro.imaging

/**
 * Pure AMOLED-analysis and transform kernels over ARGB-packed pixel arrays.
 *
 * Android-free: inputs and outputs are IntArray in the same ARGB8888
 * packing that android.graphics.Bitmap uses
 * (alpha << 24 | red << 16 | green << 8 | blue). This lets the pixel
 * logic be unit-tested on the JVM without Robolectric or instrumented
 * tests.
 *
 * Constant parity:
 *   [COLOR_BLACK]       == android.graphics.Color.BLACK       (0xFF000000)
 *   [COLOR_RED]         == android.graphics.Color.RED         (0xFFFF0000)
 *   [COLOR_TRANSPARENT] == android.graphics.Color.TRANSPARENT (0x00000000)
 *
 * The equivalence is by definition of ARGB packing and asserted via
 * an instrumented test if/when one exists; it cannot be verified in
 * a JVM unit test because android.graphics.Color returns 0 under
 * unitTests.isReturnDefaultValues = true.
 */
internal object AmoledTransform {

    /** Opaque pure black: 0xFF000000 as signed Int. */
    val COLOR_BLACK: Int = 0xFF000000.toInt()

    /** Opaque pure red: 0xFFFF0000 as signed Int. */
    val COLOR_RED: Int = 0xFFFF0000.toInt()

    /** Fully transparent: 0x00000000. */
    const val COLOR_TRANSPARENT: Int = 0

    /** In warm-tint mode, (red - blue) must strictly exceed this value. */
    const val WARM_TINT_MIN_DELTA: Int = 3

    /**
     * True iff the given pixel is "near black" under the current settings.
     *
     * A pixel is near-black when all three color channels are ≤ threshold
     * AND at least one channel is non-zero (pure black is excluded so it
     * does not get counted as needing correction).
     *
     * In warm-tint mode the pixel additionally must exhibit a warm bias,
     * i.e. (red - blue) strictly greater than [WARM_TINT_MIN_DELTA].
     *
     * Alpha is ignored: callers normalize to ARGB_8888 before invoking.
     */
    fun isNearBlack(argb: Int, threshold: Int, warmMode: Boolean): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val nearBlack = r <= threshold && g <= threshold && b <= threshold &&
                (r > 0 || g > 0 || b > 0)
        return if (warmMode) nearBlack && (r - b) > WARM_TINT_MIN_DELTA else nearBlack
    }

    /**
     * Returns a new pixel array where every near-black pixel is replaced
     * with [COLOR_BLACK]. Input is not mutated.
     */
    fun applyCorrection(pixels: IntArray, threshold: Int, warmMode: Boolean): IntArray {
        val out = pixels.copyOf()
        for (i in out.indices) {
            if (isNearBlack(out[i], threshold, warmMode)) {
                out[i] = COLOR_BLACK
            }
        }
        return out
    }

    /**
     * Returns the analysis overlay: every near-black pixel marked red,
     * paired with the count of marked pixels. Input is not mutated.
     */
    fun analyze(pixels: IntArray, threshold: Int, warmMode: Boolean): AmoledAnalysis {
        val out = pixels.copyOf()
        var count = 0
        for (i in out.indices) {
            if (isNearBlack(out[i], threshold, warmMode)) {
                out[i] = COLOR_RED
                count++
            }
        }
        return AmoledAnalysis(out, count)
    }

    /**
     * Returns a new pixel array where every pixel exactly equal to
     * [COLOR_BLACK] is replaced with [COLOR_TRANSPARENT]; other pixels
     * are preserved verbatim. Input is not mutated.
     */
    fun blackToTransparent(pixels: IntArray): IntArray {
        val out = pixels.copyOf()
        for (i in out.indices) {
            if (out[i] == COLOR_BLACK) out[i] = COLOR_TRANSPARENT
        }
        return out
    }
}

/** Return type for [AmoledTransform.analyze]: overlay array + count. */
internal class AmoledAnalysis(
    val pixels: IntArray,
    val nearBlackCount: Int,
)