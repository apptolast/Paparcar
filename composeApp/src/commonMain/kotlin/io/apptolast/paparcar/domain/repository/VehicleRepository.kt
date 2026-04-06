package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    /** Observe all vehicles for the current user, ordered by [Vehicle.isDefault] desc. */
    fun observeVehicles(): Flow<List<Vehicle>>

    /** Observe the active (default) vehicle, or null if none registered. */
    fun observeDefaultVehicle(): Flow<Vehicle?>

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
}
