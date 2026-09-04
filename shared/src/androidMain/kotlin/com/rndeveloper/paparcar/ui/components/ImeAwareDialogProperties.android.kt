package com.rndeveloper.paparcar.ui.components

import androidx.compose.ui.window.DialogProperties

/** Opting the dialog window out of the automatic inset fit is what makes `imePadding()` inside it
 * mean anything. [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001] */
actual fun imeAwareDialogProperties(): DialogProperties =
    DialogProperties(decorFitsSystemWindows = false)
