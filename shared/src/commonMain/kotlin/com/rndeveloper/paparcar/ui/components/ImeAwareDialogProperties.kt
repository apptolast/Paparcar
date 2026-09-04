package com.rndeveloper.paparcar.ui.components

import androidx.compose.ui.window.DialogProperties

/**
 * Properties for a dialog whose CONTENT reacts to the keyboard via `imePadding()`.
 *
 * On Android a dialog gets its own window, and by default that window swallows the IME insets —
 * measured on the Oppo, `imePadding()` inside it is a plain no-op and the keyboard covers the
 * actions. The opt-out (`decorFitsSystemWindows = false`) exists only in the ANDROID constructor
 * of [DialogProperties]; naming it in commonMain does not compile on Kotlin/Native — that one
 * call site kept the CI `apple` job red for a full day of master pushes.
 * [UI-A-DIALOG-PARAM-ONLY-ANDROID-KNOWS-001]
 */
expect fun imeAwareDialogProperties(): DialogProperties
