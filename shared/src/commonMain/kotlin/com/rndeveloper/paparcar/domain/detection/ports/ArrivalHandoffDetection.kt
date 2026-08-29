package com.rndeveloper.paparcar.domain.detection.ports

import com.rndeveloper.paparcar.domain.detection.DetectionTrigger

import com.rndeveloper.paparcar.domain.detection.ArmEvidence

/**
 * [DET-HANDOFF-NOT-MANUAL-001] Starts live detection for the REST of a trip the safety net just
 * dispatched a departure for [DET-ARRIVAL-HANDOFF-001] — so the next park is captured at full
 * quality instead of being orphaned.
 *
 * Deliberately a port of its own, not a second method on [ManualParkingDetection]: both callers
 * used to go through that one door (`ACTION_START_TRACKING`), so a worker-born session arrived
 * stamped with the user's own intent — `ARM:MANUAL`, exempt from the strategy gate, evidence
 * counted as strong. One door per meaning is what makes the two impossible to confuse again; the
 * arm lands as [DetectionTrigger.ARRIVAL_HANDOFF] with [ArmEvidence.ArrivalHandoff].
 *
 * No-op where automatic detection isn't available yet (iOS).
 */
interface ArrivalHandoffDetection {
    /**
     * Begin following the current trip after a dispatched departure. Safe to call repeatedly (the
     * service is idempotent). The platform may refuse a background foreground-service start
     * (Android 12+/OEM); the caller must have a fallback that asks the user rather than going
     * silent.
     */
    fun start()
}
