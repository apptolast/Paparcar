package com.rndeveloper.paparcar.presentation.util

import androidx.compose.runtime.Composable
import com.rndeveloper.paparcar.domain.fuel.FuelPrice
import com.rndeveloper.paparcar.domain.fuel.FuelPriceOrigin
import com.rndeveloper.paparcar.domain.model.EnergyLabel
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.vehicle.FuelDefaults
import com.rndeveloper.paparcar.localeGallonKind
import kotlin.math.round
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.unit_kwh_per_100
import paparcar.composeapp.generated.resources.unit_kwh_per_100_mi
import paparcar.composeapp.generated.resources.unit_l_per_100
import paparcar.composeapp.generated.resources.unit_mpg
import paparcar.composeapp.generated.resources.vehicle_registration_consumption_kwh_label
import paparcar.composeapp.generated.resources.vehicle_registration_consumption_kwh_mi_label
import paparcar.composeapp.generated.resources.vehicle_registration_consumption_liters_label
import paparcar.composeapp.generated.resources.vehicle_registration_consumption_mpg_label
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.stats_cost_from_average
import paparcar.composeapp.generated.resources.stats_cost_from_average_dated
import paparcar.composeapp.generated.resources.stats_cost_from_your_price
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

/**
 * What a spend figure has to say about the price behind it. [FUEL-PRICE-RESOLVER-001]
 *
 * ⛔ A spend figure has to say what price it used: an average and the user's own price are not the
 * same claim, and only one of them is theirs. Same family as the partial-sum rule — a figure that
 * cannot describe itself gets described by whoever reads it.
 *
 * ⛔ **La media DICE cuándo se midió** [FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001]. El
 * precio del usuario no lleva fecha porque no la tiene, y ahí la frase se queda como estaba:
 * inventarle una sería peor que no decir nada.
 *
 * Here rather than inside the figures card because it stopped having one reader: the closed-month
 * recap prints a spend too, and a second card is not allowed to invent a second way of qualifying
 * the same number. [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
 */
@Composable
fun fuelPriceNoteText(price: FuelPrice): String {
    val measuredAgo = price.measuredOn?.let { relativeTimeText(isoDateToEpochMs(it)) }
    return when {
        price.source == FuelPriceOrigin.UserOverride ->
            stringResource(Res.string.stats_cost_from_your_price)
        measuredAgo != null ->
            stringResource(Res.string.stats_cost_from_average_dated, measuredAgo)
        // Sin fecha no se finge una: se dice lo de siempre.
        else -> stringResource(Res.string.stats_cost_from_average)
    }
}

/** Renders a consumption figure without a trailing ".0" for whole numbers. */
fun formatPer100(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

/**
 * El nombre de la unidad de consumo que este usuario lee: `l/100 km` · `mpg` · `kWh/100 km` ·
 * `kWh/100 mi`. [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
 */
@Composable
fun consumptionUnitLabel(usesLiters: Boolean): String {
    val imperial = LocalDistanceUnit.current == DistanceUnit.IMPERIAL
    return stringResource(
        when {
            usesLiters && imperial -> Res.string.unit_mpg
            usesLiters -> Res.string.unit_l_per_100
            imperial -> Res.string.unit_kwh_per_100_mi
            else -> Res.string.unit_kwh_per_100
        },
    )
}

/**
 * La etiqueta del CAMPO donde se teclea el consumo — «Consumo (l/100 km)» · «Consumption (mpg)».
 *
 * Vive aquí, pegada a [consumptionUnitLabel], porque las dos responden a la misma pregunta y el
 * formulario tiene que pedir el número en la unidad en la que luego lo enseña.
 * [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
 */
@Composable
fun consumptionFieldLabel(usesLiters: Boolean): String {
    val imperial = LocalDistanceUnit.current == DistanceUnit.IMPERIAL
    return stringResource(
        when {
            usesLiters && imperial -> Res.string.vehicle_registration_consumption_mpg_label
            usesLiters -> Res.string.vehicle_registration_consumption_liters_label
            imperial -> Res.string.vehicle_registration_consumption_kwh_mi_label
            else -> Res.string.vehicle_registration_consumption_kwh_label
        },
    )
}

/**
 * Una cifra de consumo del dominio, ya convertida y redondeada como se enseña.
 *
 * El redondeo va aquí y no en [consumptionToDisplay] porque es una decisión de PRESENTACIÓN: mpg se
 * lee con un decimal («42.8»), mientras que la conversión tiene que quedar exacta para que el
 * formulario pueda devolverla al dominio sin derivar.
 */
@Composable
fun consumptionFigure(per100Km: Float, usesLiters: Boolean): String = formatPer100(
    roundToTenth(
        consumptionToDisplay(
            per100Km = per100Km,
            usesLiters = usesLiters,
            unit = LocalDistanceUnit.current,
            gallon = localeGallonKind(),
        ),
    ),
)

/** Un decimal — lo que se lee de un consumo, en cualquiera de las cuatro unidades. */
internal fun roundToTenth(value: Float): Float = round(value * TENTHS) / TENTHS

private const val TENTHS = 10f

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
    val parts = buildList {
        add(fuelTypeLabel(fuel))
        add("${consumptionFigure(primary, fuel.usesLiters)} ${consumptionUnitLabel(fuel.usesLiters)}")
        if (fuel.hasSecondaryElectric) {
            val electric = vehicle.consumptionElectricPer100
                ?: FuelDefaults.defaultElectricConsumptionPer100(fuel)
            // ⛔ La segunda cifra de un PHEV es SIEMPRE eléctrica, aunque la primera queme: cada una
            // se convierte con su propia magnitud. [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
            electric?.let {
                add("+ ${consumptionFigure(it, usesLiters = false)} ${consumptionUnitLabel(usesLiters = false)}")
            }
        }
        vehicle.energyLabel?.let { add(energyLabelText(it)) }
    }
    return parts.joinToString(" · ")
}
