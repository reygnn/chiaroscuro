package com.github.reygnn.chiaroscuro.imaging

/**
 * Strategy for what counts as "fake black" / near-black.
 *
 * - [PER_CHANNEL] — the original rule: every channel ≤ threshold. Misses
 *   low-luminance *colored* darks (deep navy, maroon, bottle-green),
 *   because one saturated channel can sit above threshold while the pixel
 *   still reads as black to the eye. Honors warm-tint.
 *
 * - [PERCEPTUAL] — luminance rule: snap when perceived brightness
 *   ([AmoledTransform.luma]) ≤ threshold, regardless of how that brightness
 *   is split across channels. A **strict superset** of [PER_CHANNEL] at the
 *   same threshold (proof: all channels ≤ T ⇒ weighted mean ≤ T), so it
 *   never matches less and additionally catches colored darks the
 *   per-channel rule cannot reach at any threshold ≤ 255. Maximizes
 *   fake-AMOLED removal; warm-tint has no meaning here and is ignored.
 */
internal enum class DetectionMode { PER_CHANNEL, PERCEPTUAL }

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

    // Integer luma weights (BT.709-ish), scaled to sum 256 so the divide is
    // a shift. luma(white)=255, luma(black)=0. Green dominates, which is why
    // green/teal darks reach "perceptually black" far sooner than the
    // per-channel rule admits.
    private const val LUMA_R = 54
    private const val LUMA_G = 183
    private const val LUMA_B = 19

    /**
     * Perceived brightness of a pixel in 0..255: (54·R + 183·G + 19·B) >> 8.
     * Alpha is ignored. Pure.
     */
    fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (LUMA_R * r + LUMA_G * g + LUMA_B * b) shr 8
    }

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
     * True iff the pixel is perceptually black at [threshold]: its [luma] is
     * at or below the threshold and it is not already pure black.
     *
     * Strict superset of [isNearBlack] (non-warm) at the same threshold, and
     * the right primitive for "remove as much fake-AMOLED as possible,
     * motif change acceptable": it also blackens deep saturated darks whose
     * single bright channel keeps the per-channel rule from ever firing.
     *
     * Note: at threshold 0 this still removes truly-invisible colored darks
     * (e.g. (0,0,13) → luma 0) — i.e. everything the eye cannot tell from
     * black — whereas the per-channel rule at 0 matches nothing.
     *
     * Alpha is ignored.
     */
    fun isPerceptualBlack(argb: Int, threshold: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        if (r == 0 && g == 0 && b == 0) return false
        return (LUMA_R * r + LUMA_G * g + LUMA_B * b) shr 8 <= threshold
    }

    /**
     * Unified fake-black predicate dispatching on [mode]. [warmMode] only
     * affects [DetectionMode.PER_CHANNEL]; it is meaningless under
     * [DetectionMode.PERCEPTUAL] and ignored there.
     */
    fun isFakeBlack(argb: Int, threshold: Int, mode: DetectionMode, warmMode: Boolean): Boolean =
        when (mode) {
            DetectionMode.PER_CHANNEL -> isNearBlack(argb, threshold, warmMode)
            DetectionMode.PERCEPTUAL -> isPerceptualBlack(argb, threshold)
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
     * with [COLOR_BLACK]. Input is not mutated. (Per-channel rule.)
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
     * Mode-aware correction. Every pixel that [isFakeBlack] under [mode] is
     * replaced with [COLOR_BLACK]. Input is not mutated. Use
     * [DetectionMode.PERCEPTUAL] for maximum fake-AMOLED removal.
     */
    fun applyCorrection(
        pixels: IntArray,
        threshold: Int,
        mode: DetectionMode,
        warmMode: Boolean,
    ): IntArray {
        val out = pixels.copyOf()
        for (i in out.indices) {
            if (isFakeBlack(out[i], threshold, mode, warmMode)) {
                out[i] = COLOR_BLACK
            }
        }
        return out
    }

    /**
     * Returns the analysis overlay (every matched pixel marked red) plus
     * three counts that fully describe the near-black population. (Per-channel
     * rule; see the warm/non-warm semantics below.)
     *
     *  - [AmoledAnalysis.nearBlackCount] — pixels that the **current**
     *    settings (threshold + warmMode) flag and that appear red in
     *    the overlay. Identical to what [applyCorrection] would blacken.
     *
     *  - [AmoledAnalysis.warmNearBlackCount] — near-black pixels with a
     *    warm bias (`R − B > WARM_TINT_MIN_DELTA`), **independent of
     *    warmMode**.
     *
     *  - [AmoledAnalysis.nonWarmNearBlackCount] — near-black pixels
     *    without a warm bias (neutral grays, cool blues), again
     *    independent of warmMode.
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
     * Mode-aware analysis. For [DetectionMode.PER_CHANNEL] this delegates to
     * the warm-aware [analyze] above. For [DetectionMode.PERCEPTUAL] the
     * warm/non-warm split is not meaningful (the rule is hue-agnostic), so
     * both breakdown counts are reported as 0 and only [AmoledAnalysis.nearBlackCount]
     * carries information — the count of perceptually-black pixels the
     * overlay marked red, identical to what the perceptual [applyCorrection]
     * would blacken.
     *
     * Input is not mutated.
     */
    fun analyze(
        pixels: IntArray,
        threshold: Int,
        mode: DetectionMode,
        warmMode: Boolean,
    ): AmoledAnalysis {
        if (mode == DetectionMode.PER_CHANNEL) return analyze(pixels, threshold, warmMode)
        val out = pixels.copyOf()
        var matched = 0
        for (i in out.indices) {
            if (isPerceptualBlack(out[i], threshold)) {
                out[i] = COLOR_RED
                matched++
            }
        }
        return AmoledAnalysis(
            pixels = out,
            nearBlackCount = matched,
            warmNearBlackCount = 0,
            nonWarmNearBlackCount = 0,
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
