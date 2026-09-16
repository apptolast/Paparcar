@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.stats_value_hours
import paparcar.composeapp.generated.resources.stats_value_hours_minutes
import paparcar.composeapp.generated.resources.stats_value_minutes
import paparcar.composeapp.generated.resources.drive_time_minutes
import paparcar.composeapp.generated.resources.relative_time_compact_day
import paparcar.composeapp.generated.resources.relative_time_compact_hour
import paparcar.composeapp.generated.resources.relative_time_compact_min
import paparcar.composeapp.generated.resources.relative_time_compact_now
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

/**
 * Compact form of [relativeTimeText] for narrow chips: "<1m", "5m", "2h", "3d".
 * Same tick-recompute logic, locale-aware strings, but far shorter output.
 */
@Composable
fun compactRelativeTimeText(timestampMs: Long): String {
    var tick by remember(timestampMs) { mutableStateOf(0) }
    LaunchedEffect(timestampMs) {
        while (true) {
            delay(TIME_RECOMPUTE_INTERVAL_MS)
            tick++
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick

    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val diffMin = (nowMs - timestampMs) / MS_PER_MINUTE
    val diffHours = diffMin / MINUTES_PER_HOUR
    val diffDays = diffHours / HOURS_PER_DAY

    return when {
        diffMin < 1L    -> stringResource(Res.string.relative_time_compact_now)
        diffMin < 60L   -> stringResource(Res.string.relative_time_compact_min, diffMin.toInt())
        diffHours < 24L -> stringResource(Res.string.relative_time_compact_hour, diffHours.toInt())
        else            -> stringResource(Res.string.relative_time_compact_day, diffDays.toInt())
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

/**
 * Composable wrapper for [formatRadius] — the one way a **radius** reaches the copy.
 * [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
 *
 * ⛔ A radius is never typed into `strings.xml`. Every sentence that names one takes it as `%1$s`
 * from here, derived from the constant that actually bounds the query, so it can neither drift from
 * that constant nor keep saying "km" to someone who chose miles.
 */
@Composable
fun radiusString(meters: Float): String = formatRadius(meters, LocalDistanceUnit.current)

/**
 * Una ALTURA en la unidad activa — el gálibo de un garaje.
 * [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
 */
@Composable
fun heightString(meters: Float): String = formatHeight(meters, LocalDistanceUnit.current)

/**
 * A span of time as a figure — "42 min", "1 h 12 min". [STATS-UI-001]
 *
 * It lives with the other time formatters because TWO surfaces write durations now: the figures
 * grid (average stay, time hunting) and the activity card's qualifier when the chart is drawing the
 * search-time series. Two copies of the same rounding is how "1 h 12 min" and "72 min" end up on
 * one screen. [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001]
 */
@Composable
fun durationText(durationMs: Long): String {
    val totalMinutes = durationMs / MS_PER_MINUTE
    val hours = (totalMinutes / MINUTES_PER_HOUR).toInt()
    val minutes = (totalMinutes % MINUTES_PER_HOUR).toInt()
    return when {
        hours == 0 -> stringResource(Res.string.stats_value_minutes, minutes)
        // ⛔ "7 h 0 min" is not how anyone says seven hours — it reads as a broken number, the same
        // way "5.6 EUR" does. Seen on device in the places list (15-09).
        minutes == 0 -> stringResource(Res.string.stats_value_hours, hours)
        else -> stringResource(Res.string.stats_value_hours_minutes, hours, minutes)
    }
}

/**
 * A wall-clock time, `HH:mm`, in the device's own zone.
 *
 * [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Deliberately NOT a relative age: an absolute time
 * needs no ticker, so it cannot freeze at whatever it read when its row was composed — the failure
 * [relativeTimeText] pays a coroutine to avoid, and that a marker or a one-shot row would simply
 * inherit. It is also the only form that is still true an hour later.
 *
 * 24-hour on purpose: this string is read next to a clock, and every locale we ship reads it that
 * way. Not a composable — it takes no resources, so it can be called from anywhere.
 */
fun formatClockTime(epochMs: Long): String {
    val local = kotlin.time.Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

/**
 * An hour of the day as a clock reading — `18` becomes `18:00`. [STATS-UI-001]
 *
 * Same 24-hour reasoning as [formatClockTime], and a sibling of it rather than a string resource:
 * this is a time FORMAT, not copy anyone translates, and splitting the two across code and
 * strings.xml is how one of them drifts.
 */
fun formatHourBucket(hourOfDay: Int): String = "${hourOfDay.toString().padStart(2, '0')}:00"

/**
 * Epoch-ms de una fecha ISO `yyyy-MM-dd`, a medianoche UTC.
 * [FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001]
 *
 * Existe para poder decir la EDAD de una medida con [relativeTimeText], que ya está traducido a los
 * nueve idiomas — en vez de inventar un formateador de fechas con nombres de mes para todos ellos.
 *
 * ⛔ Una cadena que no sea una fecha devuelve 0, y 0 se lee como "hace muchísimo": el fallo cae del
 * lado de parecer viejo, nunca del de parecer fresco. Es la dirección asimétrica de siempre.
 */
fun isoDateToEpochMs(isoDate: String): Long = runCatching {
    LocalDate.parse(isoDate).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
}.getOrDefault(0L)
