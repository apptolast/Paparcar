package com.rndeveloper.paparcar.presentation.util

import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.domain.model.SpotFreshnessPolicy
import com.rndeveloper.paparcar.domain.model.SpotType

/**
 * Bridges a [Spot] to the one ramp the UI colours itself from.
 *
 * [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001] This replaces `SpotReliabilityUiState`, which
 * declared itself "the freshness RAMP" while reading [Spot.confidence] — a number that mixed
 * community votes with a clock. The level itself now lives in the domain ([SpotFreshness]), where
 * its thresholds are testable; presentation keeps only the mapping to colours and labels.
 */
fun Spot.freshness(nowMs: Long): SpotFreshness =
    SpotFreshnessPolicy.of(reportedAtMs = location.timestamp, nowMs = nowMs)

/** How long ago this spot was published, in millis. Negative ages (clock skew between the
 *  reporter's device and this one) are clamped to zero. */
fun Spot.ageMs(nowMs: Long): Long = (nowMs - location.timestamp).coerceAtLeast(0L)

/** Provenance flag feeding the person badge on the spot puck/pin. Provenance is told by a GLYPH,
 *  never by a colour of its own — the ramp is freshness and nothing else. [UI-COLOR-DOCTRINE-001 F5] */
val Spot.isManualReport: Boolean get() = type == SpotType.MANUAL_REPORT
