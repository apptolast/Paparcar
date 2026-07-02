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
    /** [VehicleSize] enum name (e.g. "MEDIUM_SUV"). */
    val sizeCategory: String,
    /** [CarbodyType] enum name (e.g. "HATCHBACK_MEDIUM"). Null for non-CAR vehicles. */
    val carbodyType: String? = null,
    /** [VehicleType] enum name (e.g. "CAR"). Defaulted to "CAR" by the v3→v4 migration
     *  for pre-existing rows. [BUG-SCOOTER-001] */
    val vehicleType: String = "CAR",
    /** BT device address — on-device only, never synced to Firestore. */
    val bluetoothDeviceId: String? = null,
    val showBrandModelOnSpot: Boolean = false,
    val isActive: Boolean = false,
    /** On-device only — never synced to Firestore. Used for map marker display. */
    val licensePlate: String? = null,
    /** [VehicleColor] enum name (e.g. "RED"). Null = undefined → default green icon. */
    val color: String? = null,
    /**
     * Wall-clock (client epoch ms) of the last LOCAL mutation to this row. Drives Last-Write-Wins
     * reconciliation against the remote copy in `syncFromRemote`, so an offline edit is not
     * clobbered by a stale server snapshot. 0 for rows that only ever came from remote.
     * [SYNC-RECONCILE-001]
     */
    val updatedAt: Long = 0,
    /**
     * True while this row carries a local mutation not yet confirmed by the Firestore backend. The
     * inbound sync never overwrites a `pendingSync` row; cleared once the remote write is acked.
     * [SYNC-RECONCILE-001]
     */
    val pendingSync: Boolean = false,
)
