@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import com.rndeveloper.paparcar.ui.components.papGuideHighlight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.tone
import com.rndeveloper.paparcar.domain.model.SpotTone
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
import com.rndeveloper.paparcar.ui.components.SpotReleasingIndicator
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
import paparcar.composeapp.generated.resources.home_report_needs_location
import paparcar.composeapp.generated.resources.home_spots_unasked
import paparcar.composeapp.generated.resources.home_report_subtitle
import paparcar.composeapp.generated.resources.home_spot_freshness_fresh
import paparcar.composeapp.generated.resources.home_spot_freshness_stale
import paparcar.composeapp.generated.resources.home_spot_freshness_recent
import paparcar.composeapp.generated.resources.location_fallback_spot
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PapColor

/**
 * Spot row (v1 redesign).
 *
 *  - 3dp left selection indicator (primary) so the row keeps its neutral fill.
 *  - The map's spot puck (the gap glyph) whose colour mirrors the marker tier
 *    (FRESH=green, RECENT=amber, STALE=red).
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
    // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] The TONE, not the ramp tier: a plaza that is being
    // vacated is not a point on the age ramp, and reading it as one would paint it green.
    val freshness = spot.tone(nowMs)

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
                        if (isSelected) PapColor.selected else Color.Transparent,
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
    freshness: SpotTone,
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

    // The canonical row anatomy, not a hand-rolled twin of it: puck = leading, meta = subtitleSlot,
    // age pill = trailing. The title line keeps its inline POI glyph via titleSlot — the structured
    // sibling of `title`, same contract subtitle/subtitleSlot already had. [UI-LIST-ITEM-001]
    PapListItem(
        title = displayText,
        modifier = modifier,
        contentPadding = PaddingValues(0.dp), // the caller owns this row's outer padding
        gap = ROW_SLOT_GAP_DP.dp,
        leading = {
            // Same puck as the map marker, tail-less — one shared component keeps list and map in
            // sync, with the freshness tier encoded by colour/ring/badge. [HOME-PUCK-001]
            SpotPuckIcon(
                reliability = freshness,
                enRouteCount = spot.enRouteCount,
                isManual = spot.isManualReport,
                // La fila deja de mentir sin escribir una palabra: el disco del puck dice que la
                // salida se dedujo. [SPOT-A-DEDUCED-SPOT-LOOKS-LIKE-A-SURE-ONE-001]
                unconfirmed = spot.status.isUnconfirmedOffer,
                modifier = Modifier.size(BADGE_DP.dp),
            )
        },
        titleSlot = {
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
                    // Spot/place name is the row's identity → MARCA (rowName), like the vehicle
                    // name. [TYPO-AUDIT-001] [CARD-ONE-BADGE-001]
                    style = PaparcarType.current.rowName,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        subtitleSlot = { SpotMetaRow(spot = spot, distanceM = distanceM) },
        // The trailing token says the one thing that decides whether to go: how old the space is
        // — or, for a plaza still under its car, that it is not a space yet.
        // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001]
        trailing = when {
            freshness == SpotTone.RELEASING -> { { SpotReleasingIndicator() } }
            spot.location.timestamp > 0L -> { { SpotAgeIndicator(ageMs = ageMs, freshness = freshness) } }
            else -> null
        },
    )
}

// Meta line — reliability tier · drive time · en-route count. All LECTURA: it shares a line box
// with taxonomy you READ, one line under a name in MARCA. CIFRA here was the visible clash the
// user reported. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
//
// La meta se queda con lo que hace falta para DECIDIR desde la lista. Lo que describe la plaza una
// vez elegida vive en su modal, que ya lo pinta — la fila lo estaba repitiendo.
// [UI-SPOT-ROW-SAYS-WHAT-DECIDES-001]
//
//  - La fiabilidad sale: el color del puck ya la dice, y el modal la explica con su medidor
//    (`FiabilityIndicator`).
//  - SIN CONFIRMAR sale: el modal lo cuenta entero, con los dos botones que lo resuelven. Aqui era
//    una palabra larga sin salida. [DET-HANDOFF-NOT-MANUAL-001 §B.3]
//  - La gente en camino se queda, en icono + cifra: es la senal de que la plaza puede estar cogida
//    al llegar, y como glifo cuesta un tercio de lo que costaba escrita.
// El espaciado agrupa: tiempo y metros son el MISMO dato dicho de dos formas, asi que van pegados
// por su separador; la gente en camino es otra cosa y se separa mas. Un `spacedBy` uniforme los
// ponia a los tres a la misma distancia y se leian como tres datos sueltos.
@Composable
private fun SpotMetaRow(spot: Spot, distanceM: Float?) {
    val type = PaparcarType.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (distanceM != null) {
            // Sin tiempo que sostener a esa distancia, la fila arranca en los metros — y el
            // separador se va con él: un « · » suelto delante de la cifra se lee como un dato que
            // falta. [COPY-A-DURATION-ROLLS-INTO-HOURS-AND-A-CAPACITY-IS-NOT-FREE-SPACES-001]
            driveTimeString(distanceM)?.let { driveTime ->
                Text(
                    driveTime,
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
            }
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

// ─────────────────────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────────────────────
// Empty states — surface card with centred icon/title/subtitle.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeEmptySpots(
    modifier: Modifier = Modifier,
    /**
     * ⛔ True cuando NO se ha preguntado: sin ubicación y sin que el mapa se haya llevado a ningún
     * sitio, no hay centro de consulta. «No hay plazas libres cerca» sería entonces un veredicto
     * sobre un sitio que nadie ha mirado — el mismo pecado que los carriles de parkings y
     * gasolineras ya tenían prohibido. Aquí la frase dice qué falta y cómo arreglarlo: buscar una
     * dirección vale tanto como activar la ubicación.
     * [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001] [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
     */
    unasked: Boolean = false,
) {
    // Una LINEA, no una caja — sigue siendo la doctrina: sin borde, sin titulo+subtitulo, sin CTA
    // propio (eso ya lo da la tarjeta de avisar que va debajo). [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]
    //
    // Lo que SI vuelve es la ilustracion, en el mismo formato minimo que ya usan gasolineras y
    // parkings: imagen + caption, nada mas — no el bloque pesado (~150dp, borde discontinuo,
    // titulo, subtitulo, CTA relleno) que se quito. Solo en el veredicto real de "vacio": el estado
    // "unasked" no es un veredicto sobre el mundo (nadie ha preguntado todavia), asi que se queda
    // en texto solo, igual que las otras frases-que-no-son-veredicto de gasolina y parkings.
    if (unasked) {
        Text(
            text = stringResource(Res.string.home_spots_unasked),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        )
    } else {
        Column(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EmptySpotsIllustration(
                modifier = Modifier.size(EMPTY_ILLUSTRATION_W.dp, EMPTY_ILLUSTRATION_H.dp),
            )
            Text(
                text = stringResource(Res.string.home_empty_title),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun HomeEmptyFilteredSpots(
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Misma receta que su hermana: una linea. La diferencia es que aqui SI hay algo que hacer sin
    // salir del carril - quitar el filtro -, asi que la accion va en la misma linea, en texto.
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.home_filter_empty_title),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.home_filter_empty_clear),
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
    /** False while Home still has no place to start the pin from — see
     *  [SPOT-A-REPORT-WITHOUT-A-PLACE-MUST-NOT-HAPPEN-001]. */
    enabled: Boolean = true,
    /** A checklist step is pointing at this card. [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001] */
    highlighted: Boolean = false,
) {
    PapOutlinedCard(
        onClick = onReport.takeIf { enabled },
        modifier = modifier.fillMaxWidth().papGuideHighlight(highlighted, PapShapes.card),
        shape = PapShapes.card,
    ) {
        PapListItem(
            title = stringResource(Res.string.home_report_fab_cd),
            // ⛔ Deshabilitada, la tarjeta DICE por qué. Un control apagado y mudo se lee como una
            // app rota; este tiene una razón que además el usuario puede resolver, y la frase la
            // nombra entera: causa, consecuencia y remedio. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
            subtitle = stringResource(
                if (enabled) Res.string.home_report_subtitle else Res.string.home_report_needs_location,
            ),
            titleStyle = PaparcarType.current.rowTitle,
            titleMaxLines = 1,
            subtitleStyle = PaparcarType.current.label,
            subtitleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = PRIMARY_CARD_SUBTITLE_ALPHA),
            // Dos líneas SIEMPRE: ninguna de las dos frases cabe en una sola en un móvil estrecho, y
            // la de habilitada moría en «…quieres compar…». Recortar una razón la deja diciendo la
            // mitad, y la altura no es nueva — la versión deshabilitada ya reservaba las dos.
            subtitleMaxLines = 2,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            gap = 12.dp,
            leading = {
                // ⛔ Verde, y es una de las dos excepciones del presupuesto: esta tarjeta ES una
                // acción — toda ella avisa de una plaza — así que su glifo dice lo mismo que su "+".
                // Al pasar el tile canónico a neutro por defecto, este megáfono se quedó gris y en
                // device leía como un control APAGADO, que es lo contrario de lo que ofrece.
                // (User, 10-09.) [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]
                PapIconTile(
                    icon = Icons.Rounded.Campaign,
                    size = PRIMARY_CARD_ICON_BOX_DP.dp,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    tint = PapColor.action,
                    iconSize = 22.dp,
                )
            },
            trailing = {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = PapColor.action,
                    modifier = Modifier.size(22.dp),
                )
            },
        )
    }
}

private const val SELECTION_INDICATOR_W_DP = 3
private const val SELECTION_INDICATOR_H_DP = 56
private const val BADGE_DP = 42
/** Gap between the row's slots (puck · text block · age pill) — denser than the PapListItem
 *  default: the puck already carries visual weight. */
private const val ROW_SLOT_GAP_DP = 12
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
// Same footprint as the fuel lane's empty illustration — one size for the three lanes.
private const val EMPTY_ILLUSTRATION_W = 112
private const val EMPTY_ILLUSTRATION_H = 96
