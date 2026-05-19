package com.github.reygnn.chiaroscuro.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes a [Bitmap] from a content [Uri].
 *
 * Exists as an injectable seam so the VM's `loadImage` path is unit-testable
 * on the JVM. Under the project's `unitTests.isReturnDefaultValues = true`
 * stub, [BitmapFactory.decodeStream] returns `null` and any assertion that
 * passes through `loadImage` is otherwise meaningless. Tests inject a fake
 * loader — typically a single-expression lambda thanks to `fun interface`:
 *
 * ```
 * val loader = BitmapLoader { _, _ -> null }
 * val vm = EditorViewModel(repository, loader)
 * ```
 *
 * Production code uses [ContentResolverBitmapLoader] (the constructor
 * default on `EditorViewModel`), so no wiring change is needed at the
 * call sites that already exist.
 */
fun interface BitmapLoader {
    suspend fun load(context: Context, uri: Uri): Bitmap?
}

/**
 * Reads from [Context.contentResolver] and decodes via [BitmapFactory],
 * then **normalizes the result to [Bitmap.Config.ARGB_8888]**.
 *
 * The normalization is mandatory, not cosmetic. On Android Q+ the platform
 * may decode certain image sources (HEIF, some animated/large PNGs, system
 * picker hand-offs) into [Bitmap.Config.HARDWARE] — a GPU-backed config
 * whose pixels live in graphics memory and cannot be read with
 * [Bitmap.getPixels]. The downstream `ImageProcessing` adapter calls
 * `getPixels` unconditionally, so a HARDWARE bitmap reaches the AMOLED
 * analyze/apply path and throws `IllegalStateException` at the first user
 * action.
 *
 * `inPreferredConfig = ARGB_8888` is only a hint and the decoder is free
 * to ignore it for hardware-accelerated paths, so the explicit `copy` is
 * the only reliable guarantee. The original is recycled when we own it
 * (i.e. when a copy was actually made) to release the native buffer
 * promptly.
 *
 * The IO-dispatcher hop for the decode lives here so tests can inject a
 * purely in-memory loader and stay on the test dispatcher. Other
 * IO-bound operations in the ViewModel (`applyQuickAction`,
 * `analyzeAmoled`, `applyAmoledCorrection`, `saveTransparent`) still
 * manage their own `withContext(Dispatchers.IO)` explicitly — this
 * abstraction is scoped to the decode path only.
 */
object ContentResolverBitmapLoader : BitmapLoader {
    override suspend fun load(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@withContext null

            if (raw.config == Bitmap.Config.ARGB_8888) {
                raw
            } else {
                raw.copy(Bitmap.Config.ARGB_8888, true).also { raw.recycle() }
            }
        }
}
