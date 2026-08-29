package com.rndeveloper.paparcar.domain.model

/**
 * The ONE answer to "which of my parked cars stands in for me right now" under multi-parking
 * [MULTI-PARKING-001] — the subject of Home's initial camera focus, the Browse peek, the midpoint
 * FAB, the release fallback and the detection banner's `Parked` payload.
 *
 * **The most recently parked session wins.** Parking is the freshest evidence of which car the user
 * actually drives these days, so the app opens on it; a monitoring rank (Bluetooth > Active) must
 * not pin every surface on a car parked weeks ago just because it is better watched. It
 * self-corrects: the next park of the other car makes that one the subject again.
 *
 * Watch rank ([sortRank]) only breaks timestamp ties; a full tie keeps session order. Sessions
 * arrive from Room already ordered `timestamp DESC`, so this agrees with list order in practice —
 * it is re-derived here so fakes / Firestore ordering can never change the answer.
 *
 * Deliberately NOT preferring a session that owns a geofence: hiding an unwatched car behind a
 * watched one made the honest watch badge describe a car the user was not looking at.
 * [UI-PREFERRED-SESSION-RECENCY-001] [DET-READY-TRIP-OVER-PARKED-001]
 */
fun preferredParkingSession(
    activeSessions: List<UserParking>,
    vehicles: List<Vehicle>,
): UserParking? = activeSessions.maxWithOrNull(parkedSessionPreference(vehicles))

/**
 * The preference behind [preferredParkingSession], exposed so every surface that ORDERS parked
 * sessions (e.g. Home's vehicle chips row) ranks them with the same rule instead of re-deriving
 * it. Greater = more preferred: most recent park first, watch rank only breaks timestamp ties.
 * [UI-BROWSE-DRIVING-OVER-PARKED-001]
 */
fun parkedSessionPreference(vehicles: List<Vehicle>): Comparator<UserParking> =
    compareBy<UserParking> { it.location.timestamp }
        .thenByDescending { session ->
            vehicles.firstOrNull { it.id == session.vehicleId }
                ?.monitoringStatus()?.sortRank()
                ?: Int.MAX_VALUE
        }
