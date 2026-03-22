package io.apptolast.paparcar.presentation.util

import java.util.Locale

private val IMPERIAL_COUNTRIES = setOf("US", "LR", "MM")

actual fun defaultDistanceUnit(): DistanceUnit {
    val country = Locale.getDefault().country.uppercase()
    return if (country in IMPERIAL_COUNTRIES) DistanceUnit.IMPERIAL else DistanceUnit.METRIC
}