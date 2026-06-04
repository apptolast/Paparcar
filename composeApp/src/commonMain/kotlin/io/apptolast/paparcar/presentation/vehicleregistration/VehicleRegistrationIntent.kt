package io.apptolast.paparcar.presentation.vehicleregistration

import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType

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
    data class SetSize(val size: VehicleSize) : VehicleRegistrationIntent()
    /** Pick a high-level vehicle category (CAR / MOTORCYCLE / SCOOTER / BIKE). [BUG-SCOOTER-001] */
    data class SetVehicleType(val type: VehicleType) : VehicleRegistrationIntent()
    data class SetShowOnSpot(val enabled: Boolean) : VehicleRegistrationIntent()
    data class LoadVehicle(val vehicleId: String) : VehicleRegistrationIntent()
    data object Save : VehicleRegistrationIntent()
    data object DeleteVehicle : VehicleRegistrationIntent()
    data object NavigateBack : VehicleRegistrationIntent()
}