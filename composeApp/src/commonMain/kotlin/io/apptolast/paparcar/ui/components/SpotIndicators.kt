@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Timer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PapAmber
import io.apptolast.paparcar.ui.theme.PapAmberMuted
import io.apptolast.paparcar.ui.theme.PapGreen
import io.apptolast.paparcar.ui.theme.PapGreenMuted
import io.apptolast.paparcar.ui.theme.PapRed
import io.apptolast.paparcar.ui.theme.PapRedMuted
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import kotlinx.coroutines.delay
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.spot_indicator_en_route
import paparcar.composeapp.generated.resources.spot_indicator_ttl_expired
import paparcar.composeapp.generated.resources.spot_indicator_ttl_minutes

private val IndicatorIconSize = 12.dp
private const val TTL_TICK_MS  = 30_000L  // refresh every 30 s

// ─────────────────────────────────────────────────────────────────────────────
// TTLIndicator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Live time-to-live indicator for a community spot.
 *
 * Shows remaining minutes based on [expiresAtMs]. Updates every 30 s.
 * Color transitions: green (>10 min) → amber (≤10 min) → red (≤3 min / expired).
 *
 * @param expiresAtMs Epoch-ms when the spot expires (publish time + TTL).
 */
@Composable
fun TTLIndicator(
    expiresAtMs: Long,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(expiresAtMs) {
        while (true) {
            delay(TTL_TICK_MS)
            nowMs = Clock.System.now().toEpochMilliseconds()
        }
    }

    val remainingMs   = expiresAtMs - nowMs
    val remainingMins = (remainingMs / 60_000L).coerceAtLeast(0L)
    val expired       = remainingMs <= 0L

    val containerColor = when {
        expired         -> PapRedMuted
        remainingMins <= 3  -> PapRedMuted
        remainingMins <= 10 -> PapAmberMuted
        else            -> PapGreenMuted
    }
    val contentColor = when {
        expired         -> PapRed
        remainingMins <= 3  -> PapRed
        remainingMins <= 10 -> PapAmber
        else            -> PapGreen
    }
    val label = if (expired) {
        stringResource(Res.string.spot_indicator_ttl_expired)
    } else {
        stringResource(Res.string.spot_indicator_ttl_minutes, remainingMins.toInt())
    }

    PapBadge(
        label = label,
        containerColor = containerColor,
        contentColor = contentColor,
        icon = Icons.Outlined.Timer,
        modifier = modifier,
    )
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
            imageVector = Icons.Outlined.Group,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(IndicatorIconSize),
        )
        Text(
            text = stringResource(Res.string.spot_indicator_en_route, count),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}
