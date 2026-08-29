@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.layout.PaddingValues
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.Group
import com.rndeveloper.paparcar.ui.illustrations.EmptySpotsIllustration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.rndeveloper.paparcar.ui.icons.icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.stateColors
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.presentation.util.distanceMeters
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.util.driveTimeString
import com.rndeveloper.paparcar.presentation.util.locationDisplayText
import com.rndeveloper.paparcar.presentation.util.isManualReport
import com.rndeveloper.paparcar.presentation.util.freshness
import com.rndeveloper.paparcar.presentation.util.ageMs
import com.rndeveloper.paparcar.ui.components.EnRouteIndicator
import com.rndeveloper.paparcar.ui.components.PapEmptyStateCard
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.ui.components.SpotPuckIcon
import com.rndeveloper.paparcar.ui.components.SpotAgeIndicator
import com.rndeveloper.paparcar.ui.components.rememberSpotAgeClock
import com.rndeveloper.paparcar.ui.theme.PapBorders
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.spot_indicator_en_route
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_empty_subtitle
import paparcar.composeapp.generated.resources.home_empty_title
import paparcar.composeapp.generated.resources.home_filter_empty_clear
import paparcar.composeapp.generated.resources.home_filter_empty_subtitle
import paparcar.composeapp.generated.resources.home_filter_empty_title
import paparcar.composeapp.generated.resources.home_report_fab_cd
import paparcar.composeapp.generated.resources.home_report_subtitle
import paparcar.composeapp.generated.resources.home_spot_freshness_fresh
import paparcar.composeapp.generated.resources.home_spot_freshness_stale
import paparcar.composeapp.generated.resources.home_spot_freshness_recent
import paparcar.composeapp.generated.resources.home_spot_unconfirmed_badge
import paparcar.composeapp.generated.resources.location_fallback_spot
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PapColor

/**
 * Spot row (v1 redesign).
 *
 *  - 3dp left selection indicator (primary) so the row keeps its neutral fill.
 *  - Circular "P" badge whose colour mirrors the map marker tier
 *    (FRESH=green, RECENT=amber, STALE=red; manual provenance = person badge on the puck).
 *  - Meta row: UPPERCASE freshness label + distance + drive time.
 */
