@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles.components

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
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.presentation.util.locationDisplayText
import com.rndeveloper.paparcar.presentation.util.relativeTimeText
import com.rndeveloper.paparcar.ui.components.PapSectionHeaderRow
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.onVehicleIdentityContainer
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityContainer
import com.rndeveloper.paparcar.presentation.util.formatClockTime
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_view_map
import paparcar.composeapp.generated.resources.location_fallback_parking
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.rndeveloper.paparcar.ui.theme.PapAlpha

/**
 * Day separator inside the timeline — "HOY", "AYER", "VIERNES, 14 AGO 2026".
 *
 * It is a SUB-section header, not a data token: it opens a group under "APARCADO ACTUALMENTE"
 * instead of repeating inside a row, so it wears the same LECTURA voice as that header one step down
 * (`dense`), through the same component. It used to wear the then-condensed `badge` role — CIFRA is for
 * DATA, and a date separator is layout structure. [UI-HISTORY-IDENTITY-AND-SOURCE-001]
 */
@Composable
internal fun DayHeaderRow(label: String) {
    PapSectionHeaderRow(
        title = label,
        modifier = Modifier.padding(top = DAY_HEADER_TOP_PAD_DP.dp, bottom = DAY_HEADER_BOTTOM_PAD_DP.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = DAY_HEADER_TEXT_ALPHA),
        dense = true,
        leading = {
            Box(
                modifier = Modifier
                    .size(DAY_HEADER_DOT_DP.dp)
                    .background(
                        PapColor.brandData.copy(alpha = DAY_HEADER_DOT_ALPHA),
                        CircleShape,
                    )
            )
        },
    )
}

/**
 * @param watch how the page's vehicle is monitored — the timeline belongs to ONE car (Vehículos is a
 *   per-vehicle pager), so its rail and its live card carry that car's identity colour instead of a
 *   fixed brand green. [UI-COLOR-DOCTRINE-001][UI-HISTORY-IDENTITY-AND-SOURCE-001]
 */
@Composable
internal fun EndedSessionTimelineNode(
    session: UserParking,
    isLast: Boolean,
    watch: VehicleWatch,
    isActive: Boolean = false,
    onViewOnMap: (lat: Double, lon: Double, sessionId: String) -> Unit,
) {
    val identity = vehicleIdentityColor(watch)
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
                PulsingDot(color = identity, modifier = Modifier.size(ACTIVE_DOT_SIZE_DP.dp))
            } else {
                Box(
                    Modifier
                        .size(DOT_SIZE_DP.dp)
                        .background(identity.copy(alpha = DOT_ALPHA), CircleShape)
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
            watch = watch,
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
    watch: VehicleWatch,
    onViewOnMap: (lat: Double, lon: Double, sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val dateTime = remember(session.location.timestamp) {
        Instant.fromEpochMilliseconds(session.location.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    }
    // One place decides how this app writes a wall clock. [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001]
    val timeStr = formatClockTime(session.location.timestamp)
    val activeRelativeTime = relativeTimeText(session.location.timestamp)
    val primaryText = locationDisplayText(
        placeInfo = session.placeInfo,
        address = session.address,
    ) ?: stringResource(Res.string.location_fallback_parking)
    val secondaryText = if (isActive) activeRelativeTime
        else session.address?.city?.let { "$it · $timeStr" } ?: timeStr

    // The live card keeps the filled container it always had — only its HUE now follows the
    // vehicle's watch, because `primaryContainer` exists in green alone and a BT-watched car was
    // announcing itself in the assisted tier's colour. Container and content resolve as a pair.
    // [UI-HISTORY-IDENTITY-AND-SOURCE-001]
    val textPrimary = if (isActive) onVehicleIdentityContainer(watch) else cs.onSurface
    val textMuted = textPrimary.copy(alpha = if (isActive) ACTIVE_META_ALPHA else META_ALPHA)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) vehicleIdentityContainer(watch) else cs.surfaceContainerHigh,
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
                    // Place/address name is the session card's identity title → MARCA (rowName);
                    // the "city · time" subline below stays LECTURA prose. [TYPO-AUDIT-001]
                    style = PaparcarType.current.rowName,
                    color = textPrimary,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Spacer(Modifier.height(TITLE_META_GAP_DP.dp))
                Text(
                    // Leads with a place name you read ("city · 09:14") → LECTURA (caption), same as the
                    // vehicle footer. CIFRA stays for pure-data rows (distance · drive ·
                    // en-route in Home spots), which carry no place name. [CARD-ONE-BADGE-001]
                    text = secondaryText,
                    style = PaparcarType.current.caption,
                    color = textMuted,
                )
            }
            IconButton(
                onClick = { onViewOnMap(session.location.latitude, session.location.longitude, session.id) },
            ) {
                Icon(Icons.Rounded.Map, contentDescription = stringResource(Res.string.history_view_map), tint = PapColor.action)
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
private val META_ALPHA = PapAlpha.muted
private const val ACTIVE_META_ALPHA = 0.6f
