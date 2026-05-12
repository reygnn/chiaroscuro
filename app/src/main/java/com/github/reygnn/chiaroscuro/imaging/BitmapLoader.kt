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
 * Reads from [Context.contentResolver] and decodes via [BitmapFactory].
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
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }
}
