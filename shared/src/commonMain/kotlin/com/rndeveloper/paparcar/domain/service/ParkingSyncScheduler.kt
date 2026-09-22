package com.rndeveloper.paparcar.domain.service

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * Schedules background propagation of parking-session mutations to the remote backend.
 *
 * All three operations are fire-and-forget (non-suspending) and return immediately after
 * enqueuing. The Android implementation is backed by WorkManager — survives process death,
 * retries on failure, and requires NETWORK_CONNECTED. Room is the source of truth; these
 * calls only reconcile Firestore asynchronously.
 *
 * Pipeline tag: [PIPE-001] / [PIPE-002]
 *
 * ## Which method to call
 *
 * | Mutation                          | Call                                       | Firestore op     |
 * |-----------------------------------|--------------------------------------------|------------------|
 * | New session saved / pin moved     | [enqueueSaveNewParkingSession]             | set() (full)     |
 * | Session released / cleared        | [enqueueClearActiveParkingSession]         | update(isActive) |
 * | Geocoder result ready             | [enqueueUpdateParkingSessionAddressAndPlace]  | update(address)  |
 * | Vehicle deleted (with history)    | [enqueueDeleteVehicleRemote]               | delete() ×2      |
 *
 * @see com.rndeveloper.paparcar.domain.usecase.parking.ConfirmParkingUseCase
 * @see com.rndeveloper.paparcar.data.repository.UserParkingRepositoryImpl
 */
interface ParkingSyncScheduler {

    /**
     * Propagates a new session to Firestore via a full set().
     *
     * If [previousSessionId] is non-null, also flips `isActive = false` on that session
     * in Firestore (mirrors `dao.clearActiveByVehicle()`). [PIPE-001]
     */
    fun enqueueSaveNewParkingSession(session: UserParking, previousSessionId: String?)

    /**
     * Marks a session as inactive in Firestore via a targeted update() on `isActive`.
     *
     * Mirrors `dao.clearActiveById()` — only the flag is touched, no other fields overwritten. [PIPE-002]
     *
     * ⛔ **Un campo: `isActive`.** Ni `endedAtMs`, ni `publishedSpot`, ni `updatedAt`. Es el camino
     * RÁPIDO, y quien completa el documento es [enqueueParkingSessionSnapshot] — que hay que encolar
     * aparte, porque durante un tiempo tres comentarios dijeron que esto ya lo cubría alguien.
     * [SYNC-A-CLOSED-SESSION-MUST-REACH-FIRESTORE-WHOLE-001]
     */
    fun enqueueClearActiveParkingSession(sessionId: String)

    /**
     * Sube el estado **ACTUAL** de [session] a Firestore como documento completo, ordenado DESPUÉS
     * de lo que ya esté encolado para ella. [SYNC-A-CLOSED-SESSION-MUST-REACH-FIRESTORE-WHOLE-001]
     *
     * Nace de un hueco medido en campo: el espejo remoto de una sesión **cerrada** decía «cerrada»
     * sin decir cuándo, porque el único push de documento completo que existía
     * (`pushPendingParkingSessions`) lo dispara `AppViewModel` al ARRANCAR online. Un cierre en
     * background —el caso normal, la detección cierra con el móvil en el bolsillo— no llegaba entero
     * hasta que el usuario volvía a abrir la app. Si no la abría, nunca.
     *
     * Contrato, y las dos mitades importan:
     * - **Estado actual, no el del encolado**, siempre que la plataforma pueda releerlo (iOS relee
     *   Room al drenar; Android lo serializa al encolar, que aquí es equivalente porque la fila ya
     *   está cerrada cuando se llama).
     * - **Ordenado detrás**, nunca reemplazando: cancelar la cadena de la sesión se llevaría por
     *   delante el parche del flag y la actualización de dirección en vuelo.
     *   [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001]
     *
     * ⛔ **Sin implementación por defecto, a propósito.** Un no-op heredado es exactamente cómo una
     * plataforma se queda sin sincronizar sin que nadie se equivoque. [DET-KOIN-MODULE-VERIFY-001]
     */
    fun enqueueParkingSessionSnapshot(session: UserParking)

    /**
     * Pushes geocoder-resolved address and POI data to Firestore for an existing session.
     *
     * Uses a partial update() so it never overwrites coordinates set by [enqueueSaveNewParkingSession].
     * Enqueued by [UserParkingRepositoryImpl.updateParkingSessionAddressAndPlace] after the local Room write,
     * chained after [enqueueSaveNewParkingSession] to avoid NOT_FOUND on first delivery. [PIPE-002]
     */
    fun enqueueUpdateParkingSessionAddressAndPlace(sessionId: String, address: AddressInfo?, placeInfo: PlaceInfo?)

    /**
     * [GEOF-001] Schedules an immediate one-time geofence-restoration pass — re-registers the GMS
     * geofences for every active session in Room. Called right after [UserParkingRepositoryImpl.syncFromRemote]
     * repopulates Room post-login, so a reinstall (which wipes BOTH the registered geofences AND Room)
     * gets its geofence back the moment the active session is synced, instead of waiting for the
     * periodic `GeofenceJanitorWorker` to run. Idempotent. Default no-op for platforms without WorkManager.
     */
    fun enqueueGeofenceRestore() {}

    /**
     * [SYNC-A-REMOTE-DELETE-HAS-NO-OUTBOX-BEHIND-IT-001] Deletes a vehicle's remote footprint —
     * its parking sessions FIRST, then the vehicle document, in one job.
     *
     * The one write class that CANNOT ride a fire-and-forget scope: an update that never lands
     * leaves a `pendingSync` row behind and `pushPendingVehicles()` heals it, but a delete leaves
     * NO row to hang the flag on. If the process dies before the remote delete lands, nothing
     * local remembers it was owed — and the inbound reconcile then pulls the surviving remote
     * docs straight back into Room: the delete UNDOES ITSELF. So the invariant: a remote write
     * that cannot be reconstructed from local state travels in a worker, not in a scope.
     *
     * Sessions before vehicle on purpose: surviving session docs are what the reconcile
     * resurrects as history of a car that no longer exists.
     */
    fun enqueueDeleteVehicleRemote(vehicleId: String)
}
