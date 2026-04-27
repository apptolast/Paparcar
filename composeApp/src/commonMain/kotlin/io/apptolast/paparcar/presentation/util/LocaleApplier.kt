package io.apptolast.paparcar.presentation.util

/**
 * Applies a BCP-47 language tag as the app's display language.
 * Pass "auto" to restore the system locale.
 * Called after the user changes the language in Settings.
 */
expect fun applyAppLocale(tag: String)
