package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.VehicleColor
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.ui.components.DrivingRadarHalo
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapShimmerBox
import com.rndeveloper.paparcar.ui.components.PapStepperSlot
import com.rndeveloper.paparcar.ui.components.SpotPuckIcon
import com.rndeveloper.paparcar.ui.components.VehicleGlyph
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PapFonts
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.figureOpticalLiftSp
import com.rndeveloper.paparcar.ui.theme.greenOutline
import com.rndeveloper.paparcar.ui.theme.PapColor
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_counter_unit_free
import paparcar.composeapp.generated.resources.home_peek_dismiss_cd

/**
 * The unified bottom-sheet molde for every Home state — browse, selected
 * parking, selected spot, add parking, add spot (and the AddingZone escape
 * hatch). One anatomy, five optional slots: [UI-SHEET-001]
 *
 *  ┌──────────────────────────────────────────────────────────────────────┐
 *  │ ‹  [LEAD]  EYEBROW (state-tinted caps)                [× | pill]   › │
 *  │     46dp   Title — 1 line, ellipsis (the address)                    │
 *  │            subtitle (optional, muted)                                │
 *  ├──────────────────────────────────────────────────────────────────────┤
 *  │  banner  — info strip (icon + title + sub) on surfaceContainerHigh   │
 *  │  meta    — icon+value rows       [metaAction: 38dp edit icon-button] │
 *  │  chips   — filter / size selector row                                │
 *  │  content — escape hatch (forms)                                      │
 *  │  actions — PapFooterButton stack (max 1 Filled = the loop action)    │
 *  └──────────────────────────────────────────────────────────────────────┘
 *
 * **Subject rule** (decided by the caller): the lead tile is the SUBJECT of
 * the sheet. With a parked car the subject is the vehicle (lead = car glyph,
 * free-spot count moves to the trailing pill); with no parked car the subject
 * is the zone (lead = counter tile).
 */
