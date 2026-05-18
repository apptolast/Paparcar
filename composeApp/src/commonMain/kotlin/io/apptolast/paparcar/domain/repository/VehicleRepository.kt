package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    /** Observe all vehicles for the current user, ordered by [Vehicle.isDefault] desc. */
    fun observeVehicles(): Flow<List<Vehicle>>

    /** Observe the active (default) vehicle, or null if none registered. */
    fun observeDefaultVehicle(): Flow<Vehicle?>

    /**
     * One-shot fetch of the user's default vehicle. Designed for save-path callers like
     * [io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase] that already
     * know the userId and need a synchronous answer, not a long-lived subscription. Falls
     * back to `user_profile.defaultVehicleId` if the vehicles table somehow lost the
     * `isDefault=1` flag but the profile still points to a valid id. [AUTH-001]
     */
    suspend fun getDefaultVehicle(userId: String): Vehicle?

    /**
     * One-shot pull of the vehicle list from Firestore into Room. Idempotent.
     *
     * Called during splash bootstrap so the local DB reflects remote state before
     * the app decides which screen to land on. Failures are returned, not thrown —
     * the caller decides whether to surface or ignore them.
     */
    suspend fun syncFromRemote(userId: String): Result<Unit>

    /** Save a new vehicle or update an existing one. */
    suspend fun saveVehicle(vehicle: Vehicle)

    /** Delete a vehicle by id. If it was the default, the remaining list has no default. */
    suspend fun deleteVehicle(id: String)

    /**
     * Set the given vehicle as the active (default) one.
     * Clears isDefault on all others for this user.
     */
    suspend fun setDefaultVehicle(id: String)

    /**
     * Pairs a Bluetooth device MAC address with a vehicle.
     * Pass null to remove the pairing.
     * Stored on-device only — never synced to Firestore.
     */
    suspend fun updateBluetoothDevice(vehicleId: String, deviceAddress: String?)

    /** Deletes all local and remote vehicles for [userId]. Called during account deletion. */
    suspend fun deleteAllData(userId: String): Result<Unit>

    /** Returns true if the user has at least one vehicle cached in Room. */
    suspend fun hasVehicles(userId: String): Boolean
}
