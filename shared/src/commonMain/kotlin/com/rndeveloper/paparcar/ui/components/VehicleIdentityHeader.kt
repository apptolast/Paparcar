package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.my_car_unnamed_vehicle

/**
 * Shared identity header for a vehicle card — ONE anatomy consumed by the Vehicles ficha and the
 * Home single-vehicle card so gaps, sizes and the reading order (status → name → carbody · size)
 * are defined in exactly one place:
 *
 * ```
 * [ tile glyph ]  ◉ Name (MARCA, cardTitle)                   [trailing action]
 *                 Sedán · Mediano  (quiet metadata, no chips)
 * ```
 *
 * **NO boxed element and no method label.** The watch tier is a glyph immediately before the name
 * ([VehicleWatchLeadingIcon]) in the identity colour — the exact anatomy of the Home vehicle chip,
 * which is what `HOME-VEH-REFINE-001` meant by *"status is colour-only, never a method label"*.
 *
 * The pill that used to live here (`ACTIVO` / `BT` in a tonal box, per `CARD-ONE-BADGE-001`) was the
 * deviation, and it cost twice: the LENGTH of the status word decided how much width was left for
 * the name — `ACTIVO` wrapped a 9-char "Oppo Test" into two lines while `BT` left a 12-char "Skoda
 * Kamiq" whole — and it said in words what the glyph and the colour already said. The word is not
 * lost, it changed channel: it is now the glyph's `contentDescription`, so a screen reader still
 * speaks the tier that colour alone could not carry.
 * [UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001] [UI-COLOR-DOCTRINE-001]
 *
 * The illustration sits on a rounded tonal tile so every carbody (a low coupé, a thin motorcycle)
 * carries the same visual weight. Status is colour-only, never a method label. [HOME-VEH-REFINE-001]
 */
@Composable
fun VehicleIdentityHeader(
    vehicle: Vehicle,
    modifier: Modifier = Modifier,
    // While a trip is being detected, the en-route radar halo pulses behind the glyph.
    isDriving: Boolean = false,
    // The carbody · size subtitle is a detail-screen attribute — the Vehicles ficha shows it; the
    // glanceable Home card hides it (name → status → parked-at is what matters). The status badge
    // itself always shows. [CARD-META-POLISH-001]
    showSize: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val watch = vehicle.monitoringStatus().watch()
    // The vehicle's identity colour — its watch method — paints the NAME itself.
    // [UI-COLOR-DOCTRINE-001]
    val identity = vehicleIdentityColor(watch)
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(TILE_DP.dp)
                .clip(RoundedCornerShape(TILE_CORNER_DP.dp))
                .background(cs.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (isDriving) DrivingRadarHalo(diameter = GLYPH_DP.dp, color = identity)
            VehicleGlyph(
                carbody = vehicle.carbodyType,
                size = vehicle.sizeCategory,
                glyphSize = GLYPH_DP.dp,
                color = vehicle.color,
            )
        }
        Spacer(Modifier.width(TILE_TEXT_GAP_DP.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Identity line — watch glyph, then the name. Same order and same gap as the Home chip:
            // the tier is a SHAPE (radar / Bluetooth / hollow ring) in the identity colour, never a
            // word and never a box, so a long status can no longer eat the name's width.
            // [UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WATCH_NAME_GAP_DP.dp),
            ) {
                // The word the pill used to show survives HERE, as the glyph's description: shape
                // and colour say the tier to the eye, this says it to a screen reader — and to
                // anyone for whom green and grey are the same colour.
                VehicleWatchLeadingIcon(
                    watch = watch,
                    contentDescription = vehicleWatchPinLabel(watch),
                )
                // Name stays onSurface — the glyph beside it already wears the identity colour;
                // tinting both is over-information. [UI-COLOR-DOCTRINE-001]
                //
                // `maxLines = 2` stays for a genuinely long name ("Volkswagen Transporter"): a name
                // is identity and an ellipsis loses it, so wrapping is the better failure. What the
                // glyph removed is the wrap CAUSED by the status, not the ability to wrap.
                Text(
                    text = vehicle.displayName(fallback = stringResource(Res.string.my_car_unnamed_vehicle)),
                    style = PaparcarType.current.cardTitle,
                    color = cs.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Quiet descriptive subtitle (carbody · size) — no chips. Detail-screen only.
            if (showSize) {
                Spacer(Modifier.height(NAME_META_GAP_DP.dp))
                val subtitle = listOfNotNull(
                    vehicle.carbodyType?.label(),
                    vehicleSizeLabel(vehicle.sizeCategory),
                ).joinToString(SUBTITLE_SEPARATOR)
                Text(
                    text = subtitle,
                    // App convention for an icon·title·subtitle row: subtitle is quiet PROSE (LECTURA
                    // caption), same as PapListItem — NOT a CIFRA data token. [CARD-ONE-BADGE-001]
                    style = PaparcarType.current.caption,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(TRAILING_GAP_DP.dp))
            trailing()
        }
    }
}

private const val TILE_DP = 56
private const val TILE_CORNER_DP = 14
private const val GLYPH_DP = 44
private const val TILE_TEXT_GAP_DP = 12
private const val NAME_META_GAP_DP = 6
// 6 — the Home chip's glyph-to-name gap (`CHIP_TOP_GAP_DP`), duplicated as a value rather than
// shared: it is the same NUMBER, not the same decision, and the chip owns its own rhythm.
// [UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001]
private const val WATCH_NAME_GAP_DP = 6
private const val TRAILING_GAP_DP = 8
private const val SUBTITLE_SEPARATOR = " · "
