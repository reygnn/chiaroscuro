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
     * True iff the pixel has a warm color bias, i.e. (red - blue) strictly
     * greater than [WARM_TINT_MIN_DELTA]. Does NOT check near-blackness —
     * callers gate on [isNearBlack] first when they only care about warm
     * darks.
     *
     * Exists so [analyze] can categorize near-black pixels as warm vs
     * non-warm in a single pass without re-running the full near-black
     * arithmetic.
     */
    fun isWarmBiased(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val b = argb and 0xFF
        return (r - b) > WARM_TINT_MIN_DELTA
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
     * Returns the analysis overlay (every matched pixel marked red) plus
     * three counts that fully describe the near-black population:
     *
     *  - [AmoledAnalysis.nearBlackCount] — pixels that the **current**
     *    settings (threshold + warmMode) flag and that appear red in
     *    the overlay. Identical to what [applyCorrection] would blacken.
     *
     *  - [AmoledAnalysis.warmNearBlackCount] — near-black pixels with a
     *    warm bias (`R − B > WARM_TINT_MIN_DELTA`), **independent of
     *    warmMode**. Counted on the raw near-black classification, so
     *    the value is meaningful whether warmMode is currently on or off.
     *
     *  - [AmoledAnalysis.nonWarmNearBlackCount] — near-black pixels
     *    without a warm bias (neutral grays, cool blues), again
     *    independent of warmMode.
     *
     * The two breakdown counts let the UI recommend Warm Tint based on
     * the actual content of the loaded image: a mixed population (both
     * counts non-zero) is the canonical case where Warm Tint protects
     * non-warm shadows from blanket blackening.
     *
     * Input is not mutated.
     */
    fun analyze(pixels: IntArray, threshold: Int, warmMode: Boolean): AmoledAnalysis {
        val out = pixels.copyOf()
        var matched = 0
        var warm = 0
        var nonWarm = 0
        for (i in out.indices) {
            if (!isNearBlack(out[i], threshold, warmMode = false)) continue
            val isWarm = isWarmBiased(out[i])
            if (isWarm) warm++ else nonWarm++
            if (!warmMode || isWarm) {
                out[i] = COLOR_RED
                matched++
            }
        }
        return AmoledAnalysis(
            pixels = out,
            nearBlackCount = matched,
            warmNearBlackCount = warm,
            nonWarmNearBlackCount = nonWarm,
        )
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

/**
 * Return type for [AmoledTransform.analyze]: overlay array plus three
 * counts that describe the near-black population. See [AmoledTransform.analyze]
 * for the semantics of each count.
 */
internal class AmoledAnalysis(
    val pixels: IntArray,
    val nearBlackCount: Int,
    val warmNearBlackCount: Int,
    val nonWarmNearBlackCount: Int,
)