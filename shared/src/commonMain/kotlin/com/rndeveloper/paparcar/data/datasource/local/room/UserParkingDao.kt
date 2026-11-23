package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UserParkingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: UserParkingEntity)

    /** REPLACE-conflict bulk insert used by [UserParkingRepository.syncFromRemote]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<UserParkingEntity>)

    /** All sessions for this user — the inbound reconcile reads these to protect pending local
     *  edits from a stale remote snapshot. [SYNC-RECONCILE-USERPARKING-001] */
    @Query("SELECT * FROM parking_sessions WHERE userId = :userId")
    suspend fun getByUser(userId: String): List<UserParkingEntity>

    /** Rows with a local edit not yet confirmed onto Firestore — drained by
     *  `pushPendingParkingSessions`. [SYNC-RECONCILE-USERPARKING-001] */
    @Query("SELECT * FROM parking_sessions WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<UserParkingEntity>

    /** Clears the pending flag once the remote write for [id] has been acked. */
    @Query("UPDATE parking_sessions SET pendingSync = 0 WHERE id = :id")
    suspend fun clearPending(id: String)

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 AND geofenceId = :geofenceId LIMIT 1")
    suspend fun getActiveByGeofence(geofenceId: String): UserParkingEntity?

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 AND vehicleId = :vehicleId LIMIT 1")
    suspend fun getActiveByVehicle(vehicleId: String): UserParkingEntity?

    @Query("SELECT * FROM parking_sessions WHERE retractedAtMs IS NULL ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParkingEntity>

    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId AND isActive = 0 AND retractedAtMs IS NULL ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getEndedSessionsByVehiclePaged(vehicleId: String, limit: Int, offset: Int): List<UserParkingEntity>

    // [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] The four history-facing reads (this, the two
    // paged ones and observeByVehicle) carry `retractedAtMs IS NULL`: a parking the app itself
    // disproved is not a row the user should read. The DIAGNOSTIC reads deliberately do NOT —
    // getById, getByUser, getPendingSync and the active/geofence lookups still see everything,
    // because a withdrawn pin is exactly what a field report is trying to explain.
    @Query("SELECT * FROM parking_sessions WHERE retractedAtMs IS NULL ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<UserParkingEntity>>

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 ORDER BY timestamp DESC")
    fun observeActive(): Flow<List<UserParkingEntity>>

    /**
     * [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] The most recent closed stop still waiting for
     * «¿Repostaste aquí?».
     *
     * ⛔ ONE row, not a list, and that is the product decision rather than a query optimisation:
     * Home asks about one thing at a time, and a queue of pump questions would be the interruption
     * this app's doctrine spends most of its guards avoiding. The rest stay in the vehicle's
     * history, where the user goes looking rather than being found.
     *
     * Ordered by `endedAtMs` and not by `timestamp`: the question is about a DEPARTURE.
     */
    @Query("""
        SELECT * FROM parking_sessions
        WHERE refuelStop = 'PENDING' AND isActive = 0 AND retractedAtMs IS NULL
        ORDER BY endedAtMs DESC
        LIMIT 1
    """)
    fun observeLatestPendingRefuelStop(): Flow<UserParkingEntity?>

    @Query("SELECT * FROM parking_sessions WHERE isActive = 1 ORDER BY timestamp DESC")
    suspend fun getAllActive(): List<UserParkingEntity>

    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId AND retractedAtMs IS NULL ORDER BY timestamp DESC")
    fun observeByVehicle(vehicleId: String): Flow<List<UserParkingEntity>>

    // Every deactivation stamps updatedAt=:now + pendingSync=1 so the change is a NEWER local edit
    // that (a) wins the inbound reconcile over a stale remote isActive=true — the fix for the
    // resurrected/duplicate-active session — and (b) gets drained to Firestore.
    // [SYNC-RECONCILE-USERPARKING-001]
    // Close provenance: endedAtMs via COALESCE (the FIRST close is the real departure moment — an
    // idempotent re-clear must not move it) and publishedSpot via MAX (a later promotion may
    // confirm a publication, never retract one). [VEH-STATS-SAY-SOMETHING-USEFUL-001]
    // [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] `refuelStop` via COALESCE, like the instant above
    // and for a sharper reason: the user's answer is written into this same column, so a re-clear
    // that overwrote it would re-ask a question already answered — and the promotion of a deduced
    // departure IS such a re-clear, minutes or hours later.
    @Query("""
        UPDATE parking_sessions SET
            isActive = 0,
            endedAtMs = COALESCE(endedAtMs, :endedAtMs),
            publishedSpot = MAX(publishedSpot, :publishedSpot),
            refuelStop = COALESCE(refuelStop, :refuelStop),
            updatedAt = :now,
            pendingSync = 1
        WHERE id = :sessionId
    """)
    suspend fun clearActiveById(
        sessionId: String,
        endedAtMs: Long,
        publishedSpot: Boolean,
        refuelStop: String?,
        now: Long,
    )

    /**
     * [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] The user's answer to «¿Repostaste aquí?».
     *
     * No COALESCE: this is the one write that is ALLOWED to move the column, and it is the whole
     * reason the column exists. Guarded on `refuelStop IS NOT NULL` instead — an answer may only
     * reach a stop the app actually asked about, so a stale screen cannot invent a refuel on an
     * ordinary park.
     */
    @Query("""
        UPDATE parking_sessions SET
            refuelStop = :answer,
            updatedAt = :now,
            pendingSync = 1
        WHERE id = :sessionId AND refuelStop IS NOT NULL
    """)
    suspend fun answerRefuelStopById(sessionId: String, answer: String, now: Long)

    /**
     * [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] Withdraws a parking the app concluded never
     * happened. Stamps updatedAt + pendingSync like every other close, so the withdrawal reaches
     * Firestore through the ordinary reconcile.
     *
     * COALESCE on the instant for the same reason the close uses it: the FIRST withdrawal is the
     * real one, and an idempotent repeat must not move it.
     */
    @Query("""
        UPDATE parking_sessions SET
            retractedAtMs = COALESCE(retractedAtMs, :retractedAtMs),
            updatedAt = :now,
            pendingSync = 1
        WHERE id = :sessionId
    """)
    suspend fun retractById(sessionId: String, retractedAtMs: Long, now: Long)

    /**
     * [DET-A-TRAIN-IS-NOT-A-CAR-001] Re-opens a session whose DEPARTURE the trip went on to refute:
     * the car never left, so the park it had is the truth again.
     *
     * The exact inverse of [clearActiveById], and each column is undone on purpose:
     *
     *  - `endedAtMs` back to NULL rather than left where it was. The close comment above calls the
     *    first stamp "the real departure moment" and guards it with COALESCE — which is right for a
     *    departure that happened. This one did not, so leaving the stamp would make the REAL
     *    departure, whenever it comes, unable to date itself.
     *  - `publishedSpot` back to 0, deliberately breaking that column's MAX monotonicity. The rule
     *    it breaks says "a later promotion may confirm a publication, never retract one", and it is
     *    written for promotions. This is a withdrawal: the caller takes the spot document back, so
     *    a session that still claimed to have freed a plaza would be counting a plaza that no
     *    longer exists in anyone's map. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
     *  - `provisionalDepartureAtMs` cleared: no deduction is pending on a car that never went.
     *  - `refuelStop` back to NULL, same logic as the two above: the question «¿repostaste aquí?»
     *    was asked ABOUT a departure, and this one did not happen. Leaving it would keep a prompt
     *    alive on a car that is still parked. [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001]
     *
     * `updatedAt`/`pendingSync` like every other write here, so the re-opening wins the inbound
     * reconcile against the stale remote `isActive = false` it is undoing.
     * [SYNC-RECONCILE-USERPARKING-001]
     */
    @Query("""
        UPDATE parking_sessions SET
            isActive = 1,
            endedAtMs = NULL,
            publishedSpot = 0,
            provisionalDepartureAtMs = NULL,
            refuelStop = NULL,
            updatedAt = :now,
            pendingSync = 1
        WHERE id = :sessionId AND retractedAtMs IS NULL
    """)
    suspend fun reopenById(sessionId: String, now: Long)

    /** [DET-HANDOFF-NOT-MANUAL-001 §B] Marks (or clears, with null) a pending DEDUCED departure on a
     *  session that stays ACTIVE: the spot went out provisionally and the car was not given up.
     *  Local-only — `pendingSync` is deliberately untouched so this never travels to Firestore. */
    @Query("UPDATE parking_sessions SET provisionalDepartureAtMs = :atMs, updatedAt = :now WHERE id = :sessionId")
    suspend fun setProvisionalDeparture(sessionId: String, atMs: Long?, now: Long)

    // A supersede close (new park replaces the vehicle's active session) still ENDS the old one —
    // stamp endedAtMs like any other close; publishedSpot stays untouched (nothing was published
    // by this path). [VEH-STATS-SAY-SOMETHING-USEFUL-001]
    // [STATS-EVERY-NUMBER-CITES-ITS-EVIDENCE-001] `endedAtMs` is its own parameter: the old pin
    // ended no later than the NEW session began (its measured stop), not when this code ran — a
    // backfilled successor must not inflate its predecessor's parked duration by its own delay.
    @Query("UPDATE parking_sessions SET isActive = 0, endedAtMs = COALESCE(endedAtMs, :endedAtMs), updatedAt = :now, pendingSync = 1 WHERE isActive = 1 AND vehicleId = :vehicleId")
    suspend fun clearActiveByVehicle(vehicleId: String, endedAtMs: Long, now: Long)

    /** Deactivates legacy/unidentified active sessions (no vehicleId). Without this, a new
     *  vehicle-less session would ACCUMULATE next to previous vehicle-less actives. [DET-SOLID-001] */
    @Query("UPDATE parking_sessions SET isActive = 0, endedAtMs = COALESCE(endedAtMs, :endedAtMs), updatedAt = :now, pendingSync = 1 WHERE isActive = 1 AND vehicleId IS NULL")
    suspend fun clearActiveOrphans(endedAtMs: Long, now: Long)

    /**
     * Atomic replace of the vehicle's active session: deactivate-then-insert in ONE transaction,
     * so process death can never leave the vehicle with zero (or two) active sessions — the
     * invariant the non-transactional clear+insert pair could not guarantee. Returns the id of
     * the session that was active before the swap (the caller removes its orphan geofence).
     *
     * [session] must already carry `updatedAt=:now` + `pendingSync=true` (stamped by the caller);
     * the previous-session clear is stamped with the same [now] so BOTH edits win the reconcile.
     * [DET-SOLID-001][MULTI-PARKING-001][SYNC-RECONCILE-USERPARKING-001]
     */
    @Transaction
    suspend fun replaceActiveSession(session: UserParkingEntity, now: Long): String? {
        val previous = session.vehicleId?.let { getActiveByVehicle(it) }
        // The predecessor ended when the successor's measured stop began — session.timestamp is
        // that stop (plausibility-bounded at confirm), never later than [now].
        if (session.vehicleId != null) {
            clearActiveByVehicle(session.vehicleId, endedAtMs = session.timestamp, now = now)
        } else {
            clearActiveOrphans(endedAtMs = session.timestamp, now = now)
        }
        insert(session)
        return previous?.id
    }

    /** Hygiene probe for the janitor's self-repair sweep: vehicles holding more than one
     *  active session (invariant violation — should always return empty). [DET-SOLID-001] */
    @Query("""
        SELECT * FROM parking_sessions WHERE isActive = 1 AND vehicleId IN (
            SELECT vehicleId FROM parking_sessions
            WHERE isActive = 1 AND vehicleId IS NOT NULL
            GROUP BY vehicleId HAVING COUNT(*) > 1
        ) ORDER BY vehicleId, timestamp DESC
    """)
    suspend fun getActiveDuplicates(): List<UserParkingEntity>

    @Query("DELETE FROM parking_sessions WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)

    /**
     * [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001] Every row of a vehicle, retracted ones
     * included. Deleting the car takes its parkings with it: a row whose vehicleId no page will
     * ever ask for again is not history, it is a leak — it keeps syncing and occupying space while
     * being unreachable from every screen.
     */
    @Query("DELETE FROM parking_sessions WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicle(vehicleId: String)

    /**
     * How many parkings the USER would see disappear with [vehicleId] — the number the delete
     * warning quotes. Mirrors the history reads exactly (`isActive = 0`, `retractedAtMs IS NULL`):
     * counting rows the history never showed would inflate the warning, and the active one cannot
     * be here because a vehicle holding one can't be deleted at all.
     */
    @Query("SELECT COUNT(*) FROM parking_sessions WHERE vehicleId = :vehicleId AND isActive = 0 AND retractedAtMs IS NULL")
    suspend fun countEndedByVehicle(vehicleId: String): Int

    /** Unconditional wipe of every row. Used by [LocalSessionCache.wipe] on sign-out. */
    @Query("DELETE FROM parking_sessions")
    suspend fun deleteAll()

    @Query("""
        UPDATE parking_sessions SET
            addressStreet      = :street,
            addressCity        = :city,
            addressRegion      = :region,
            addressCountry     = :country,
            addressCountryCode = :countryCode,
            addressProvince    = :province,
            addressPostalCode  = :postalCode,
            placeInfoName      = :placeInfoName,
            placeInfoCategory  = :placeInfoCategory,
            updatedAt          = :now,
            pendingSync        = 1
        WHERE id = :id
    """)
    suspend fun updateAddressAndPlace(
        id: String,
        street: String?,
        city: String?,
        region: String?,
        country: String?,
        countryCode: String?,
        province: String?,
        postalCode: String?,
        placeInfoName: String?,
        placeInfoCategory: String?,
        now: Long,
    )

    /**
     * Manual-edit path — overwrites lat/lon/accuracy and **clears
     * address + POI fields** so the re-scheduled enrichment worker fills them
     * with the new location's geocode. Used by `UpdateParkingLocationUseCase`
     * when the user drags the parked-car pin to a new spot via the
     * `HomeMode.AddingParking` edit flow.
     *
     * ⛔ **`timestamp` NO se toca.** Es el reloj de «aparcado desde» —y, en detección, la línea de
     * nacimiento de sesión de [DET-SESSION-BIRTH-001)— y el coche lleva ahí desde que se aparcó, no
     * desde que el usuario arregló el pin. Se escribía, y por eso corregir un aparcamiento de ocho
     * minutos lo dejaba en «Aparcado hace <1m» (campo 16-09), falseaba de paso la duración en
     * historial y estadísticas, y contradecía la promesa impresa en esa misma hoja: «Corregir
     * mantiene el tiempo aparcado; marcar uno nuevo lo pone a cero». El cero es verdad en el OTRO
     * botón, que crea una sesión nueva.
     * [PARK-A-CORRECTION-MOVES-THE-PLACE-NOT-THE-CLOCK-001]
     *
     * [PARK-A-PIN-MUST-SAY-WHO-PLACED-IT-001] It also rewrites the PROVENANCE, because after a drag
     * the old one is a lie: the pin now sits where the user put it, not where the detector guessed.
     * The three fields move together or not at all —
     * - `detectionPath` → who placed it (`user_moved`),
     * - `detectionReliability` → 1.0, the same ground truth a hand-placed pin gets,
     * - `zoneRadiusMeters` → NULL, because the doubt was about where the CAR was and the user has
     *   just answered that; leaving the radius would draw a target badge around a point the user
     *   pointed at. [DET-DOUBT-REACHES-REMOTE-001]
     *
     * Field 2026-08-30 19:32:44 (Redmi): a dragged pin still read `unattended_zone_gap_anchor` with
     * reliability 0.5 — the pin claimed the unattended net had placed it.
     */
    @Query("""
        UPDATE parking_sessions SET
            latitude             = :lat,
            longitude            = :lon,
            accuracy             = :accuracy,
            addressStreet        = NULL,
            addressCity          = NULL,
            addressRegion        = NULL,
            addressCountry       = NULL,
            addressCountryCode   = NULL,
            addressProvince      = NULL,
            addressPostalCode    = NULL,
            placeInfoName        = NULL,
            placeInfoCategory    = NULL,
            detectionPath        = :detectionPath,
            detectionReliability = :detectionReliability,
            zoneRadiusMeters     = NULL,
            updatedAt            = :now,
            pendingSync          = 1
        WHERE id = :id
    """)
    suspend fun updateLocation(
        id: String,
        lat: Double,
        lon: Double,
        accuracy: Float,
        detectionPath: String,
        detectionReliability: Float,
        now: Long,
    )

    @Query("SELECT * FROM parking_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UserParkingEntity?

    /** The vehicle's parking right before [beforeTimestamp] — the origin of the trip that ended at
     *  the session in question (feeds the pin-to-pin inferred route of a backfill pin).
     *  [ROUTE-GAP-HONEST-001] */
    @Query(
        "SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId AND timestamp < :beforeTimestamp " +
            "AND retractedAtMs IS NULL ORDER BY timestamp DESC LIMIT 1"
    )
    suspend fun getPreviousByVehicle(vehicleId: String, beforeTimestamp: Long): UserParkingEntity?

    /**
     * Writes the driven route + its snapped flag onto a session (the post-park worker's one-time
     * snap, or the initial raw store). Stamps updatedAt + pendingSync so the change reaches Firestore
     * via the reconcile. [DET-ROUTE-SNAP-STORE-001]
     */
    @Query("""
        UPDATE parking_sessions SET
            routePolyline           = :routePolyline,
            routeSnapped            = :routeSnapped,
            routeInferredSpans      = :routeInferredSpans,
            routeInferredResolution = NULL,
            routeDistanceMeters     = :routeDistanceMeters,
            updatedAt               = :now,
            pendingSync             = 1
        WHERE id = :id
    """)
    suspend fun updateRoute(
        id: String,
        routePolyline: String?,
        routeSnapped: Boolean,
        routeInferredSpans: String?,
        routeDistanceMeters: Float?,
        now: Long,
    )

    /**
     * Stores the user's verdict on a route's road-inferred stretches ("did you drive this way?").
     * Stamps updatedAt + pendingSync so the answer reaches Firestore. [ROUTE-GAP-HONEST-001]
     */
    @Query("""
        UPDATE parking_sessions SET
            routeInferredResolution = :resolution,
            updatedAt               = :now,
            pendingSync             = 1
        WHERE id = :id
    """)
    suspend fun updateRouteResolution(id: String, resolution: String?, now: Long)
}
