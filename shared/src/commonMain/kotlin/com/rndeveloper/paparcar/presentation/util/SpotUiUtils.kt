package com.rndeveloper.paparcar.presentation.util

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlin.math.roundToInt

/** UI-facing `Float` wrapper over the domain [haversineMeters] great-circle distance. */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float =
    haversineMeters(lat1, lon1, lat2, lon2).toFloat()

fun formatDistance(meters: Float, unit: DistanceUnit = DistanceUnit.METRIC): String = when (unit) {
    DistanceUnit.METRIC -> when {
        meters < 1000 -> "${meters.roundToInt()} m"
        else -> {
            val tenths = ((meters / 1000f) * 10 + 0.5f).toLong().coerceAtLeast(0)
            "${tenths / 10}.${tenths % 10} km"
        }
    }
    DistanceUnit.IMPERIAL -> {
        val feet = meters * 3.28084f
        val miles = meters / 1609.344f
        when {
            feet < 1000f -> "${feet.roundToInt()} ft"
            else -> {
                val tenths = ((miles * 10) + 0.5f).toLong().coerceAtLeast(0)
                "${tenths / 10}.${tenths % 10} mi"
            }
        }
    }
}

/**
 * Returns the best human-readable label for a location, combining POI name and
 * address when both are available, or `null` when neither exists — the call
 * site supplies its own context-appropriate fallback (a spot vs your parking).
 * Raw coordinates are never a label: the adjacent map already says WHERE, the
 * text's only job is to name it. [UI-LOCATION-FALLBACK-SPEAKS-HUMAN-001]
 * The POI category is shown via a dedicated icon at the render site, never an emoji.
 */
fun locationDisplayText(
    placeInfo: PlaceInfo?,
    address: AddressInfo?,
): String? {
    val place = placeInfo?.name
    val addr = address?.displayLine
    return when {
        place != null && addr != null -> "$place  ·  $addr"
        place != null -> place
        else -> addr
    }
}
