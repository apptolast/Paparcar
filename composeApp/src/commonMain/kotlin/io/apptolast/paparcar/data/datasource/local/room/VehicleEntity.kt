package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val brand: String? = null,
    val model: String? = null,
    /** VehicleSize enum name (e.g. "MEDIUM"). */
    val sizeCategory: String,
    /** BT device address — on-device only, never synced to Firestore. */
    val bluetoothDeviceId: String? = null,
    val showBrandModelOnSpot: Boolean = false,
    val isDefault: Boolean = false,
)
