package io.apptolast.paparcar.domain.service

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking

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
 *
 * @see io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
 * @see io.apptolast.paparcar.data.repository.UserParkingRepositoryImpl
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
     */
    fun enqueueClearActiveParkingSession(sessionId: String)

    /**
     * Pushes geocoder-resolved address and POI data to Firestore for an existing session.
     *
     * Uses a partial update() so it never overwrites coordinates set by [enqueueSaveNewParkingSession].
     * Enqueued by [UserParkingRepositoryImpl.updateParkingSessionAddressAndPlace] after the local Room write,
     * chained after [enqueueSaveNewParkingSession] to avoid NOT_FOUND on first delivery. [PIPE-002]
     */
    fun enqueueUpdateParkingSessionAddressAndPlace(sessionId: String, address: AddressInfo?, placeInfo: PlaceInfo?)
}
