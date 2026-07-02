package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.ui.theme.rememberDataTypography
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van

/**
 * The vehicle size shown as a small tonal chip (not plain grey text) so the token reads the same
 * across the Vehicles ficha and the Home single-vehicle card. Display-only — never selectable here.
 * [HOME-VEH-REFINE-001]
 */
@Composable
fun SizeChip(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(SIZE_CHIP_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.padding(horizontal = SIZE_CHIP_H_PAD.dp, vertical = SIZE_CHIP_V_PAD.dp),
            // Condensed data slot — the size token stays narrow so it never crowds the status pin
            // beside it (and never breaks "Mediano" into "M/ed/ia/no"). [HOME-VEH-REFINE-001]
            style = rememberDataTypography().sizeBadge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun vehicleSizeLabel(size: VehicleSize): String = when (size) {
    VehicleSize.MOTORCYCLE  -> stringResource(Res.string.vehicle_size_moto)
    VehicleSize.MICRO_SMALL -> stringResource(Res.string.vehicle_size_small)
    VehicleSize.MEDIUM_SUV  -> stringResource(Res.string.vehicle_size_medium)
    VehicleSize.LARGE_SEDAN -> stringResource(Res.string.vehicle_size_large)
    VehicleSize.VAN_HIGH    -> stringResource(Res.string.vehicle_size_van)
}

private const val SIZE_CHIP_RADIUS_DP = 999
private const val SIZE_CHIP_H_PAD = 10
private const val SIZE_CHIP_V_PAD = 4
