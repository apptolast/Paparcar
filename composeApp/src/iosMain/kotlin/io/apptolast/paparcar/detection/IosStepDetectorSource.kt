package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.sensor.StepDetectorSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * iOS stub. Returns an empty flow so the coordinator's timeout-based fallback handles
 * candidate validation on iOS until a CMPedometer-backed implementation is wired.
 *
 * Tracked in `docs/backlog/detection-improvements-2026-05-27.md` — not in scope of the
 * Android-first BUG-GARAGE-COLA-001 ticket.
 */
class IosStepDetectorSource : StepDetectorSource {
    override fun steps(): Flow<Unit> = emptyFlow()
}
