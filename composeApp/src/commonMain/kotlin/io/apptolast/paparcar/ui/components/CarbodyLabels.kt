package io.apptolast.paparcar.ui.components

import androidx.compose.runtime.Composable
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.ParkingAlertKey
import io.apptolast.paparcar.domain.model.VehicleSize
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.carbody_family_long
import paparcar.composeapp.generated.resources.carbody_hatchback_medium
import paparcar.composeapp.generated.resources.carbody_hatchback_small
import paparcar.composeapp.generated.resources.carbody_pickup
import paparcar.composeapp.generated.resources.carbody_sedan
import paparcar.composeapp.generated.resources.carbody_suv_large
import paparcar.composeapp.generated.resources.carbody_suv_medium
import paparcar.composeapp.generated.resources.carbody_suv_small
import paparcar.composeapp.generated.resources.carbody_van_commercial
import paparcar.composeapp.generated.resources.carbody_van_light
import paparcar.composeapp.generated.resources.parking_alert_high_ceiling
import paparcar.composeapp.generated.resources.parking_alert_long_car
import paparcar.composeapp.generated.resources.parking_alert_standard
import paparcar.composeapp.generated.resources.parking_alert_wide_car
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van

/**
 * Compose-aware label lookups for the bidimensional vehicle taxonomy.
 *
 * Keeps the domain enums Compose-free: each enum maps to a `Res.string.X`
 * resolved at call time so the translated copy lives entirely in the
 * `composeResources/values…` resource folders.
 */
@Composable
fun CarbodyType.label(): String = stringResource(
    when (this) {
        CarbodyType.HATCHBACK_SMALL -> Res.string.carbody_hatchback_small
        CarbodyType.SUV_SMALL -> Res.string.carbody_suv_small
        CarbodyType.HATCHBACK_MEDIUM -> Res.string.carbody_hatchback_medium
        CarbodyType.SUV_MEDIUM -> Res.string.carbody_suv_medium
        CarbodyType.SEDAN -> Res.string.carbody_sedan
        CarbodyType.FAMILY_LONG -> Res.string.carbody_family_long
        CarbodyType.SUV_LARGE -> Res.string.carbody_suv_large
        CarbodyType.VAN_LIGHT -> Res.string.carbody_van_light
        CarbodyType.VAN_COMMERCIAL -> Res.string.carbody_van_commercial
        CarbodyType.PICKUP -> Res.string.carbody_pickup
    }
)

/**
 * Length-based size labels — reuses the historical `vehicle_size_*` keys
 * (text refreshed by Fase 6 to match the new five-tier nomenclature).
 */
@Composable
fun VehicleSize.label(): String = stringResource(
    when (this) {
        VehicleSize.MOTORCYCLE -> Res.string.vehicle_size_moto
        VehicleSize.MICRO_SMALL -> Res.string.vehicle_size_small
        VehicleSize.MEDIUM_SUV -> Res.string.vehicle_size_medium
        VehicleSize.LARGE_SEDAN -> Res.string.vehicle_size_large
        VehicleSize.VAN_HIGH -> Res.string.vehicle_size_van
    }
)

@Composable
fun ParkingAlertKey.label(): String = stringResource(
    when (this) {
        ParkingAlertKey.HIGH_CEILING -> Res.string.parking_alert_high_ceiling
        ParkingAlertKey.LONG_CAR -> Res.string.parking_alert_long_car
        ParkingAlertKey.WIDE_CAR -> Res.string.parking_alert_wide_car
        ParkingAlertKey.STANDARD -> Res.string.parking_alert_standard
    }
)
