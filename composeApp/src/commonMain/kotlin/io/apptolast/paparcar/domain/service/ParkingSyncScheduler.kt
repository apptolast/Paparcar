package io.apptolast.paparcar.domain.service

import io.apptolast.paparcar.domain.model.UserParking

/**
 * Schedules background propagation of a newly saved [UserParking] session to the
 * remote backend (Firestore).
 *
 * The implementation (WorkManager on Android) runs the network calls off the
 * critical path of [ConfirmParkingUseCase], so the foreground service can stop
 * the moment the local Room insert finishes — Firestore is reconciled later with
 * automatic retry when the device is online.
 *
 * If a [previousSessionId] is provided, the worker also marks that previous
 * session as inactive in Firestore (mirrors the local `dao.clearActive()` call).
 *
 * @see io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
 */
interface ParkingSyncScheduler {
    fun schedule(session: UserParking, previousSessionId: String?)
}
