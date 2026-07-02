@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.vehicles.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.presentation.util.relativeTimeText
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.rememberDataTypography
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_view_map
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
internal fun DayHeaderRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DAY_HEADER_TOP_PAD_DP.dp, bottom = DAY_HEADER_BOTTOM_PAD_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DAY_HEADER_GAP_DP.dp),
    ) {
        Box(
            modifier = Modifier
                .size(DAY_HEADER_DOT_DP.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = DAY_HEADER_DOT_ALPHA), CircleShape)
        )
        Text(
            // Uppercase day label = data token → condensed statusPin, keeping its muted tone.
            text = label.uppercase(),
            style = rememberDataTypography().statusPin,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = DAY_HEADER_TEXT_ALPHA),
        )
    }
}

@Composable
internal fun EndedSessionTimelineNode(
    session: UserParking,
    isLast: Boolean,
    isActive: Boolean = false,
    onViewOnMap: (lat: Double, lon: Double, sessionId: String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Column(
            modifier = Modifier
                .width(RAIL_COLUMN_WIDTH_DP.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top spacer sized so the dot's CENTER sits on the card title's first text line
            // (card top padding + ~half a line ≈ DOT_CENTER_Y): spacer = center − dot radius.
            Spacer(Modifier.height(if (isActive) ACTIVE_DOT_TOP_SPACER_DP.dp else DOT_TOP_SPACER_DP.dp))
            if (isActive) {
                PulsingDot(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ACTIVE_DOT_SIZE_DP.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(DOT_SIZE_DP.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = DOT_ALPHA), CircleShape)
                )
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(RAIL_WIDTH_DP.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = PapBorders.HAIRLINE_DIVIDER_ALPHA))
                )
            }
        }

        Spacer(Modifier.width(RAIL_CARD_GAP_DP.dp))

        SessionCardContent(
            session = session,
            isActive = isActive,
            onViewOnMap = onViewOnMap,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = CARD_BOTTOM_GAP_DP.dp),
        )
    }
}

@Composable
private fun SessionCardContent(
    session: UserParking,
    isActive: Boolean,
    onViewOnMap: (lat: Double, lon: Double, sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val dateTime = remember(session.location.timestamp) {
        Instant.fromEpochMilliseconds(session.location.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val timeStr = "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
    val activeRelativeTime = relativeTimeText(session.location.timestamp)
    val primaryText = locationDisplayText(
        placeInfo = session.placeInfo,
        address = session.address,
        lat = session.location.latitude,
        lon = session.location.longitude,
    )
    val secondaryText = if (isActive) activeRelativeTime
        else session.address?.city?.let { "$it · $timeStr" } ?: timeStr

    val textPrimary = if (isActive) cs.onPrimaryContainer else cs.onSurface
    val textMuted = textPrimary.copy(alpha = if (isActive) ACTIVE_META_ALPHA else META_ALPHA)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) cs.primaryContainer else cs.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                start = CARD_PAD_DP.dp,
                top = CARD_PAD_DP.dp,
                bottom = CARD_PAD_DP.dp,
                end = CARD_END_PAD_DP.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = textPrimary,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Spacer(Modifier.height(TITLE_META_GAP_DP.dp))
                Text(
                    // Data-dense meta line ("city · 09:14") — condensed, same treatment as the Home
                    // spot-row meta so both timelines read identically. [UI-REGRESSION]
                    text = secondaryText,
                    style = rememberDataTypography().compactBody,
                    color = textMuted,
                )
            }
            IconButton(
                onClick = { onViewOnMap(session.location.latitude, session.location.longitude, session.id) },
            ) {
                Icon(Icons.Rounded.Map, contentDescription = stringResource(Res.string.history_view_map), tint = cs.primary)
            }
        }
    }
}

// ── Day header ───────────────────────────────────────────────────────────────
private const val DAY_HEADER_TOP_PAD_DP = 12
private const val DAY_HEADER_BOTTOM_PAD_DP = 6
private const val DAY_HEADER_GAP_DP = 8
private const val DAY_HEADER_DOT_DP = 5
private const val DAY_HEADER_DOT_ALPHA = 0.4f
private const val DAY_HEADER_TEXT_ALPHA = 0.4f

// ── Timeline rail (dot + connector line) ─────────────────────────────────────
private const val RAIL_COLUMN_WIDTH_DP = 20
private const val RAIL_WIDTH_DP = 1.5f
private const val RAIL_CARD_GAP_DP = 8
private const val DOT_SIZE_DP = 8
private const val ACTIVE_DOT_SIZE_DP = 14
private const val DOT_ALPHA = 0.65f
// Dot center optically aligned with the card title's first line: card top padding (12) plus roughly
// half a bodyMedium line (~8) ⇒ center at ~20dp; spacer = center − dot radius.
private const val DOT_CENTER_Y_DP = 20
private const val DOT_TOP_SPACER_DP = DOT_CENTER_Y_DP - DOT_SIZE_DP / 2
private const val ACTIVE_DOT_TOP_SPACER_DP = DOT_CENTER_Y_DP - ACTIVE_DOT_SIZE_DP / 2

// ── Session card ─────────────────────────────────────────────────────────────
private const val CARD_CORNER_DP = 12
private const val CARD_PAD_DP = 12
private const val CARD_END_PAD_DP = 4
private const val CARD_BOTTOM_GAP_DP = 8
private const val TITLE_META_GAP_DP = 2
private const val META_ALPHA = 0.5f
private const val ACTIVE_META_ALPHA = 0.6f
