package io.apptolast.paparcar.presentation.util

import androidx.compose.runtime.Composable

/**
 * Returns a launcher that opens an external maps app (Google Maps on Android,
 * Apple Maps on iOS — pending) with turn-by-turn directions to the given point.
 *
 * @return `(lat, lon, walking) -> Unit`. When `walking` is true, the launcher
 *   requests walking-mode directions; otherwise driving.
 *
 * The Composable form lets the launcher capture the local platform context
 * (e.g. Android `LocalContext`) without leaking it into ViewModels.
 *
 * Introduced for [PEEK-ACTIONS-001].
 */
@Composable
expect fun rememberOpenExternalNavigation(): (lat: Double, lon: Double, walking: Boolean) -> Unit
