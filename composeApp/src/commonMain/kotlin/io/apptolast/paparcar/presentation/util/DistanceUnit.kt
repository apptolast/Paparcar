package io.apptolast.paparcar.presentation.util

/** Measurement system for displaying distances to the user. */
enum class DistanceUnit { METRIC, IMPERIAL }

/**
 * Returns the distance unit preferred by the device's current locale.
 * Imperial (ft / mi) for US, Liberia and Myanmar; metric everywhere else.
 */
expect fun defaultDistanceUnit(): DistanceUnit