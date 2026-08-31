package com.rndeveloper.paparcar.data.datasource.remote.dto

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
    /** [SpotType] enum name — the parking origin (AUTO_DETECTED / MANUAL_REPORT / HOME_GEOFENCE).
     *  Null on legacy docs → read as AUTO_DETECTED. Drives the history detail detection label. [HISTORY-DETAIL-001] */
    val spotType: String? = null,
    /** Arm-evidence label of the confirming session ("speed" / "vehicle_enter" / "manual" / …) — the
     *  ARM trigger. Half of the pin provenance. Null for legacy / non-session pins. [DET-PIN-PROVENANCE-001] */
    val armEvidence: String? = null,
    /** Confirmation PATH that placed this pin ("steps+egress" / "safety_net_backfill" / "bt" / … ) —
     *  which trigger put the parking. The other half of provenance. Null for legacy pins. [DET-PIN-PROVENANCE-001] */
    val detectionPath: String? = null,
    /**
     * [DET-DOUBT-REACHES-REMOTE-001] Radius of the AREA this pin really is, or null when it is a
     * point. Derived `isApproximate` comes from it (`zoneRadiusMeters != null`).
     *
     * It used to be local-only. The reason was written down — an unrefined zone stays on the device
     * that drew it [DET-HONEST-CLOSE-001] — and the cost was not: in remote a 250 m circle was
     * indistinguishable from an exact pin, so every diagnosis of an approximate park had to read
     * Room over a cable (field 2026-08-30, the 142 m pin). A doubt the app measured and then hides
     * from its own diagnostics is a doubt it cannot learn from.
     */
    val zoneRadiusMeters: Float? = null,
    /** The driven route that led to this parking as a Google-encoded polyline (lat/lon). One compact
     *  string synced so the trip renders in history on any device. Null on legacy / BT docs.
     *  [DET-ROUTE-TRACK-001] */
    val routePolyline: String? = null,
    /** Whether [routePolyline] is the final on-road line (true) or raw fixes still being snapped
     *  (false). Synced so a new device knows to draw vs show "recalculating". [DET-ROUTE-SNAP-STORE-001] */
    val routeSnapped: Boolean = false,
    /** Encoded provenance of the polyline's road-INFERRED stretches ("a:b" bridge / "a!" cut) when
     *  the matcher reconstructed data holes. Null = fully measured. [ROUTE-GAP-HONEST-001] */
    val routeInferredSpans: String? = null,
    /** RouteInferenceResolution enum name ("CONFIRMED"/"REJECTED") — the user's verdict on the
     *  inferred stretches; null while the question is pending. [ROUTE-GAP-HONEST-001] */
    val routeInferredResolution: String? = null,
    /** Haversine length (meters) of [routePolyline]. Null on legacy docs — the inbound mapper
     *  recomputes it from the polyline (self-healing). [VEH-STATS-SAY-SOMETHING-USEFUL-001] */
    val routeDistanceMeters: Float? = null,
    /** Epoch-ms when the session ended (isActive → false). Null on legacy docs / active sessions.
     *  [VEH-STATS-SAY-SOMETHING-USEFUL-001] */
    val endedAtMs: Long? = null,
    /** Whether closing this session published a community spot. Legacy docs read false.
     *  [VEH-STATS-SAY-SOMETHING-USEFUL-001] */
    val publishedSpot: Boolean = false,
    /** [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] Epoch-ms of the withdrawal, or null. */
    val retractedAtMs: Long? = null,
    /** Epoch-ms of the local edit this document mirrors. Stamped on every write so the inbound-sync
     *  Last-Write-Wins merge can tell when the server has caught up with a pending local edit.
     *  Legacy docs read 0 → always lose to a real local timestamp. [SYNC-RECONCILE-USERPARKING-001] */
    val updatedAt: Long = 0L,
)
