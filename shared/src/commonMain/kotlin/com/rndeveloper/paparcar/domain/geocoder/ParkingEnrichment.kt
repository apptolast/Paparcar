package com.rndeveloper.paparcar.domain.geocoder

import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.Flow

/**
 * Stamps a parking session with what the geocoder answered, and says whether that enrichment is
 * FINISHED or has to be asked again. [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
 *
 * ## Why this is one function and not two copies
 *
 * Android (`EnrichParkingSessionWorker`) and iOS (`SyncOutboxDrainer`, `OutboxOp.EnrichSession`)
 * each carried their own literal copy of this loop, down to the same `var saved = false`. Both
 * therefore carried the same bug: `saved` was raised by the PHASE-1 emission — the street — so a
 * lookup that never reached Overpass finished indistinguishable from a street with no place near
 * it, and both platforms called it done. `done` is terminal on both: WorkManager closes the work,
 * the drainer drops the op, and the only thing left in the app that re-enqueues enrichment is the
 * user dragging the pin by hand.
 *
 * Measured 14-09-2026 (El Puerto de Santa María): three of four Overpass calls answered 504 and the
 * other two mirrors answered nothing at all, so a session parked 14 m from a supermarket kept
 * "Diseminado 2.4 50" as its final answer, for good.
 *
 * ## The rule
 *
 * > An enrichment is complete when BOTH questions have an answer. *"I could not ask"* is a retry;
 * > *"I asked and there is no place within 40 m"* is an answer.
 *
 * Which is why [AddressAndPlace.poiChecked] exists: from outside the repository the two used to be
 * the same `placeInfo == null`.
 *
 * @param answers the geocoder flow, already `catch`-ed by the caller — the log line belongs to
 *   whoever knows its attempt count and tag, and a throw here would be read as "not finished",
 *   which for a transport failure is exactly right.
 * @return true when the session was stamped AND the POI question was answered.
 */
suspend fun enrichParkingSession(
    sessionId: String,
    answers: Flow<AddressAndPlace>,
    repository: UserParkingRepository,
): Boolean {
    var addressSaved = false
    var poiAnswered = false

    answers.collect { info ->
        // A borrowed-neighbour answer is display-only: persisting it would stamp a guess as the
        // session's real street, and its cell's seal says nothing about THIS point's POI. Skipping
        // it leaves both flags down → asked again. [GEO-CACHE-ANSWERS-NEARBY-001]
        if (info.approximate) return@collect
        if (info.poiChecked) poiAnswered = true
        // The no-POI answer carries nothing phase 1 did not already stamp. Writing it again would
        // re-dirty the row and enqueue a second identical remote push for every park without a
        // place nearby — which is most parks.
        if (addressSaved && info.placeInfo == null) return@collect
        repository
            .updateParkingSessionAddressAndPlace(sessionId, info.address, info.placeInfo)
            .onSuccess { addressSaved = true }
    }

    return addressSaved && poiAnswered
}
