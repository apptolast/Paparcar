package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.VehicleColor
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.ui.components.PapAlertDialog
import io.apptolast.paparcar.ui.components.PapDialogAccent
import io.apptolast.paparcar.ui.components.PapListItem
import io.apptolast.paparcar.ui.components.SpotPuckIcon
import io.apptolast.paparcar.ui.components.VehicleGlyph
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PaparcarType
import io.apptolast.paparcar.ui.theme.greenOutline
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_counter_unit_free
import paparcar.composeapp.generated.resources.home_parking_action_move_location
import paparcar.composeapp.generated.resources.home_parking_edit_dialog_body
import paparcar.composeapp.generated.resources.home_parking_edit_dialog_title
import paparcar.composeapp.generated.resources.home_parking_edit_menu_cd
import paparcar.composeapp.generated.resources.home_parking_menu_delete
import paparcar.composeapp.generated.resources.home_peek_dismiss_cd
import paparcar.composeapp.generated.resources.home_release_dialog_cancel

/**
 * The unified bottom-sheet molde for every Home state — browse, selected
 * parking, selected spot, add parking, add spot (and the AddingZone escape
 * hatch). One anatomy, five optional slots: [UI-SHEET-001]
 *
 *  ┌──────────────────────────────────────────────────────────────────────┐
 *  │ [LEAD]  EYEBROW (state-tinted caps)                      [× | pill]  │
 *  │  46dp   Title — 1 line, ellipsis (the address)                       │
 *  │         subtitle (optional, muted)                                   │
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
    subtitle: String? = null,
    trailing: PapSheetTrailing? = PapSheetTrailing.Dismiss,
    banner: (@Composable () -> Unit)? = null,
    meta: (@Composable ColumnScope.() -> Unit)? = null,
    metaAction: (@Composable () -> Unit)? = null,
    chips: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.padding(horizontal = PAP_SHEET_HORIZONTAL_PAD_DP.dp)) {

        // ── Slot 1 · Header — same leading + overline + title + subtitle + trailing anatomy as
        // every other row, delegated to the shared PapListItem. The height is RESERVED at the
        // 3-line size (eyebrow + title + subtitle) in every state — 2-line headers breathe —
        // so the sheet's collapsed "header band" is one FIXED design derivation and a collapse
        // cut always lands exactly under the header. [UI-LIST-ITEM-002] [UI-SHEET-006]
        PapListItem(
            modifier = Modifier.defaultMinSize(minHeight = papSheetHeaderReservedHeight()),
            overline = eyebrow,
            overlineColor = eyebrowColor ?: eyebrowTone.color(),
            overlineStyle = PaparcarType.current.eyebrow,
            title = title,
            titleStyle = PaparcarType.current.cardTitle,
            titleWeight = FontWeight.SemiBold,
            titleMaxLines = 1,
            subtitle = subtitle,
            subtitleStyle = PaparcarType.current.caption,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            subtitleMaxLines = 1,
            // Horizontal inset already applied by the parent Column; keep the sheet's own top/bottom.
            contentPadding = PaddingValues(top = 12.dp, bottom = 14.dp),
            gap = 12.dp,
            leading = { PapSheetLeadTile(lead) },
            trailing = when (trailing) {
                PapSheetTrailing.Dismiss -> ({ PapSheetDismissButton(onDismiss = onDismiss) })
                null -> null
            },
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
 * The 46dp rounded-square lead tile — ALWAYS boxed, never a bare glyph. The
 * variant is the sheet's subject; its container colour is the state colour
 * (own = surface/green, manual report = green announce, community = blue).
 */
internal sealed interface PapSheetLead {
    /** The user's vehicle — full-colour illustration on a quiet surface tile. */
    data class Vehicle(
        val carbody: CarbodyType?,
        val size: VehicleSize?,
        val color: VehicleColor? = null,
    ) : PapSheetLead

    /** Free-spot counter — digit + unit. Green with n>0, amber with 0. */
    data class SpotCounter(val count: Int) : PapSheetLead

    /** A community spot — the SAME reliability puck drawn on the map marker and the
     *  spot list row (colour + TTL ring + badge encode the tier), never a flat "P".
     *  [HOME-PUCK-001] */
    data class CommunitySpot(
        val reliability: SpotReliabilityUiState,
        val enRouteCount: Int = 0,
    ) : PapSheetLead

    /** Reporting a free spot — megaphone on the action-green tile. */
    data object Announce : PapSheetLead

    /** Generic escape (AddingZone's picked icon). */
    data class GenericIcon(val icon: ImageVector) : PapSheetLead
}

/** Eyebrow tint: green = own action/vehicle, blue = manual report, muted = neutral context. */
internal enum class PapSheetEyebrowTone { Action, Manual, Neutral }

@Composable
private fun PapSheetEyebrowTone.color(): Color = when (this) {
    PapSheetEyebrowTone.Action -> MaterialTheme.colorScheme.primary
    PapSheetEyebrowTone.Manual -> MaterialTheme.colorScheme.tertiary
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
            // Full-colour brand illustration (level-3) — never tinted. [INACTIVE-OPAQUE-001]
            VehicleGlyph(
                carbody = lead.carbody,
                size = lead.size,
                glyphSize = LEAD_GLYPH_DP.dp,
                color = lead.color,
            )
        }

        is PapSheetLead.SpotCounter -> {
            val hasSpots = lead.count > 0
            val accent = if (hasSpots) cs.primary else cs.secondary
            LeadTileBox(container = if (hasSpots) cs.primaryContainer else cs.secondaryContainer) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
    subtitle: String? = null,
    icon: ImageVector = Icons.Rounded.Info,
    iconTint: Color = MaterialTheme.colorScheme.secondary,
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
                style = PaparcarType.current.body,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Slot-3 meta action — the 38dp circular edit icon-button (pin+pencil, green
 * outline). Opens the SAME pin-positioning sheet as manual add-parking, in
 * edit mode — where "Delete record" lives as the integrated destructive
 * action. No intermediate menu/dialog. ONLY for the user's own parking,
 * never on community spots. [UI-SHEET-004]
 */
@Composable
internal fun PapSheetEditButton(
    onEdit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(EDIT_BUTTON_DP.dp)
            .clip(CircleShape)
            .border(BorderStroke(PapBorders.medium, greenOutline), CircleShape)
            .clickable(onClick = onEdit),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.EditLocationAlt,
            contentDescription = stringResource(Res.string.home_parking_edit_menu_cd),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(EDIT_ICON_DP.dp),
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

// Component dimensions (not spacing tokens — they belong to this molde).
private const val LEAD_TILE_DP = 46
private const val LEAD_GLYPH_DP = 38
private const val LEAD_ICON_DP = 24
private const val DISMISS_BUTTON_DP = 34
private const val DISMISS_ICON_DP = 18
private const val EDIT_BUTTON_DP = 38
private const val EDIT_ICON_DP = 18
private const val BANNER_BADGE_DP = 20
private const val BANNER_ICON_DP = 13
private const val BANNER_BADGE_BG_ALPHA = 0.18f
