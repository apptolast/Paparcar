package io.apptolast.paparcar.presentation.util

import androidx.compose.runtime.compositionLocalOf

/** Measurement system for displaying distances to the user. */
enum class DistanceUnit { METRIC, IMPERIAL }

/** CompositionLocal that provides the active [DistanceUnit] for the app. */
val LocalDistanceUnit = compositionLocalOf { DistanceUnit.METRIC }

/**
 * Returns the distance unit preferred by the device's current locale.
 * Imperial (ft / mi) for US, Liberia and Myanmar; metric everywhere else.
 */
expect fun defaultDistanceUnit(): DistanceUnit