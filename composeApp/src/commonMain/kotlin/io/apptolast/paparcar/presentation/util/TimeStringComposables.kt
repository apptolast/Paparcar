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

/**
 * Localized relative-time string that recomputes itself every 60 seconds.
 * Uses string resources so it adapts to the active locale.
 */
@Composable
fun relativeTimeText(timestampMs: Long): String {
    var tick by remember(timestampMs) { mutableStateOf(0) }
    LaunchedEffect(timestampMs) {
        while (true) {
            delay(60_000L)
            tick++
        }
    }

    // Suppress the unused-variable warning — tick is only read to trigger recomposition.
    @Suppress("UNUSED_EXPRESSION") tick

    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val diffMin = (nowMs - timestampMs) / 60_000L
    val diffHours = diffMin / 60L
    val diffDays = diffHours / 24L

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
    val minutes = (meters / 80).roundToInt().coerceAtLeast(1)
    return stringResource(Res.string.walk_time_minutes, minutes)
}

@Composable
fun driveTimeString(meters: Float): String {
    val minutes = (meters / 500).roundToInt().coerceAtLeast(1)
    return stringResource(Res.string.drive_time_minutes, minutes)
}

/**
 * Composable wrapper for [formatDistance] that automatically resolves the
 * device's preferred unit system (metric / imperial) at composition time.
 */
@Composable
fun distanceString(meters: Float): String = formatDistance(meters, defaultDistanceUnit())
