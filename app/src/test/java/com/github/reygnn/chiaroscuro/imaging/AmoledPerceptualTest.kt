package com.github.reygnn.chiaroscuro.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the perceptual (luminance) detection path added alongside
 * the original per-channel rule. The headline invariant is the strict
 * superset property and the colored-dark cases the per-channel rule can
 * never reach.
 */
class AmoledPerceptualTest {

    // ── luma ─────────────────────────────────────────────────────

    @Test
    fun `luma of pure black is zero`() {
        assertEquals(0, AmoledTransform.luma(argb(0, 0, 0)))
    }

    @Test
    fun `luma of pure white is 255`() {
        assertEquals(255, AmoledTransform.luma(argb(255, 255, 255)))
    }

    @Test
    fun `luma of mid grey is mid`() {
        assertEquals(128, AmoledTransform.luma(argb(128, 128, 128)))
    }

    @Test
    fun `green dominates luma`() {
        // Same channel value in green weighs far more than in blue.
        assertTrue(AmoledTransform.luma(argb(0, 40, 0)) > AmoledTransform.luma(argb(0, 0, 40)))
    }

    // ── isPerceptualBlack — basics ───────────────────────────────

    @Test
    fun `pure black is not perceptual black by convention`() {
        assertFalse(AmoledTransform.isPerceptualBlack(argb(0, 0, 0), threshold = 16))
    }

    @Test
    fun `pure white is never perceptual black`() {
        assertFalse(AmoledTransform.isPerceptualBlack(argb(255, 255, 255), threshold = 50))
    }

    @Test
    fun `neutral pixel at threshold is perceptual black`() {
        // luma(5,5,5) == 5
        assertTrue(AmoledTransform.isPerceptualBlack(argb(5, 5, 5), threshold = 5))
        assertFalse(AmoledTransform.isPerceptualBlack(argb(5, 5, 5), threshold = 4))
    }

    @Test
    fun `invisible colored dark is removed even at threshold 0`() {
        // luma(0,0,13) == (19*13)>>8 == 0, but it is not pure black.
        assertTrue(AmoledTransform.isPerceptualBlack(argb(0, 0, 13), threshold = 0))
    }

    // ── the point: colored darks the per-channel rule cannot reach ─

    @Test
    fun `deep navy is perceptual black but never per-channel near-black`() {
        val navy = argb(0, 0, 80) // luma == (19*80)>>8 == 5
        // Per-channel rule misses it at any threshold up to the ceiling…
        assertFalse(AmoledTransform.isNearBlack(navy, threshold = 50, warmMode = false))
        // …perceptual rule catches it comfortably.
        assertTrue(AmoledTransform.isPerceptualBlack(navy, threshold = 16))
    }

    @Test
    fun `teal gradient pixel crosses earlier under perceptual rule`() {
        val teal = argb(22, 30, 33) // luma == 28, max channel == 33
        // Per-channel needs threshold >= 33; at 30 it is not matched.
        assertFalse(AmoledTransform.isNearBlack(teal, threshold = 30, warmMode = false))
        // Perceptual matches at 30 because luma is 28.
        assertTrue(AmoledTransform.isPerceptualBlack(teal, threshold = 30))
    }

    // ── strict superset invariant ────────────────────────────────

    @Test
    fun `per-channel match implies perceptual match at same threshold`() {
        val t = 24
        for (r in 0..t) for (g in 0..t) for (b in 0..t) {
            val p = argb(r, g, b)
            if (AmoledTransform.isNearBlack(p, t, warmMode = false)) {
                assertTrue(
                    "luma=${AmoledTransform.luma(p)} should be <= $t for ($r,$g,$b)",
                    AmoledTransform.isPerceptualBlack(p, t),
                )
            }
        }
    }

    // ── apply / analyze wiring through the mode enum ─────────────

    @Test
    fun `perceptual apply blackens navy that per-channel apply leaves`() {
        val pixels = intArrayOf(argb(0, 0, 80))
        val perCh = AmoledTransform.applyCorrection(
            pixels, threshold = 50, mode = DetectionMode.PER_CHANNEL, warmMode = false,
        )
        val percept = AmoledTransform.applyCorrection(
            pixels, threshold = 16, mode = DetectionMode.PERCEPTUAL, warmMode = false,
        )
        assertEquals(argb(0, 0, 80), perCh[0])            // untouched by old rule
        assertEquals(AmoledTransform.COLOR_BLACK, percept[0]) // removed by new rule
    }

    @Test
    fun `perceptual analyze reports zero warm breakdown`() {
        val pixels = intArrayOf(argb(0, 0, 80), argb(10, 8, 6), argb(200, 200, 200))
        val a = AmoledTransform.analyze(
            pixels, threshold = 16, mode = DetectionMode.PERCEPTUAL, warmMode = false,
        )
        assertEquals(2, a.nearBlackCount) // navy + warm-ish dark, not the bright grey
        assertEquals(0, a.warmNearBlackCount)
        assertEquals(0, a.nonWarmNearBlackCount)
    }

    @Test
    fun `mode per-channel analyze matches the legacy overload`() {
        val pixels = intArrayOf(argb(3, 3, 3), argb(10, 4, 2), argb(0, 0, 0))
        val viaMode = AmoledTransform.analyze(
            pixels, threshold = 12, mode = DetectionMode.PER_CHANNEL, warmMode = false,
        )
        val legacy = AmoledTransform.analyze(pixels, threshold = 12, warmMode = false)
        assertEquals(legacy.nearBlackCount, viaMode.nearBlackCount)
    }

    // ── helper ───────────────────────────────────────────────────

    private fun argb(r: Int, g: Int, b: Int, a: Int = 0xFF): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b
}
