package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parking_sessions")
data class UserParkingEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val vehicleId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val spotId: String? = null,
    val geofenceId: String? = null,
    val isActive: Boolean,
    // AddressInfo — populated asynchronously after the parking session is saved
    val addressStreet: String? = null,
    val addressCity: String? = null,
    val addressRegion: String? = null,
    val addressCountry: String? = null,
    val addressCountryCode: String? = null,
    // PlaceInfo — name + PlaceCategory enum name (e.g. "FUEL")
    val placeInfoName: String? = null,
    val placeInfoCategory: String? = null,
    // Detection reliability [0.0, 1.0]: 1.0=user confirmed, ~0.90=vehicle-exit, ~0.75=slow-path
    val detectionReliability: Float? = null,
    // VehicleSize enum name (e.g. "MEDIUM_SUV") — passed to the published Spot so nearby drivers see fit
    val sizeCategory: String? = null,
    // CarbodyType enum name (e.g. "HATCHBACK_MEDIUM") — passed to the published Spot for the body badge
    val carbodyType: String? = null,
    // SpotType enum name (e.g. "MANUAL_REPORT") — how this parking was originated (auto detection /
    // manual report / home geofence). Drives the detection-method label in the history detail screen.
    // Null on legacy rows written before v15 → read as AUTO_DETECTED. Synced to Firestore. [HISTORY-DETAIL-001]
    val spotType: String? = null,
    // Non-null when parked inside a private zone — DepartureDetectionWorker skips Spot publication
    val privateZoneId: String? = null,
    // Max GPS speed (m/s) observed during the detection session that confirmed this park.
    // LOCAL-ONLY (not synced to Firestore) — feeds the repark-plausibility guard. [DET-SOLID-001]
    val tripMaxSpeedMps: Float? = null,
    // Arm evidence label of the confirming session (e.g. "speed", "vehicle_enter", "manual").
    // Now synced to Firestore for remote provenance diagnostics. [DET-SOLID-001][DET-PIN-PROVENANCE-001]
    val armEvidence: String? = null,
    // Confirmation PATH that placed this pin ("steps+egress" / "safety_net_backfill" / "bt" / …) —
    // which trigger put the parking. Synced to Firestore. Null for legacy rows. [DET-PIN-PROVENANCE-001]
    val detectionPath: String? = null,
    // Non-null when this session is an APPROXIMATE ZONE (honest close) rather than an exact point:
    // the radius (meters) of the area. LOCAL-ONLY (not synced to Firestore) — an unrefined "please
    // confirm" mark that stays on the device that detected it until the user refines it to an exact
    // point. [DET-HONEST-CLOSE-001]
    val zoneRadiusMeters: Float? = null,
    // Driven route to this parking as a Google-encoded polyline (one compact TEXT column, ~hundreds
    // of bytes/trip). Synced to Firestore. Null on legacy rows / BT parks. [DET-ROUTE-TRACK-001]
    val routePolyline: String? = null,
    // Whether routePolyline is the FINAL on-road (map-matched) line vs raw fixes awaiting the one-time
    // snap. false + non-null polyline = "recalculating". Synced. [DET-ROUTE-SNAP-STORE-001]
    val routeSnapped: Boolean = false,
    // Epoch-ms of the last LOCAL mutation of this row (save / clear-active / move / enrich). Drives
    // the inbound-sync Last-Write-Wins merge so a stale remote snapshot can't resurrect an ended
    // session or clobber an offline edit. Local is authoritative. [SYNC-RECONCILE-USERPARKING-001]
    val updatedAt: Long = 0,
    // True while a local edit has not been confirmed onto Firestore. The reconcile never lets a
    // remote row overwrite a pending local row that is strictly newer; cleared once the remote
    // write acks (worker/drainer). [SYNC-RECONCILE-USERPARKING-001]
    val pendingSync: Boolean = false,
)
