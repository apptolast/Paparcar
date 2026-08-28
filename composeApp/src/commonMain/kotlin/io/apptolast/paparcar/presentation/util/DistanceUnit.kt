package io.apptolast.paparcar.presentation.util

import androidx.compose.runtime.compositionLocalOf

/** Measurement system for displaying distances to the user. */
enum class DistanceUnit { METRIC, IMPERIAL }

/** CompositionLocal that provides the active [DistanceUnit] for the app. */
val LocalDistanceUnit = compositionLocalOf { DistanceUnit.METRIC }