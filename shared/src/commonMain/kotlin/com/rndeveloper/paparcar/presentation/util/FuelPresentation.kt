package com.rndeveloper.paparcar.presentation.util

import androidx.compose.runtime.Composable
import com.rndeveloper.paparcar.domain.model.EnergyLabel
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.vehicle.FuelDefaults
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.unit_kwh_per_100
import paparcar.composeapp.generated.resources.unit_l_per_100
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.energy_label_b
import paparcar.composeapp.generated.resources.energy_label_c
import paparcar.composeapp.generated.resources.energy_label_eco
import paparcar.composeapp.generated.resources.energy_label_none
import paparcar.composeapp.generated.resources.energy_label_zero
import paparcar.composeapp.generated.resources.fuel_type_diesel
import paparcar.composeapp.generated.resources.fuel_type_electric
import paparcar.composeapp.generated.resources.fuel_type_gasoline
import paparcar.composeapp.generated.resources.fuel_type_hybrid
import paparcar.composeapp.generated.resources.fuel_type_lpg_cng
import paparcar.composeapp.generated.resources.fuel_type_plug_in_hybrid

/**
 * The ONE place a [FuelType] / [EnergyLabel] becomes user-facing text — shared by the registration
 * form and the vehicle page so the two surfaces cannot drift. Presentation-side on purpose:
 * domain enums never know their copy. [VEH-FUEL-001]
 */

@Composable
fun fuelTypeLabel(fuel: FuelType): String = stringResource(
    when (fuel) {
        FuelType.GASOLINE -> Res.string.fuel_type_gasoline
        FuelType.DIESEL -> Res.string.fuel_type_diesel
        FuelType.LPG_CNG -> Res.string.fuel_type_lpg_cng
        FuelType.HYBRID -> Res.string.fuel_type_hybrid
        FuelType.PLUG_IN_HYBRID -> Res.string.fuel_type_plug_in_hybrid
        FuelType.ELECTRIC -> Res.string.fuel_type_electric
    },
)

@Composable
fun energyLabelText(label: EnergyLabel): String = stringResource(
    when (label) {
        EnergyLabel.ZERO -> Res.string.energy_label_zero
        EnergyLabel.ECO -> Res.string.energy_label_eco
        EnergyLabel.C -> Res.string.energy_label_c
        EnergyLabel.B -> Res.string.energy_label_b
        EnergyLabel.NONE -> Res.string.energy_label_none
    },
)

/** Renders a consumption figure without a trailing ".0" for whole numbers. */
fun formatPer100(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

/**
 * The vehicle-data line the ficha shows under carbody · size: "Diésel · 5,5 l/100 km · ECO".
 * Consumption is the user's override when given, the NAMED default otherwise — exactly the figure
 * the stats pipeline uses, so the line never lies. A plug-in hybrid appends its electric figure.
 * Null when the vehicle has no powertrain on record. [VEH-FUEL-001]
 */
@Composable
fun vehicleFuelMetaLine(vehicle: Vehicle): String? {
    val fuel = vehicle.fuelType ?: return null
    val primary = vehicle.consumptionPer100
        ?: FuelDefaults.defaultConsumptionPer100(fuel, vehicle.vehicleType)
    val primaryUnit = stringResource(
        if (fuel.usesLiters) Res.string.unit_l_per_100 else Res.string.unit_kwh_per_100,
    )
    val parts = buildList {
        add(fuelTypeLabel(fuel))
        add("${formatPer100(primary)} $primaryUnit")
        if (fuel.hasSecondaryElectric) {
            val electric = vehicle.consumptionElectricPer100
                ?: FuelDefaults.defaultElectricConsumptionPer100(fuel)
            electric?.let { add("+ ${formatPer100(it)} ${stringResource(Res.string.unit_kwh_per_100)}") }
        }
        vehicle.energyLabel?.let { add(energyLabelText(it)) }
    }
    return parts.joinToString(" · ")
}
