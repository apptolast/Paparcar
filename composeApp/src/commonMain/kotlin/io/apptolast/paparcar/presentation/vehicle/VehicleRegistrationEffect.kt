package io.apptolast.paparcar.presentation.vehicle

import io.apptolast.paparcar.domain.error.PaparcarError

sealed class VehicleRegistrationEffect {
    data object NavigateBack : VehicleRegistrationEffect()
    /**
     * Save completed.
     *
     * @param vehicleId The id of the vehicle that was just persisted. Carried so the
     *   screen can offer follow-up navigation (e.g. "Pair Bluetooth?" modal in
     *   [VEH-BT-001]) without re-reading from the repository.
     * @param isNewVehicle `true` if this was a fresh registration, `false` if it
     *   was an edit of an existing vehicle. The Bluetooth recommendation modal
     *   only shows for new vehicles — edits don't need it.
     */
    data class SavedSuccessfully(
        val vehicleId: String,
        val isNewVehicle: Boolean,
    ) : VehicleRegistrationEffect()
    data class ShowError(val error: PaparcarError) : VehicleRegistrationEffect()
}