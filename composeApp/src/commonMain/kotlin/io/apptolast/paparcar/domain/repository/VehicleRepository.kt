package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow

interface VehicleRepository : UserScopedRepository, RemoteSyncable {
    /** Observe all vehicles for the current user, ordered by [Vehicle.isActive] desc. */
    fun observeVehicles(): Flow<List<Vehicle>>

    /** Observe the active vehicle, or null if none registered. */
    fun observeActiveVehicle(): Flow<Vehicle?>

    /**
     * One-shot fetch of the user's active vehicle. Designed for save-path callers like
     * [io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase] that already
     * know the userId and need a synchronous answer, not a long-lived subscription. Falls
     * back to `user_profile.defaultVehicleId` if the vehicles table somehow lost the
     * `isActive=1` flag but the profile still points to a valid id. [AUTH-001]
     */
    suspend fun getActiveVehicle(userId: String): Vehicle?

    /**
     * One-shot fetch of a specific vehicle by id. Used when the caller already knows which
     * vehicle owns the operation (e.g. BT detection resolves the vehicle from the device
     * address before reaching [io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase]).
     * Returns null if no vehicle with that id exists for the given user.
     */
    suspend fun getVehicleById(userId: String, vehicleId: String): Vehicle?

    /**
     * One-shot fetch of the vehicle paired with [deviceAddress] for the current user.
     * Resolves the userId internally via [com.apptolast.customlogin.domain.AuthRepository].
     * Used by the BT detection receiver to identify *which* vehicle is being driven
     * (under multi-vehicle BT, the default is no longer a reliable proxy). Returns null
     * if no vehicle has this MAC paired, or if there is no authenticated session.
     */
    suspend fun getVehicleByBluetoothDeviceId(deviceAddress: String): Vehicle?

    /**
     * One-shot pull of the vehicle list from Firestore into Room. Idempotent.
     *
     * Called during splash bootstrap so the local DB reflects remote state before
     * the app decides which screen to land on. Failures are returned, not thrown —
     * the caller decides whether to surface or ignore them.
     */
    override suspend fun syncFromRemote(userId: String): Result<Unit>

    /** Save a new vehicle or update an existing one. */
    suspend fun saveVehicle(vehicle: Vehicle)

    /** Delete a vehicle by id. If it was the default, the remaining list has no default. */
    suspend fun deleteVehicle(id: String)

    /**
     * Set the given vehicle as the active one.
     * Clears isActive on all others for this user.
     */
    suspend fun setActiveVehicle(id: String)

    /**
     * Pairs a Bluetooth device MAC address with a vehicle.
     * Pass null to remove the pairing.
     * Stored on-device only — never synced to Firestore.
     */
    suspend fun updateBluetoothDevice(vehicleId: String, deviceAddress: String?)

    /** Deletes all local and remote vehicles for [userId]. Called during account deletion. */
    override suspend fun deleteAllData(userId: String): Result<Unit>

    /** Returns true if the user has at least one vehicle cached in Room. */
    suspend fun hasVehicles(userId: String): Boolean
}