@Composable
internal fun PapSheet(
    lead: PapSheetLead,
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    eyebrowTone: PapSheetEyebrowTone = PapSheetEyebrowTone.Neutral,
    /** Overrides the tone colour — the reliability palette of a community spot. */
    eyebrowColor: Color? = null,
    /** Substring of [eyebrow] (the vehicle NAME) tinted with [eyebrowHighlightColor]; the state
     *  words around it keep the base eyebrow colour. [UI-COLOR-DOCTRINE-001] */
    eyebrowHighlight: String? = null,
    eyebrowHighlightColor: Color? = null,
    subtitle: String? = null,
    /** Modal-only escape hatch: peeks keep the fixed 1-line title (anchoring depends on the
     *  reserved header height); a MODAL sheet whose title carries a vehicle name may pass 2 so
     *  "¿Has aparcado el Škoda Kamiq?" doesn't truncate. [UX-PARK-FLOW-001 C4, device 06-08] */
    titleMaxLines: Int = 1,
    /**
     * Peeks keep ONE line: their header height is a fixed design derivation that the collapse cut
     * depends on. A sheet that exists to EXPLAIN something passes Int.MAX_VALUE — there the height
     * is free, and truncating prose with a "…" while half the screen is empty is the opposite of
     * explaining. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] [UI-SHEET-006]
     */
    subtitleMaxLines: Int = 1,
    trailing: PapSheetTrailing? = PapSheetTrailing.Dismiss,
    /** Pager chrome: the ‹ / › that open the pin before/after this one. Null (the default) leaves
     *  the header at full width, exactly as it was before the stepper existed.
     *  [UI-PEEK-STEPS-BETWEEN-PINS-001] */
    stepper: PapSheetStepper? = null,
    banner: (@Composable () -> Unit)? = null,
    meta: (@Composable ColumnScope.() -> Unit)? = null,
    metaAction: (@Composable () -> Unit)? = null,
    chips: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .pagerSwipe(stepper)
            .padding(horizontal = PAP_SHEET_HORIZONTAL_PAD_DP.dp),
    ) {

        // ── Slot 1 · Header — same leading + overline + title + subtitle + trailing anatomy as
        // every other row, delegated to the shared PapListItem. The height is RESERVED at the
        // 3-line size (eyebrow + title + subtitle) in every state — 2-line headers breathe —
        // so the sheet's collapsed "header band" is one FIXED design derivation and a collapse
        // cut always lands exactly under the header. [UI-LIST-ITEM-002] [UI-SHEET-006]
        //
        // The pager chevrons join the × in the TRAILING cluster of this same row — chrome next to
        // chrome, so the peek's actions stay actions. A 32dp chevron is shorter than the 46dp lead
        // tile, so the reserved header height (and with it the peek's measured height, the collapse
        // cut and the peek/nav divider) is untouched. [UI-PEEK-STEPS-BETWEEN-PINS-001] [UI-SHEET-006]
        PapListItem(
            modifier = Modifier.defaultMinSize(minHeight = papSheetHeaderReservedHeight()),
            // Blank = no eyebrow at all, not an empty line with its spacing. An embedded sheet (the
            // first-step explainer inside Home's sheet) drops it so the surface does not read as two
            // stacked headers. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
            overline = eyebrow.takeIf { it.isNotBlank() },
            overlineColor = eyebrowColor ?: eyebrowTone.color(),
            overlineStyle = PaparcarType.current.eyebrow,
            overlineHighlight = eyebrowHighlight,
            overlineHighlightColor = eyebrowHighlightColor ?: (eyebrowColor ?: eyebrowTone.color()),
            title = title,
            titleStyle = PaparcarType.current.cardTitle,
            titleMaxLines = titleMaxLines,
            subtitle = subtitle,
            subtitleStyle = PaparcarType.current.caption,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            subtitleMaxLines = subtitleMaxLines,
            // Horizontal inset already applied by the parent Column; keep the sheet's own top/bottom.
            contentPadding = PaddingValues(top = 12.dp, bottom = 14.dp),
            gap = 12.dp,
            leading = { PapSheetLeadTile(lead) },
            trailing = papSheetHeaderTrailing(trailing, stepper, onDismiss),
        )

        // ── Slot 2 · Banner ───────────────────────────────────────────────
        if (banner != null) {
            banner()
            Spacer(Modifier.height(14.dp))
        }

        // ── Slot 3 · Meta rows + optional edit action ─────────────────────
        if (meta != null || metaAction != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    meta?.invoke(this)
                }
                if (metaAction != null) {
                    Spacer(Modifier.width(12.dp))
                    metaAction()
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── Slot 4 · Chips ────────────────────────────────────────────────
        if (chips != null) {
            chips()
            Spacer(Modifier.height(14.dp))
        }

        // ── Escape hatch (forms — AddingZone) ─────────────────────────────
        content?.invoke(this)

        // ── Slot 5 · Actions ──────────────────────────────────────────────
        actions?.invoke(this)

        // Bottom air only when the sheet has a body — a bare header (browse
        // collapsed) keeps the tight peek rhythm so the peek/nav divider
        // stays seated. [BUG-PEEK-DIVIDER-ALIGN]
        val hasBody = banner != null || meta != null || metaAction != null ||
            chips != null || content != null || actions != null
        if (hasBody) Spacer(Modifier.height(16.dp))
    }
}

/**
 * The pager chrome of a sheet header: which pin the ‹ / › open, or null on the side where there is
 * none. Both null ⇒ the header renders untouched, at full width. [UI-PEEK-STEPS-BETWEEN-PINS-001]
 */
internal data class PapSheetStepper(
    val prevContentDescription: String,
    val nextContentDescription: String,
    val onPrev: (() -> Unit)?,
    val onNext: (() -> Unit)?,
) {
    val hasAny: Boolean get() = onPrev != null || onNext != null
}

/**
 * The header's trailing cluster: `‹ › ×`. The chevrons come FIRST and the dismiss × stays last,
 * where the user already reaches for it — no state loses its way out. [UI-PEEK-STEPS-BETWEEN-PINS-001]
 *
 * The three don't look alike on purpose: a filled circle acts on THIS card (dismiss), a bare glyph
 * moves BETWEEN cards. With three identical tonal pills in a row, the × and the › read as twins and
 * closing by accident is one mis-tap away.
 */
private fun papSheetHeaderTrailing(
    trailing: PapSheetTrailing?,
    stepper: PapSheetStepper?,
    onDismiss: () -> Unit,
): (@Composable () -> Unit)? {
    val pager = stepper?.takeIf { it.hasAny }
    if (pager == null && trailing == null) return null
    return {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (pager != null) {
                // The pair is ONE control, so they sit tight together and the air goes before the ×.
                PapStepperSlot(
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = pager.prevContentDescription,
                    onClick = pager.onPrev,
                )
                PapStepperSlot(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = pager.nextContentDescription,
                    onClick = pager.onNext,
                )
                if (trailing != null) Spacer(Modifier.width(STEPPER_TO_DISMISS_GAP_DP.dp))
            }
            when (trailing) {
                PapSheetTrailing.Dismiss -> PapSheetDismissButton(onDismiss = onDismiss)
                null -> Unit
            }
        }
    }
}

