@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.util

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private fun toRadians(deg: Double): Double = deg * PI / 180.0

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val earthRadiusM = 6_371_000.0
    val dLat = toRadians(lat2 - lat1)
    val dLon = toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(toRadians(lat1)) * cos(toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadiusM * c).toFloat()
}

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

fun formatWalkTime(meters: Float): String {
    val minutes = (meters / 80).roundToInt().coerceAtLeast(1)
    return "$minutes min a pie"
}

fun formatDriveTime(meters: Float): String {
    val minutes = (meters / 500).roundToInt().coerceAtLeast(1)
    return "$minutes min en coche"
}

fun formatRelativeTime(timestampMs: Long): String {
    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val diffMs = nowMs - timestampMs
    val diffMin = diffMs / 60_000
    val diffHours = diffMin / 60
    val diffDays = diffHours / 24
    return when {
        diffMin < 1 -> "ahora mismo"
        diffMin < 60 -> "hace $diffMin min"
        diffHours < 24 -> "hace $diffHours h"
        else -> "hace $diffDays día${if (diffDays > 1) "s" else ""}"
    }
}

fun greetingByHour(hour: Int): String = when (hour) {
    in 6..11 -> "¡Buenos días!"
    in 12..19 -> "¡Buenas tardes!"
    else -> "¡Buenas noches!"
}

/**
 * Returns the best human-readable label for a location, combining POI emoji+name
 * and address when both are available.  Falls back to coordinate string.
 */
fun locationDisplayText(
    placeInfo: PlaceInfo?,
    address: AddressInfo?,
    lat: Double,
    lon: Double,
    showEmoji: Boolean = true,
): String {
    val place = placeInfo?.let { if (showEmoji) "${it.category.emoji} ${it.name}" else it.name }
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
