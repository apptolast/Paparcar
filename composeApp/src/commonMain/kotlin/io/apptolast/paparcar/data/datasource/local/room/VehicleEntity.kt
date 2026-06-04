package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String? = null,
    val brand: String? = null,
    val model: String? = null,
    /** VehicleSize enum name (e.g. "MEDIUM"). */
    val sizeCategory: String,
    /** VehicleType enum name (e.g. "CAR"). Defaulted to "CAR" by the v3→v4 migration
     *  for pre-existing rows. [BUG-SCOOTER-001] */
    val vehicleType: String = "CAR",
    /** BT device address — on-device only, never synced to Firestore. */
    val bluetoothDeviceId: String? = null,
    val showBrandModelOnSpot: Boolean = false,
    val isActive: Boolean = false,
)
