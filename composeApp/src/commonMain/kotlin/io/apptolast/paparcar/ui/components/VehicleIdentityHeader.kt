package io.apptolast.paparcar.ui.components

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
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.model.monitoringStatus
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.my_car_unnamed_vehicle

/**
 * Shared identity header for a vehicle card — ONE anatomy consumed by the Vehicles ficha and the
 * Home single-vehicle card so gaps, sizes and the meta order (brand·model → size → status) are
 * defined in exactly one place:
 *
 * ```
 * [ tile glyph ]  Name (Outfit, titleMedium Bold)
 *                 [SIZE] ● STATUS                       [trailing action]
 * ```
 *
 * The illustration sits on a rounded tonal tile so every carbody (a low coupé, a thin motorcycle)
 * carries the same visual weight. Status is colour-only (pin), never a method label.
 * [HOME-VEH-REFINE-001]
 */
@Composable
fun VehicleIdentityHeader(
    vehicle: Vehicle,
    modifier: Modifier = Modifier,
    // While a trip is being detected, the en-route radar halo pulses behind the glyph.
    isDriving: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val monitoring = vehicle.monitoringStatus()
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
            if (isDriving) DrivingRadarHalo(diameter = GLYPH_DP.dp)
            VehicleGlyph(
                carbody = vehicle.carbodyType,
                size = vehicle.sizeCategory,
                glyphSize = GLYPH_DP.dp,
                color = vehicle.color,
            )
        }
        Spacer(Modifier.width(TILE_TEXT_GAP_DP.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vehicle.displayName(fallback = stringResource(Res.string.my_car_unnamed_vehicle)),
                style = PaparcarType.current.cardTitle,
                color = cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(NAME_META_GAP_DP.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(META_GAP_DP.dp),
            ) {
                // Order = brand·model → size → status. The size chip keeps its single-line width;
                // the pin yields (weight, ellipsis) if space runs out.
                SizeChip(label = vehicleSizeLabel(vehicle.sizeCategory))
                VehicleStatusTextPin(
                    status = monitoring,
                    label = vehicleStatusPinLabel(monitoring),
                    modifier = Modifier.weight(1f, fill = false),
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
private const val NAME_META_GAP_DP = 8
private const val META_GAP_DP = 8
private const val TRAILING_GAP_DP = 8
