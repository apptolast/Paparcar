package io.apptolast.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ParkingHistoryDto(
    val id: String = "",
    val userId: String = "",
    val vehicleId: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = 0L,
    val isActive: Boolean = false,
    val spotId: String? = null,
    val geofenceId: String? = null,
    val address: AddressDto? = null,
    val placeInfo: PlaceInfoDto? = null,
    val detectionReliability: Float? = null,
    /** [VehicleSize] enum name captured at park time. Null when unknown. */
    val sizeCategory: String? = null,
    /** [CarbodyType] enum name captured at park time. Null for non-CAR or unknown. */
    val carbodyType: String? = null,
    /** Arm-evidence label of the confirming session ("speed" / "vehicle_enter" / "manual" / …) — the
     *  ARM trigger. Half of the pin provenance. Null for legacy / non-session pins. [DET-PIN-PROVENANCE-001] */
    val armEvidence: String? = null,
    /** Confirmation PATH that placed this pin ("steps+egress" / "safety_net_backfill" / "bt" / … ) —
     *  which trigger put the parking. The other half of provenance. Null for legacy pins. [DET-PIN-PROVENANCE-001] */
    val detectionPath: String? = null,
    /** Epoch-ms of the local edit this document mirrors. Stamped on every write so the inbound-sync
     *  Last-Write-Wins merge can tell when the server has caught up with a pending local edit.
     *  Legacy docs read 0 → always lose to a real local timestamp. [SYNC-RECONCILE-USERPARKING-001] */
    val updatedAt: Long = 0L,
)