/**
 * Swiping the card is the chevrons' gesture twin: dragging LEFT pulls the next pin in from the right
 * (same as ›), dragging RIGHT the previous one (same as ‹) — and the header's page-turn plays the
 * matching slide, so finger and motion agree. Mirrors the history detail's gesture verbatim.
 * [UI-PEEK-STEPS-BETWEEN-PINS-001] [HISTORY-DETAIL-002]
 *
 * Horizontal only: it waits for HORIZONTAL touch slop, so the sheet's own vertical drag underneath
 * still gets every up/down gesture. Trigger-on-release with a distance threshold, so taps on the
 * card's buttons pass through untouched.
 */
@Composable
private fun Modifier.pagerSwipe(stepper: PapSheetStepper?): Modifier {
    if (stepper == null || !stepper.hasAny) return this
    val latest by rememberUpdatedState(stepper)
    return this.pointerInput(Unit) {
        var dragged = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragged = 0f },
            onDragEnd = {
                val threshold = SWIPE_TRIGGER_DP.dp.toPx()
                when {
                    dragged <= -threshold -> latest.onNext?.invoke()
                    dragged >= threshold -> latest.onPrev?.invoke()
                }
            },
        ) { _, dragAmount -> dragged += dragAmount }
    }
}

/**
 * The 46dp rounded-square lead tile — ALWAYS boxed, never a bare glyph. The
 * variant is the sheet's subject; its container colour is the state colour
 * (own = surface/green, manual report = green announce, community = blue).
 */
internal sealed interface PapSheetLead {
    /** The user's vehicle — full-colour illustration on a quiet surface tile. [loading] shows a
     *  skeleton instead of the pictogram while the vehicle is still resolving from Room, so the
     *  tile never flashes the generic fallback car before the real one arrives. */
    data class Vehicle(
        val carbody: CarbodyType?,
        val size: VehicleSize?,
        val color: VehicleColor? = null,
        val loading: Boolean = false,
        /** Non-null ⇒ this car's trip is running RIGHT NOW: the tile breathes the radar halo in
         *  this colour, the same motion the vehicle chip already shows. Pass the vehicle's identity
         *  colour, resolved at the call site by the one resolver (`vehicleIdentityColor`) — the tile
         *  never re-derives it. Null (default) ⇒ a still tile. [UI-PEEK-DRIVING-HAS-NO-MOTION-001] */
        val drivingHaloColor: Color? = null,
    ) : PapSheetLead

    /** Free-spot counter — digit + unit. Green with n>0, amber with 0. */
    data class SpotCounter(val count: Int) : PapSheetLead

    /** A community spot — the SAME reliability puck drawn on the map marker and the
     *  spot list row (colour + TTL ring + badge encode the tier), never a flat "P".
     *  [HOME-PUCK-001] */
    data class CommunitySpot(
        val reliability: SpotFreshness,
        val enRouteCount: Int = 0,
        /** Eyewitness report — person badge over the freshness tier. [UI-COLOR-DOCTRINE-001 F5] */
        val isManual: Boolean = false,
    ) : PapSheetLead

    /** Reporting a free spot — megaphone on the action-green tile. */
    data object Announce : PapSheetLead

    /** Generic escape (AddingZone's picked icon). */
    data class GenericIcon(val icon: ImageVector) : PapSheetLead
}

/** Eyebrow tint: green = own action, muted = neutral context. (The old blue "Manual" tone died
 *  with the tertiary retirement — reporting a spot IS an action, and a report's provenance is the
 *  person badge's job, not a colour's.) [UI-COLOR-DOCTRINE-001 F6] */
internal enum class PapSheetEyebrowTone { Action, Neutral }

