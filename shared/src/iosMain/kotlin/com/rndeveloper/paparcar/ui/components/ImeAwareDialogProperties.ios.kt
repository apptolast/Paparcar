package com.rndeveloper.paparcar.ui.components

import androidx.compose.ui.window.DialogProperties

/** No decor-fits concept on iOS — the platform manages dialog insets on its own. */
actual fun imeAwareDialogProperties(): DialogProperties = DialogProperties()
