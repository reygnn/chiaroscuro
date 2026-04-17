package com.github.reygnn.chiaroscuro.imaging

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmoledTransformTest {

    // ── isNearBlack ──────────────────────────────────────────────

    @Test
    fun `pure black is not near-black by convention`() {
        assertFalse(AmoledTransform.isNearBlack(argb(0, 0, 0), threshold = 10, warmMode = false))
    }

    @Test
    fun `pure white is never near-black`() {
        assertFalse(AmoledTransform.isNearBlack(argb(255, 255, 255), threshold = 50, warmMode = false))
    }

    @Test
    fun `pixel at exactly threshold is near-black`() {
        assertTrue(AmoledTransform.isNearBlack(argb(5, 5, 5), threshold = 5, warmMode = false))
    }

    @Test
    fun `pixel with red channel over threshold is not near-black`() {
        assertFalse(AmoledTransform.isNearBlack(argb(6, 5, 5), threshold = 5, warmMode = false))
    }

    @Test
    fun `pixel with green channel over threshold is not near-black`() {
        assertFalse(AmoledTransform.isNearBlack(argb(5, 6, 5), threshold = 5, warmMode = false))
    }

    @Test
    fun `pixel with blue channel over threshold is not near-black`() {
        assertFalse(AmoledTransform.isNearBlack(argb(5, 5, 6), threshold = 5, warmMode = false))
    }

    @Test
    fun `pixel just above zero is near-black with threshold 1`() {
        assertTrue(AmoledTransform.isNearBlack(argb(1, 0, 0), threshold = 1, warmMode = false))
        assertTrue(AmoledTransform.isNearBlack(argb(0, 1, 0), threshold = 1, warmMode = false))
        assertTrue(AmoledTransform.isNearBlack(argb(0, 0, 1), threshold = 1, warmMode = false))
    }

    @Test
    fun `alpha channel is ignored`() {
        assertTrue(AmoledTransform.isNearBlack(argb(3, 3, 3, a = 0x00), threshold = 10, warmMode = false))
        assertTrue(AmoledTransform.isNearBlack(argb(3, 3, 3, a = 0x7F), threshold = 10, warmMode = false))
    }

    // ── isNearBlack — warm mode ──────────────────────────────────

    @Test
    fun `warm mode requires red minus blue greater than delta`() {
        // r=10, b=3: delta=7 > 3 → true
        assertTrue(AmoledTransform.isNearBlack(argb(10, 5, 3), threshold = 15, warmMode = true))
    }

    @Test
    fun `warm mode rejects neutral near-black`() {
        // r=5, b=5: delta=0, not > 3 → false
        assertFalse(AmoledTransform.isNearBlack(argb(5, 5, 5), threshold = 15, warmMode = true))
    }

    @Test
    fun `warm mode boundary delta exactly 3 is excluded`() {
        // r=10, b=7: delta=3, not STRICTLY > 3 → false
        assertFalse(AmoledTransform.isNearBlack(argb(10, 5, 7), threshold = 15, warmMode = true))
    }

    @Test
    fun `warm mode boundary delta 4 is included`() {
        // r=10, b=6: delta=4 > 3 → true
        assertTrue(AmoledTransform.isNearBlack(argb(10, 5, 6), threshold = 15, warmMode = true))
    }

    @Test
    fun `warm mode still requires near-black pre-condition`() {
        // r=200, b=100: delta=100, but far above threshold → false
        assertFalse(AmoledTransform.isNearBlack(argb(200, 150, 100), threshold = 50, warmMode = true))
    }

    // ── applyCorrection ──────────────────────────────────────────

    @Test
    fun `applyCorrection replaces near-black pixels with black`() {
        val input = intArrayOf(
            argb(3, 3, 3),        // near-black → should become BLACK
            argb(100, 100, 100),  // not near-black → preserved
            argb(0, 0, 0),        // pure black → preserved (not near-black)
        )
        val out = AmoledTransform.applyCorrection(input, threshold = 10, warmMode = false)

        assertEquals(AmoledTransform.COLOR_BLACK, out[0])
        assertEquals(argb(100, 100, 100), out[1])
        assertEquals(argb(0, 0, 0), out[2])
    }

    @Test
    fun `applyCorrection does not mutate input`() {
        val input = intArrayOf(argb(3, 3, 3), argb(100, 100, 100))
        val before = input.copyOf()

        AmoledTransform.applyCorrection(input, threshold = 10, warmMode = false)

        assertArrayEquals(before, input)
    }

    @Test
    fun `applyCorrection with threshold zero changes nothing except pure-zero which is excluded`() {
        val input = intArrayOf(argb(0, 0, 0), argb(1, 0, 0), argb(255, 0, 0))
        val out = AmoledTransform.applyCorrection(input, threshold = 0, warmMode = false)

        assertArrayEquals(input, out)
    }

    // ── analyze ──────────────────────────────────────────────────

    @Test
    fun `analyze marks near-black pixels red and counts them`() {
        val input = intArrayOf(
            argb(3, 3, 3),
            argb(100, 100, 100),
            argb(5, 5, 5),
            argb(0, 0, 0),
        )
        val result = AmoledTransform.analyze(input, threshold = 10, warmMode = false)

        assertEquals(2, result.nearBlackCount)
        assertEquals(AmoledTransform.COLOR_RED, result.pixels[0])
        assertEquals(argb(100, 100, 100), result.pixels[1])
        assertEquals(AmoledTransform.COLOR_RED, result.pixels[2])
        assertEquals(argb(0, 0, 0), result.pixels[3])
    }

    @Test
    fun `analyze returns zero count when nothing matches`() {
        val input = intArrayOf(argb(200, 200, 200), argb(255, 0, 0), argb(0, 0, 0))
        val result = AmoledTransform.analyze(input, threshold = 10, warmMode = false)

        assertEquals(0, result.nearBlackCount)
        assertArrayEquals(input, result.pixels)
    }

    @Test
    fun `analyze does not mutate input`() {
        val input = intArrayOf(argb(3, 3, 3), argb(100, 100, 100))
        val before = input.copyOf()

        AmoledTransform.analyze(input, threshold = 10, warmMode = false)

        assertArrayEquals(before, input)
    }

    // ── blackToTransparent ───────────────────────────────────────

    @Test
    fun `blackToTransparent replaces pure black with transparent`() {
        val input = intArrayOf(
            AmoledTransform.COLOR_BLACK,
            argb(100, 100, 100),
            AmoledTransform.COLOR_BLACK,
        )
        val out = AmoledTransform.blackToTransparent(input)

        assertEquals(AmoledTransform.COLOR_TRANSPARENT, out[0])
        assertEquals(argb(100, 100, 100), out[1])
        assertEquals(AmoledTransform.COLOR_TRANSPARENT, out[2])
    }

    @Test
    fun `blackToTransparent leaves near-black pixels alone`() {
        val input = intArrayOf(argb(1, 1, 1), argb(0, 0, 0))
        val out = AmoledTransform.blackToTransparent(input)

        // argb(1,1,1) is close to black but is not exactly COLOR_BLACK
        assertEquals(argb(1, 1, 1), out[0])
        // argb(0,0,0) with alpha 0xFF is fully opaque but not == -16777216 sign-bit;
        // it equals 0xFF000000.toInt() == COLOR_BLACK, so this one DOES get replaced.
        assertEquals(AmoledTransform.COLOR_TRANSPARENT, out[1])
    }

    @Test
    fun `blackToTransparent does not mutate input`() {
        val input = intArrayOf(AmoledTransform.COLOR_BLACK, argb(100, 100, 100))
        val before = input.copyOf()

        AmoledTransform.blackToTransparent(input)

        assertArrayEquals(before, input)
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Pack ARGB components into the same Int layout that
     * android.graphics.Bitmap uses (alpha<<24 | r<<16 | g<<8 | b).
     */
    private fun argb(r: Int, g: Int, b: Int, a: Int = 0xFF): Int =
        (a shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}