@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapAmber
import com.rndeveloper.paparcar.ui.theme.PapAmberMuted
import com.rndeveloper.paparcar.ui.theme.PapSpotFresh
import com.rndeveloper.paparcar.ui.theme.PapSpotFreshMuted
import com.rndeveloper.paparcar.ui.theme.PapRed
import com.rndeveloper.paparcar.ui.theme.PapRedMuted
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import kotlinx.coroutines.delay
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.spot_indicator_en_route
import paparcar.composeapp.generated.resources.spot_indicator_age_now
import paparcar.composeapp.generated.resources.spot_indicator_age_minutes
import paparcar.composeapp.generated.resources.spot_indicator_age_hours
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.ui.theme.PapAlpha

private val   IndicatorIconSize              = 12.dp
private const val AGE_TICK_MS                = 30_000L  // refresh every 30 s
private const val MINUTES_PER_HOUR           = 60L

// ─────────────────────────────────────────────────────────────────────────────
// SpotAgeIndicator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Live age indicator for a community spot: how long ago it was freed, coloured by the one
 * freshness ramp. [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001]
 *
 * This used to be `TTLIndicator`, a countdown to [com.rndeveloper.paparcar.domain.model.Spot.expiresAt]
 * with thresholds of its own (green > 10 min remaining, amber ≤ 10, red ≤ 3). Two things were
 * wrong with that. It **contradicted the marker**: a 2 h spot went red on the map at 54 minutes
 * while this chip stayed green until 110. And it **promised something nothing measures** — no
 * signal in the app says whether a spot has been taken, so `expiresAt` is only when the sweep
 * deletes the document, never a statement about the parking space.
 *
 * Age is the honest version of the same chip: it reports an observed fact and lets the driver
 * judge. The colour is not decided here — it comes from [SpotFreshness], the same level the puck
 * and the map marker use.
 *
 * @param ageMs How long ago the spot was published.
 * @param freshness The ramp level for that age, resolved by the caller so one spot cannot disagree
 *   with itself across the row.
 */
@Composable
fun SpotAgeIndicator(
    ageMs: Long,
    freshness: SpotFreshness,
    modifier: Modifier = Modifier,
) {
    val ageMinutes = (ageMs / MS_PER_MINUTE).coerceAtLeast(0L)

    val containerColor = when (freshness) {
        SpotFreshness.FRESH  -> PapSpotFreshMuted
        SpotFreshness.RECENT -> PapAmberMuted
        SpotFreshness.STALE  -> PapRedMuted
    }
    val contentColor = when (freshness) {
        SpotFreshness.FRESH  -> PapSpotFresh
        SpotFreshness.RECENT -> PapAmber
        SpotFreshness.STALE  -> PapRed
    }
    val label = when {
        // "0 min ago" reads as no information at all — the first minute says it in words.
        // [UI-SPOT-CLOCKS-NEVER-READ-ZERO-001]
        ageMinutes < 1L -> stringResource(Res.string.spot_indicator_age_now)
        ageMinutes < MINUTES_PER_HOUR -> stringResource(Res.string.spot_indicator_age_minutes, ageMinutes.toInt())
        else -> stringResource(Res.string.spot_indicator_age_hours, (ageMinutes / MINUTES_PER_HOUR).toInt())
    }

    PapBadge(
        label = label,
        containerColor = containerColor,
        contentColor = contentColor,
        icon = Icons.Rounded.Schedule,
        modifier = modifier,
        // Age is a data token — condensed per the typography mechanism. [HOME-VEH-REFINE-001]
        textStyle = PaparcarType.current.badge,
    )
}

/** Emits epoch-millis now and re-emits every 30 s, so the age label climbs on screen instead of
 *  freezing at the value captured on first composition. [SPOT-TTL-LIVE-001] */
@Composable
fun rememberSpotAgeClock(): Long {
    var nowMs by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(AGE_TICK_MS)
            nowMs = Clock.System.now().toEpochMilliseconds()
        }
    }
    return nowMs
}

// ─────────────────────────────────────────────────────────────────────────────
// EnRouteIndicator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Shows how many users are currently heading to a spot.
 *
 * Hidden when [count] is 0.
 *
 * @param count Number of users currently en route to this spot.
 */
@Composable
fun EnRouteIndicator(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs),
    ) {
        Icon(
            imageVector = Icons.Rounded.Group,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = PapAlpha.subtitle),
            modifier = Modifier.size(IndicatorIconSize),
        )
        Text(
            text = stringResource(Res.string.spot_indicator_en_route, count),
            // En-route count is a data token — condensed per the typography mechanism. [HOME-VEH-REFINE-001]
            style = PaparcarType.current.badge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = PapAlpha.subtitle),
        )
    }
}
