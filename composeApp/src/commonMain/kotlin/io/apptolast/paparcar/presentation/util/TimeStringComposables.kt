@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.drive_time_minutes
import paparcar.composeapp.generated.resources.relative_time_day
import paparcar.composeapp.generated.resources.relative_time_days
import paparcar.composeapp.generated.resources.relative_time_hours
import paparcar.composeapp.generated.resources.relative_time_just_now
import paparcar.composeapp.generated.resources.relative_time_minutes
import paparcar.composeapp.generated.resources.walk_time_minutes
import kotlin.math.roundToInt

private const val TIME_RECOMPUTE_INTERVAL_MS = 60_000L
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val WALK_SPEED_M_PER_MIN = 80f
private const val DRIVE_SPEED_M_PER_MIN = 500f

/**
 * Localized relative-time string that recomputes itself every 60 seconds.
 * Uses string resources so it adapts to the active locale.
 */
@Composable
fun relativeTimeText(timestampMs: Long): String {
    var tick by remember(timestampMs) { mutableStateOf(0) }
    LaunchedEffect(timestampMs) {
        while (true) {
            delay(TIME_RECOMPUTE_INTERVAL_MS)
            tick++
        }
    }

    // Suppress the unused-variable warning — tick is only read to trigger recomposition.
    @Suppress("UNUSED_EXPRESSION") tick

    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val diffMin = (nowMs - timestampMs) / MS_PER_MINUTE
    val diffHours = diffMin / MINUTES_PER_HOUR
    val diffDays = diffHours / HOURS_PER_DAY

    return when {
        diffMin < 1L -> stringResource(Res.string.relative_time_just_now)
        diffMin < 60L -> stringResource(Res.string.relative_time_minutes, diffMin.toInt())
        diffHours < 24L -> stringResource(Res.string.relative_time_hours, diffHours.toInt())
        diffDays == 1L -> stringResource(Res.string.relative_time_day, diffDays.toInt())
        else -> stringResource(Res.string.relative_time_days, diffDays.toInt())
    }
}

@Composable
fun walkTimeString(meters: Float): String {
    val minutes = (meters / WALK_SPEED_M_PER_MIN).roundToInt().coerceAtLeast(1)
    return stringResource(Res.string.walk_time_minutes, minutes)
}

@Composable
fun driveTimeString(meters: Float): String {
    val minutes = (meters / DRIVE_SPEED_M_PER_MIN).roundToInt().coerceAtLeast(1)
    return stringResource(Res.string.drive_time_minutes, minutes)
}

/**
 * Composable wrapper for [formatDistance] that resolves the active
 * unit system from [LocalDistanceUnit] at composition time.
 */
@Composable
fun distanceString(meters: Float): String = formatDistance(meters, LocalDistanceUnit.current)
