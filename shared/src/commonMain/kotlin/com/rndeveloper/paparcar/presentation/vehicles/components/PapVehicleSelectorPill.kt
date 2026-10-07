package com.rndeveloper.paparcar.presentation.vehicles.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.ui.components.VehicleIcon
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
import com.rndeveloper.paparcar.ui.theme.PapControlHeight
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.my_car_unnamed_vehicle
import paparcar.composeapp.generated.resources.vehicle_status_active_cd

/**
 * "Which car are we talking about" — THE answer, for every screen that asks.
 * [UI-STATS-ONE-QUESTION-ONE-COMPONENT-001]
 *
 * ⛔ **One question, one component.** This was private to `VehiclesScreen`, so the stats panel grew
 * its own selector out of bare `PaparcarFilterChip`s — no glyph, no identity colour, no watch dot,
 * and the name in the chip's voice instead of MARCA. The same question answered twice, and the
 * second answer dropped half of [UI-COLOR-DOCTRINE-001] on the floor: *el color del NOMBRE de un
 * coche dice CÓMO se le vigila*, resolved in ONE place by `vehicleIdentityColor`.
 *
 * Visual contract, aligned with [com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip]:
 * neutral outline, no neon-primary border when selected, and an identity dot the base chip does not
 * need.
 */
@Composable
internal fun PapVehicleSelectorPill(
    vehicle: Vehicle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tabName = vehicle.displayName(
        fallback = stringResource(Res.string.my_car_unnamed_vehicle),
    )
    // The selected pill wears ITS vehicle's identity colour — the watch method (green = active
    // detection, blue = BT, grey = unwatched), same colour as the name everywhere else.
    // [UI-COLOR-DOCTRINE-001]
    val watch = vehicle.monitoringStatus().watch()
    val accent = vehicleIdentityColor(watch)
    val bg = if (selected) accent.copy(alpha = SELECTED_FILL_ALPHA) else cs.surfaceContainerHigh
    // Name stays onSurface — the fill/border and the watch dot already carry the identity colour.
    val fg = cs.onSurface

    Surface(
        onClick = onClick,
        modifier = modifier.height(PapControlHeight.chip),
        shape = RoundedCornerShape(PILL_RADIUS_DP.dp),
        color = bg,
        // Selected = tonal fill + MUTED accent border, honouring the "no neon border on selected"
        // contract this pill previously violated. [UI-REGRESSION]
        border = if (selected) BorderStroke(PapBorders.strong, accent.copy(alpha = SELECTED_BORDER_ALPHA))
        else BorderStroke(PapBorders.thin, cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PILL_H_PAD_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PILL_GAP_DP.dp),
        ) {
            VehicleIcon(
                carbody = vehicle.carbodyType,
                size = vehicle.sizeCategory,
                tint = Color.Unspecified, // native multi-colour silhouette [BOLT-MARKERS-001]
                color = vehicle.color,
                modifier = Modifier.size(GLYPH_DP.dp),
            )
            Text(
                text = tabName,
                // The vehicle NAME is identity → MARCA (rowName) everywhere, including this selector
                // pill, so the name reads in one voice across card/header/selector. [CARD-ONE-BADGE-001]
                style = PaparcarType.current.rowName,
                color = fg,
                maxLines = 1,
            )
            VehicleWatchDot(watch = watch)
        }
    }
}

/** Small watch dot — the vehicle's identity colour (green = active detection, blue = BT) when
 *  monitored, absent when it is not. The selector pill has no room for a border or a glyph, so the
 *  dot carries the method alone; it reads the same single resolver as every other surface.
 *  [HOME-VEH-REFINE-001] [UI-COLOR-DOCTRINE-001] */
@Composable
private fun VehicleWatchDot(watch: VehicleWatch) {
    if (watch == VehicleWatch.Off) return // no dot for an unwatched vehicle
    val color = vehicleIdentityColor(watch)
    val cd = stringResource(Res.string.vehicle_status_active_cd)
    Box(
        modifier = Modifier
            .size(ACTIVE_DOT_DP.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = cd },
    )
}

/** The pill's own height, so a row of them lines up with whatever sits beside it (the "+" chip in
 *  Vehicles, nothing in the panel). */

private const val PILL_RADIUS_DP = 999
private const val PILL_H_PAD_DP = 12
private const val PILL_GAP_DP = 6
private const val GLYPH_DP = 20
private const val ACTIVE_DOT_DP = 6
private const val SELECTED_FILL_ALPHA = 0.14f
private const val SELECTED_BORDER_ALPHA = 0.45f // muted "green-line", not the neon primary
