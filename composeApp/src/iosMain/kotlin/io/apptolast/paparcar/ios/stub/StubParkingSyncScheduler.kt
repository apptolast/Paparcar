package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler

/**
 * iOS stub. Phase 6 will replace with a real implementation that uses
 * `BGProcessingTaskRequest` + Firestore SDK (when the Mac build lands).
 *
 * Until then this is a no-op, mirroring how [StubParkingEnrichmentScheduler]
 * was originally a no-op before [IosParkingEnrichmentScheduler] replaced it.
 */
class StubParkingSyncScheduler : ParkingSyncScheduler {
    override fun schedule(session: UserParking, previousSessionId: String?) = Unit
}
