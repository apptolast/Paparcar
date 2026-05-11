package io.apptolast.paparcar.domain.service

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking

/**
 * Schedules background propagation of parking-session mutations to the remote backend.
 *
 * All three operations use the same contract:
 * - Fire-and-forget (non-suspending), returns immediately after enqueuing.
 * - Backed by WorkManager on Android — survives process death, retries on failure.
 * - Room is the source of truth; these calls only reconcile Firestore asynchronously.
 *
 * @see io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
 * @see io.apptolast.paparcar.data.repository.UserParkingRepositoryImpl
 */
interface ParkingSyncScheduler {
    /** Propagate a new or updated session (save + optionally mark previous as inactive). [PIPE-001] */
    fun schedule(session: UserParking, previousSessionId: String?)

    /** Mark a session as inactive in Firestore (mirrors local dao.clearActive()). [PIPE-002] */
    fun scheduleClearActive(sessionId: String)

    /** Push geocoder address + POI data to Firestore for an existing session. [PIPE-002] */
    fun scheduleLocationUpdate(sessionId: String, address: AddressInfo?, placeInfo: PlaceInfo?)
}
