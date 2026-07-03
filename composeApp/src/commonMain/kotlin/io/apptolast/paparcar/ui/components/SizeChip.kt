package io.apptolast.paparcar.ui.components

import androidx.compose.runtime.Composable
import io.apptolast.paparcar.domain.model.VehicleSize
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van

/**
 * Length-based size label for a vehicle. Once rendered as a tonal chip beside the status; now the
 * size is quiet subtitle text in [VehicleIdentityHeader] (the status badge is the card's only boxed
 * element), so this survives purely as the label lookup. [CARD-ONE-BADGE-001]
 */
@Composable
fun vehicleSizeLabel(size: VehicleSize): String = when (size) {
    VehicleSize.MOTORCYCLE  -> stringResource(Res.string.vehicle_size_moto)
    VehicleSize.MICRO_SMALL -> stringResource(Res.string.vehicle_size_small)
    VehicleSize.MEDIUM_SUV  -> stringResource(Res.string.vehicle_size_medium)
    VehicleSize.LARGE_SEDAN -> stringResource(Res.string.vehicle_size_large)
    VehicleSize.VAN_HIGH    -> stringResource(Res.string.vehicle_size_van)
}