@Composable
private fun PapSheetEyebrowTone.color(): Color = when (this) {
    // The eyebrow is a WORD, so it takes the readable leg of the brand green.
    // [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001]
    PapSheetEyebrowTone.Action -> PapColor.actionText
    PapSheetEyebrowTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Header trailing: dismiss × in modal states, nothing in browse. */
internal sealed interface PapSheetTrailing {
    data object Dismiss : PapSheetTrailing
}

@Composable
private fun PapSheetLeadTile(lead: PapSheetLead) {
    val cs = MaterialTheme.colorScheme
    when (lead) {
        is PapSheetLead.Vehicle -> LeadTileBox(container = cs.surfaceContainerHigh) {
            if (lead.loading) {
                // Vehicle not resolved yet: breathe a placeholder instead of flashing the generic
                // fallback car (the tile would otherwise show a wrong default for one frame). [UI-VEHICLE-ICON-SKELETON-001]
                PapShimmerBox(modifier = Modifier.size(LEAD_GLYPH_DP.dp), shape = CircleShape)
            } else {
                Box(contentAlignment = Alignment.Center) {
                    // A running trip breathes the SAME halo as the vehicle chip's identity row, at
                    // the same glyph-to-halo proportion — one motion vocabulary across both
                    // surfaces. Contained in the glyph box, so the tile never resizes.
                    // [UI-PEEK-DRIVING-HAS-NO-MOTION-001] [UI-COLOR-DOCTRINE-001]
                    lead.drivingHaloColor?.let { DrivingRadarHalo(diameter = LEAD_GLYPH_DP.dp, color = it) }
                    // Full-colour brand illustration (level-3) — never tinted. [INACTIVE-OPAQUE-001]
                    VehicleGlyph(
                        carbody = lead.carbody,
                        size = lead.size,
                        glyphSize = LEAD_GLYPH_DP.dp,
                        color = lead.color,
                    )
                }
            }
        }

        is PapSheetLead.SpotCounter -> {
            val hasSpots = lead.count > 0
            val accent = if (hasSpots) PapColor.brandData else PapColor.attention
            LeadTileBox(container = if (hasSpots) cs.primaryContainer else cs.secondaryContainer) {
                // Centrar la CAJA de texto no centra lo que se ve: encima del dígito sobra el
                // ascenso que no usa, y debajo de las mayúsculas sobra el descenso. Con Jakarta ese
                // hueco es 0.29 em y el bloque quedaba pegado al borde inferior del tile. La
                // corrección sale de las métricas de la familia, así que sigue valiendo si la
                // familia cambia. [UI-SHEET-001] [UI-TYPE-FAMILY-CANDIDATES-001]
                val type = PaparcarType.current
                // El lift sale en SP porque lo dictan las metricas de la letra, y se aplica en DP
                // porque es un desplazamiento de layout: convertirlo con la densidad es lo que hace
                // que siga valiendo cuando el usuario agranda el tipo del sistema. Tomarlo como dp
                // sin convertir coincide solo con fontScale = 1.0; con la letra grande el hueco
                // muerto crece y la correccion no, asi que el par volvia a hundirse — el mismo
                // sintoma que [UI-SHEET-001] habia arreglado. [UI-TYPE-SYSTEM-HYGIENE-001]
                val opticalLift = with(LocalDensity.current) {
                    PapFonts.current.figureOpticalLiftSp(
                        figureSp = type.counter.fontSize.value,
                        unitSp = type.counterUnit.fontSize.value,
                    ).sp.toDp()
                }
                Column(
                    modifier = Modifier.offset(y = -opticalLift),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${lead.count}",
                        style = PaparcarType.current.counter,
                        color = accent,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(Res.string.home_counter_unit_free).uppercase(),
                        style = PaparcarType.current.counterUnit,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        is PapSheetLead.CommunitySpot ->
            // Bare (no tile box): the puck is a self-coloured level-3 marker, exactly as it
            // renders on the map and in the spot list — one shared subject across all three
            // surfaces. [HOME-PUCK-001]
            SpotPuckIcon(
                reliability = lead.reliability,
                enRouteCount = lead.enRouteCount,
                isManual = lead.isManual,
                modifier = Modifier.size(LEAD_TILE_DP.dp),
            )

        PapSheetLead.Announce -> LeadTileBox(container = cs.primaryContainer) {
            Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(LEAD_ICON_DP.dp),
            )
        }

        is PapSheetLead.GenericIcon -> LeadTileBox(container = cs.primaryContainer) {
            Icon(
                imageVector = lead.icon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(LEAD_ICON_DP.dp),
            )
        }
    }
}

@Composable
private fun LeadTileBox(container: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(LEAD_TILE_DP.dp)
            .clip(PapShapes.cardSmall)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Slot-2 banner — info strip on `surfaceContainerHigh` with a small circular
 * icon badge. Replaces the old `HelperRow`.
 */
@Composable
internal fun PapSheetBanner(
    title: String,
    modifier: Modifier = Modifier,
    /** Two lines by default (it sits under a header whose height is derived); an explainer passes
     *  Int.MAX_VALUE so the caveat is READ, not cut mid-sentence.
     *  [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] */
    maxLines: Int = 2,
    subtitle: String? = null,
    icon: ImageVector = Icons.Rounded.Info,
    iconTint: Color = PapColor.attention,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PapShapes.cardSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(BANNER_BADGE_DP.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = BANNER_BADGE_BG_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(BANNER_ICON_DP.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = PaparcarType.current.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Twin round icon-button for the meta-action row — a bare 38dp circle with a green
 * outline and a primary-tinted glyph. Groups low-emphasis utilities (navigate to the
 * car, edit the pin) side by side, so the full-width footer stays for the ONE loop
 * action ("I'm leaving"). [contentDescription] replaces the visible label for
 * accessibility. [UX-PARKED-STATE-001]
 */
@Composable
internal fun PapSheetRoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(SHEET_ICON_BUTTON_DP.dp)
            .clip(CircleShape)
            .border(BorderStroke(PapBorders.medium, greenOutline), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(SHEET_ICON_GLYPH_DP.dp),
        )
    }
}

/** Dismiss × — 34dp circle on `surfaceContainerHigh` so it reads as a real control. */
@Composable
private fun PapSheetDismissButton(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .size(DISMISS_BUTTON_DP.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Close,
            contentDescription = stringResource(Res.string.home_peek_dismiss_cd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(DISMISS_ICON_DP.dp),
        )
    }
}

/** Horizontal content inset of every sheet state — the 16dp sheet grid, so text
 *  doesn't step sideways when the sheet expands. [HOME-VEH-REFINE-001] */
internal const val PAP_SHEET_HORIZONTAL_PAD_DP = 16

/**
 * The collapsed "header band" of the sheet — drag pill block + the reserved 3-line
 * header. A design DERIVATION, not a measurement: deterministic for a given font
 * scale, and font-scale AWARE (the text stack is sp) so a larger system font never
 * clips the 46dp lead tile out of the band. [UI-SHEET-006]
 */
@Composable
internal fun papSheetHeaderBandHeight(): Dp =
    // The cut bites into the header's BOTTOM PADDING (14dp) instead of landing exactly on its
    // edge: with a zero-margin cut, the next block starts on the very cut line and any rounding
    // (or a meta icon standing taller than its text line) leaks a sliver above the nav. 8dp of
    // clearance keeps everything below the header reliably hidden. [UI-SHEET-006]
    PILL_BLOCK_DP.dp + papSheetHeaderReservedHeight() - BAND_BOTTOM_CLEARANCE_DP.dp

/** Reserved header min-height — vertical padding + max(lead tile, 3-line text stack).
 *  The RESERVE stays full-size; only the band cut above subtracts clearance. */
@Composable
private fun papSheetHeaderReservedHeight(): Dp {
    val textStack = with(LocalDensity.current) { HEADER_TEXT_STACK_SP.sp.toDp() } + HEADER_OVERLINE_GAP_DP.dp
    return HEADER_V_PAD_DP.dp + max(LEAD_TILE_DP.dp, textStack)
}

// Text stack of a 3-line header: eyebrow lh 14sp + title lh 24sp + caption lh 16sp.
// Keep in sync with the header roles used above.
private const val HEADER_TEXT_STACK_SP = 54
// PapListItem's overline→title gap (dp).
private const val HEADER_OVERLINE_GAP_DP = 2
// Header contentPadding: top 12 + bottom 14.
private const val HEADER_V_PAD_DP = 26
// Drag pill block above the header: top 8 + pill 4 + bottom 2. Keep in sync with HomePeekHandle.
internal const val PILL_BLOCK_DP = 14
// How far the band cut bites into the header's bottom padding (< HEADER_V_PAD_DP's bottom 14).
private const val BAND_BOTTOM_CLEARANCE_DP = 8

// Air between the chevron pair and the dismiss ×. Enough that the two stop reading as one strip of
// buttons; the chevrons themselves sit tight together, because they ARE one control.
private const val STEPPER_TO_DISMISS_GAP_DP = 10

// Swipe distance that commits a page step — same threshold as the history detail's, so the two
// surfaces need the same flick. Comfortably above tap slop, well below half the card's width.
private const val SWIPE_TRIGGER_DP = 64

// Component dimensions (not spacing tokens — they belong to this molde).
private const val LEAD_TILE_DP = 46
private const val LEAD_GLYPH_DP = 38
private const val LEAD_ICON_DP = 24
private const val DISMISS_BUTTON_DP = 34
private const val DISMISS_ICON_DP = 18
private const val SHEET_ICON_BUTTON_DP = 38
private const val SHEET_ICON_GLYPH_DP = 18
private const val BANNER_BADGE_DP = 20
private const val BANNER_ICON_DP = 13
private const val BANNER_BADGE_BG_ALPHA = 0.18f
