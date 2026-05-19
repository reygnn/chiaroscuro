package com.github.reygnn.chiaroscuro.model

/**
 * User-facing export result, emitted by the ViewModel and resolved to a
 * localized string by the screen at display time.
 *
 * Rationale: the ViewModel must not hold a Context, so it cannot call
 * Context.getString(...). Instead it emits a typed sentinel and the
 * screen — which owns Context — does the resource lookup. This keeps
 * VM tests independent of Android resources.
 *
 * [Error] is itself a sealed interface so the VM can distinguish the
 * one failure mode it can name precisely ([Error.CannotOpenOutputStream])
 * from the long tail of framework exceptions ([Error.Generic]). The
 * known case becomes a properly localized string; the generic case
 * still surfaces the framework message as a debugging hint and accepts
 * that the hint itself won't be translated.
 */
sealed interface ExportMessage {
    data object Saved : ExportMessage
    data object AmoledApplied : ExportMessage

    sealed interface Error : ExportMessage {
        /**
         * The destination URI's output stream could not be opened. Most
         * commonly: the document was deleted between the SAF picker
         * returning and the write attempt, the URI permission was revoked
         * by the system, or the storage volume is unavailable.
         */
        data object CannotOpenOutputStream : Error

        /**
         * Any other failure during export. The [message] is the framework
         * exception's message string — intentionally not localized; it
         * serves as a debugging hint rather than a polished user message.
         */
        data class Generic(val message: String?) : Error
    }
}
