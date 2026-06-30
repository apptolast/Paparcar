package io.apptolast.paparcar.presentation.util

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.util.haversineMeters
import kotlin.math.abs
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
 * address when both are available. Falls back to coordinate string. The POI
 * category is shown via a dedicated icon at the render site, never an emoji.
 */
fun locationDisplayText(
    placeInfo: PlaceInfo?,
    address: AddressInfo?,
    lat: Double,
    lon: Double,
): String {
    val place = placeInfo?.name
    val addr = address?.displayLine
    return when {
        place != null && addr != null -> "$place  ·  $addr"
        place != null -> place
        addr != null -> addr
        else -> formatCoords(lat, lon)
    }
}

/** "40.4167°, -3.7037°" — KMP-safe, no String.format */
fun formatCoords(lat: Double, lon: Double, decimals: Int = 4): String =
    "${formatCoord(lat, decimals)}°, ${formatCoord(lon, decimals)}°"

private fun formatCoord(value: Double, decimals: Int): String {
    val sign = if (value < 0) "-" else ""
    val absVal = abs(value)
    val intPart = absVal.toLong()
    val multiplier = when (decimals) {
        4 -> 10_000L
        6 -> 1_000_000L
        else -> 10_000L
    }
    val frac = ((absVal - intPart) * multiplier + 0.5).toLong().coerceIn(0, multiplier - 1)
    return "$sign$intPart.${frac.toString().padStart(decimals, '0')}"
}
