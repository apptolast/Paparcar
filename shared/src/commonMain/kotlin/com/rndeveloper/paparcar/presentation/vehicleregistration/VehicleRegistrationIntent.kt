package com.rndeveloper.paparcar.presentation.vehicleregistration

import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.EnergyLabel
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.VehicleColor
import com.rndeveloper.paparcar.domain.model.VehicleType

sealed class VehicleRegistrationIntent {
    data class SetName(val value: String) : VehicleRegistrationIntent()
    /** Select a brand from the catalog dropdown (empty string = none selected). */
    data class SelectBrand(val brand: String) : VehicleRegistrationIntent()
    /** Select "Other" for brand — reveals free-text field, clears the catalog selection. */
    data object SelectBrandOther : VehicleRegistrationIntent()
    /** Update the free-text brand value when [SelectBrandOther] is active. */
    data class SetCustomBrand(val value: String) : VehicleRegistrationIntent()
    /** Select a model from the catalog dropdown (empty string = none selected). */
    data class SelectModel(val model: String) : VehicleRegistrationIntent()
    /** Select "Other" for model — reveals free-text field, clears the catalog selection. */
    data object SelectModelOther : VehicleRegistrationIntent()
    /** Update the free-text model value when [SelectModelOther] is active. */
    data class SetCustomModel(val value: String) : VehicleRegistrationIntent()
    /**
     * User manually overrides the inferred body type (or picks one when inference
     * returned null). Marks [VehicleRegistrationState.isCarbodyManualOverride] true
     * so the size is recomputed from the chosen body and the auto badge is hidden.
     */
    data class SetCarbody(val body: CarbodyType) : VehicleRegistrationIntent()
    /** Pick a high-level vehicle category (CAR / MOTORCYCLE / SCOOTER / BIKE). [BUG-SCOOTER-001] */
    data class SetVehicleType(val type: VehicleType) : VehicleRegistrationIntent()
    /** Pick the vehicle's paint colour, or null to reset to the default green. [VEH-COLOR-001] */
    data class SetColor(val color: VehicleColor?) : VehicleRegistrationIntent()
    /** Pick what the vehicle burns — mandatory for a fuel-burning type. [VEH-FUEL-001] */
    data class SetFuelType(val fuel: FuelType) : VehicleRegistrationIntent()
    /** Free-text consumption override (primary energy). Blank clears back to the default. */
    data class SetConsumption(val value: String) : VehicleRegistrationIntent()
    /** Free-text electric consumption override — plug-in hybrid only. */
    data class SetConsumptionElectric(val value: String) : VehicleRegistrationIntent()

    /** What the user pays per litre / per kWh. Blank means "use my country's average".
     *  [FUEL-PRICE-RESOLVER-001] */
    data class SetFuelPrice(val value: String) : VehicleRegistrationIntent()

    data class SetEnergyPrice(val value: String) : VehicleRegistrationIntent()
    /** Pick the environmental badge, or null to clear the answer. */
    data class SetEnergyLabel(val label: EnergyLabel?) : VehicleRegistrationIntent()
    data class SetLicensePlate(val value: String) : VehicleRegistrationIntent()
    data class SetShowOnSpot(val enabled: Boolean) : VehicleRegistrationIntent()
    data class LoadVehicle(val vehicleId: String) : VehicleRegistrationIntent()
    data object Save : VehicleRegistrationIntent()
    data object DeleteVehicle : VehicleRegistrationIntent()
    data object NavigateBack : VehicleRegistrationIntent()
}
