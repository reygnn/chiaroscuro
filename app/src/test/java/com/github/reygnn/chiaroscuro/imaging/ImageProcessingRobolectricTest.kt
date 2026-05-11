package com.github.reygnn.chiaroscuro.imaging

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric test for [ImageProcessing.blackToTransparent].
 *
 * This is the **only** Robolectric-backed test in the project. It exists to
 * guard a specific Android framework interaction that cannot be exercised
 * on the pure JVM: the propagation of [Bitmap.hasAlpha] through
 * [Bitmap.copy] and [Bitmap.compress], and its effect on whether the final
 * PNG carries an alpha channel.
 *
 * Historical context: an earlier version of this project regressed silently
 * because [ImageProcessing.blackToTransparent] produced a bitmap whose
 * pixel array contained `0x00000000` values (transparent), but whose
 * [Bitmap.hasAlpha] flag was `false` (inherited from an opaque source).
 * [Bitmap.compress] uses that flag to decide whether to emit an alpha
 * channel, so the resulting PNG was fully opaque despite our pixel data.
 *
 * The two tests below pin the invariant from two angles:
 *   1. The Bitmap object itself carries hasAlpha=true after our transform.
 *   2. The emitted PNG file actually encodes an alpha channel in its
 *      IHDR color-type byte.
 *
 * We check the PNG color-type byte directly rather than re-decoding via
 * BitmapFactory, because Robolectric's decode path does not always
 * reconstruct the hasAlpha flag on the returned Bitmap even when the
 * underlying bytes contain an alpha channel. Reading the IHDR byte is
 * what the actual Android PNG decoder on a device would do — and it's
 * a stable PNG-spec invariant, not a framework-implementation detail.
 *
 * SDK 36 is targeted to match the app's minSdk. This requires
 * Robolectric 4.16+ and JDK 21 as the test-time runtime; bytecode
 * target stays JVM 17. See TESTING_CONVENTIONS.kt section 7.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.BAKLAVA])
class ImageProcessingRobolectricTest {

    @Test
    fun `blackToTransparent sets hasAlpha even when source was opaque`() {
        val opaqueSource = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            setHasAlpha(false)
            eraseColor(Color.BLACK)
        }
        // Sanity check the premise of the test.
        assertFalse("Test setup: source must start out opaque", opaqueSource.hasAlpha())

        val result = ImageProcessing.blackToTransparent(opaqueSource)

        assertTrue(
            "Result must carry hasAlpha=true so Bitmap.compress emits an alpha channel",
            result.hasAlpha(),
        )
        assertEquals(
            "Pixel array must actually contain transparent pixels",
            Color.TRANSPARENT,
            result.getPixel(0, 0),
        )
    }

    @Test
    fun `compressed PNG declares an alpha channel in its IHDR`() {
        val opaqueSource = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            setHasAlpha(false)
            eraseColor(Color.BLACK)
        }

        val transparent = ImageProcessing.blackToTransparent(opaqueSource)

        val bytes = ByteArrayOutputStream().use { out ->
            val ok = transparent.compress(Bitmap.CompressFormat.PNG, 100, out)
            assertTrue("compress(PNG) must succeed", ok)
            out.toByteArray()
        }

        assertTrue("Output must be a PNG", bytes.isPngSignature())
        assertEquals(
            "PNG IHDR color-type byte must indicate RGBA (= 6)",
            PNG_COLOR_TYPE_RGBA,
            bytes.readPngColorType(),
        )
    }

    // ── PNG spec helpers ─────────────────────────────────────────

    private fun ByteArray.isPngSignature(): Boolean {
        if (size < PNG_SIGNATURE.size) return false
        for (i in PNG_SIGNATURE.indices) {
            if (this[i] != PNG_SIGNATURE[i]) return false
        }
        return true
    }

    /**
     * Returns the color-type byte from the IHDR chunk of a PNG byte stream.
     *
     * PNG layout (PNG spec, ISO 15948):
     *   bytes  0..7   PNG signature
     *   bytes  8..11  IHDR length (always 13)
     *   bytes 12..15  IHDR type ("IHDR")
     *   bytes 16..19  width
     *   bytes 20..23  height
     *   byte     24   bit depth
     *   byte     25   color type    ← this is what we read
     *   byte     26   compression
     *   byte     27   filter
     *   byte     28   interlace
     *
     * Color type values:
     *   0 = Grayscale
     *   2 = RGB
     *   3 = Palette
     *   4 = Grayscale + Alpha
     *   6 = RGBA
     */
    private fun ByteArray.readPngColorType(): Int {
        require(size > PNG_COLOR_TYPE_OFFSET) { "Byte array too short to contain PNG IHDR" }
        return this[PNG_COLOR_TYPE_OFFSET].toInt() and 0xFF
    }

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        private const val PNG_COLOR_TYPE_OFFSET = 25
        private const val PNG_COLOR_TYPE_RGBA = 6
    }
}