@Composable
internal fun HomeSpotRow(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    onSelect: () -> Unit,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // One clock, one level, shared by the puck and the age chip — so a spot can never disagree
    // with itself across its own row. [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001]
    val nowMs = rememberSpotAgeClock()
    val freshness = spot.freshness(nowMs)

    val rowBg = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = SELECTED_ROW_BG_ALPHA)
    else
        Color.Transparent

    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        color = rowBg,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(SELECTION_INDICATOR_W_DP.dp)
                    .height(SELECTION_INDICATOR_H_DP.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ),
            )
            SpotRowContent(
                spot = spot,
                userLocation = userLocation,
                freshness = freshness,
                ageMs = spot.ageMs(nowMs),
                modifier = Modifier
                    .padding(start = 13.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun SpotRowContent(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    freshness: SpotFreshness,
    ageMs: Long,
    modifier: Modifier = Modifier,
) {
    val distanceM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, spot.location.latitude, spot.location.longitude)
    }
    val displayText = locationDisplayText(
        placeInfo = spot.placeInfo,
        address = spot.address,
    ) ?: stringResource(Res.string.location_fallback_spot)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Same puck as the map marker, tail-less — one shared component keeps list and map in sync,
        // with the freshness tier encoded by colour/ring/badge. [HOME-PUCK-001]
        SpotPuckIcon(
            reliability = freshness,
            enRouteCount = spot.enRouteCount,
            isManual = spot.isManualReport,
            modifier = Modifier.size(BADGE_DP.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(POI_ICON_GAP_DP.dp),
            ) {
                spot.placeInfo?.let { place ->
                    Icon(
                        imageVector = place.category.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(POI_ICON_DP.dp),
                    )
                }
                Text(
                    text = displayText,
                    // Spot/place name is the row's identity → Outfit (rowName), like the vehicle
                    // name. [TYPO-AUDIT-001] [CARD-ONE-BADGE-001]
                    style = PaparcarType.current.rowName,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(2.dp))
            // Meta line — reliability tier · drive time · en-route count. All Inter: it shares a
            // line box with taxonomy you READ, one line under a name in Outfit. Barlow here was the
            // visible clash the user reported. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
            val type = PaparcarType.current
            // La meta se queda con lo que hace falta para DECIDIR desde la lista. Lo que describe la
            // plaza una vez elegida vive en su modal, que ya lo pinta — la fila lo estaba
            // repitiendo. [UI-SPOT-ROW-SAYS-WHAT-DECIDES-001]
            //
            //  - La fiabilidad sale: el color del puck ya la dice, y el modal la explica con su
            //    medidor (`FiabilityIndicator`).
            //  - SIN CONFIRMAR sale: el modal lo cuenta entero, con los dos botones que lo
            //    resuelven. Aqui era una palabra larga sin salida. [DET-HANDOFF-NOT-MANUAL-001 §B.3]
            //  - La gente en camino se queda, en icono + cifra: es la senal de que la plaza puede
            //    estar cogida al llegar, y como glifo cuesta un tercio de lo que costaba escrita.
            // El espaciado agrupa: tiempo y metros son el MISMO dato dicho de dos formas, asi que
            // van pegados por su separador; la gente en camino es otra cosa y se separa mas. Un
            // `spacedBy` uniforme los ponia a los tres a la misma distancia y se leian como tres
            // datos sueltos.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (distanceM != null) {
                    Text(
                        driveTimeString(distanceM),
                        style = type.meta,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_MUTED_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        SheetTokens.META_SEPARATOR,
                        style = type.meta,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SheetTokens.META_SEPARATOR_ALPHA),
                    )
                    // Los metros viven aqui, no arriba: la linea del nombre entera es para el NOMBRE,
                    // que es lo que se escanea y lo que se truncaba en los sitios de nombre largo.
                    Text(
                        distanceString(distanceM),
                        style = type.meta,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SheetTokens.META_VALUE_ALPHA),
                        maxLines = 1,
                    )
                }
                if (spot.enRouteCount > 0) {
                    Spacer(Modifier.width(EN_ROUTE_LEAD_GAP_DP.dp))
                    Icon(
                        imageVector = Icons.Rounded.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = META_MUTED_ALPHA),
                        modifier = Modifier.size(EN_ROUTE_ICON_DP.dp),
                    )
                    Spacer(Modifier.width(EN_ROUTE_GAP_DP.dp))
                    Text(
                        spot.enRouteCount.toString(),
                        style = type.meta,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_MUTED_ALPHA),
                        maxLines = 1,
                    )
                }
            }
        }

        if (spot.location.timestamp > 0L) {
            SpotAgeIndicator(ageMs = ageMs, freshness = freshness)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────────────────────
// Empty states — surface card with centred icon/title/subtitle.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeEmptySpots(
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Dashed border = "this slot is waiting to be filled" — an invitation, not a card.
    // "Notify me when there's one" (bell → zone subscription) is deliberately absent:
    // there is no zone-subscription backend yet. [ZONE-SUBSCRIBE-001] [UI-SHEET-001]
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeW = PapBorders.thin.toPx()
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(EMPTY_DASH_CORNER_DP.dp.toPx()),
                    style = Stroke(
                        width = strokeW,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(EMPTY_DASH_ON_DP.dp.toPx(), EMPTY_DASH_OFF_DP.dp.toPx()),
                        ),
                    ),
                )
            }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EmptySpotsIllustration(
            modifier = Modifier.size(EMPTY_ILLUSTRATION_W.dp, EMPTY_ILLUSTRATION_H.dp),
        )
        Spacer(Modifier.height(2.dp))
        // Shared empty-state recipe (titleSmall Bold + bodySmall) — same hierarchy as
        // HomeEmptyFilteredSpots so the two states read as one family. [HOME-VEH-REFINE-001]
        Text(
            stringResource(Res.string.home_empty_title),
            style = PaparcarType.current.rowTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(Res.string.home_empty_subtitle),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_SUBTITLE_ALPHA),
        )
        Spacer(Modifier.height(8.dp))
        PapFooterButton(
            label = stringResource(Res.string.home_report_fab_cd),
            leadingIcon = Icons.Rounded.Campaign,
            onClick = onReport,
            style = PapFooterButtonStyle.Filled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun HomeEmptyFilteredSpots(
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PapEmptyStateCard(modifier = modifier) {
        Icon(
            Icons.Rounded.FilterAltOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_ICON_ALPHA),
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(2.dp))
        // Same recipe as HomeEmptySpots (titleSmall Bold + bodySmall). [HOME-VEH-REFINE-001]
        Text(
            stringResource(Res.string.home_filter_empty_title),
            style = PaparcarType.current.rowTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(Res.string.home_filter_empty_subtitle),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_SUBTITLE_ALPHA),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(Res.string.home_filter_empty_clear),
            style = PaparcarType.current.label,
            color = PapColor.actionText,
            modifier = Modifier.clickable(onClick = onClearFilter),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Report spot CTA — same molde as HomeParkingRow (icon box + title + subtitle)
// but with a trailing "+" instead of a chevron. The "+" alone signals the
// add-action; the redundant "Notify the community" pill is dropped.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeReportSpotCard(
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PapOutlinedCard(
        onClick = onReport,
        modifier = modifier.fillMaxWidth(),
        shape = PapShapes.cardSmall,
    ) {
        PapListItem(
            title = stringResource(Res.string.home_report_fab_cd),
            subtitle = stringResource(Res.string.home_report_subtitle),
            titleStyle = PaparcarType.current.rowTitle,
            titleMaxLines = 1,
            subtitleStyle = PaparcarType.current.label,
            subtitleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = PRIMARY_CARD_SUBTITLE_ALPHA),
            subtitleMaxLines = 1,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            gap = 12.dp,
            leading = {
                PapIconTile(
                    icon = Icons.Rounded.Campaign,
                    size = PRIMARY_CARD_ICON_BOX_DP.dp,
                    shape = RoundedCornerShape(PRIMARY_CARD_ICON_CORNER_DP.dp),
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    iconSize = 22.dp,
                )
            },
            trailing = {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            },
        )
    }
}

