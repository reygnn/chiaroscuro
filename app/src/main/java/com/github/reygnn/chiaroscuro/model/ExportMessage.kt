package com.github.reygnn.chiaroscuro.model

/**
 * User-facing export result, emitted by the ViewModel and resolved to a
 * localized string by the screen at display time.
 *
 * Rationale: the ViewModel must not hold a Context, so it cannot call
 * Context.getString(...). Instead it emits a typed sentinel and the
 * screen — which owns Context — does the resource lookup. This keeps
 * VM tests independent of Android resources.
 */
sealed interface ExportMessage {
    data object Saved : ExportMessage
    data object AmoledApplied : ExportMessage
    data class Error(val throwableMessage: String?) : ExportMessage
}