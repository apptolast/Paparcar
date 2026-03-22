package io.apptolast.paparcar.presentation.util

import platform.Foundation.NSLocale

private val IMPERIAL_COUNTRIES = setOf("US", "LR", "MM")

actual fun defaultDistanceUnit(): DistanceUnit {
    // localeIdentifier format: "en_US", "es_ES", "my_MM", etc.
    val country = NSLocale.currentLocale.localeIdentifier
        .substringAfterLast('_')
        .uppercase()
    return if (country in IMPERIAL_COUNTRIES) DistanceUnit.IMPERIAL else DistanceUnit.METRIC
}