private const val SELECTION_INDICATOR_W_DP = 3
private const val SELECTION_INDICATOR_H_DP = 56
private const val BADGE_DP = 42
private const val POI_ICON_DP = 15
private const val POI_ICON_GAP_DP = 5
private const val EN_ROUTE_ICON_DP = 15
private const val EN_ROUTE_GAP_DP = 4
/** Aire ANTES del glifo de gente: los separa del par tiempo/distancia. */
private const val EN_ROUTE_LEAD_GAP_DP = 14
private const val SELECTED_ROW_BG_ALPHA = 0.30f
// Separator between data tokens on the meta line ("FIABLE · 80 m · 1 min").
private val META_MUTED_ALPHA = PapAlpha.subtitle
private val PRIMARY_CARD_SUBTITLE_ALPHA = PapAlpha.subtitle
private const val PRIMARY_CARD_ICON_BOX_DP = 44
private const val PRIMARY_CARD_ICON_CORNER_DP = 14
private const val EMPTY_ICON_ALPHA = 0.25f
private val EMPTY_SUBTITLE_ALPHA = PapAlpha.muted
private const val EMPTY_ILLUSTRATION_W = 180
private const val EMPTY_ILLUSTRATION_H = 154
// Dashed "waiting slot" border of the empty state — same 14dp tier as cardSmall.
private const val EMPTY_DASH_CORNER_DP = 14
private const val EMPTY_DASH_ON_DP = 6
private const val EMPTY_DASH_OFF_DP = 6
