package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * What a fence reconcile must DO: register the fences active sessions expect and drop the ones no
 * session owns. [IOS-F1-A-CONTROLLER-FOR-THE-HAPPY-PATH-001]
 *
 * iOS is where this earns its keep: `CLCircularRegion`s survive reboots and app kills, but the
 * inventory can still desync from Room — reinstall (regions gone, sessions restored from remote),
 * "Reset Location & Privacy", or a `monitoringDidFail`. Android's `GeofenceJanitorWorker` covers
 * the same duty on its own schedule; the decision is shared so the two can never diverge on what
 * "in sync" means.
 *
 * Top-level pure function, not an injected verdict class: it feeds the controller's reconcile,
 * it is not a diagnosable verdict of its own. [DET-VERDICT-NOT-PREDICATE-001] (pattern:
 * `VehicleFenceOwnershipPolicy`, `SessionSupersede`).
 */
data class FenceReconcileActions(
    /** Sessions whose fence must be (re-)registered — they carry a fence id the OS is not monitoring. */
    val toRegister: List<UserParking>,
    /** Monitored ids no active session owns — orphans to stop monitoring. */
    val toRemove: Set<String>,
) {
    val isInSync: Boolean get() = toRegister.isEmpty() && toRemove.isEmpty()
}

/**
 * @param activeSessions the active parking sessions (Room truth). Sessions with a null
 *   [UserParking.geofenceId] are skipped: no fence was ever minted for them, and the reconcile
 *   restores inventory — it never invents identity.
 * @param monitoredIds the region ids the OS is currently monitoring for this app.
 * @param maxRegions the platform budget (iOS caps an app at 20 monitored regions). When the wanted
 *   set exceeds it, the FIRST [maxRegions] sessions win — callers pass sessions most-recent-first
 *   so the budget drops the oldest parks, never the newest.
 */
fun reconcileFences(
    activeSessions: List<UserParking>,
    monitoredIds: Set<String>,
    maxRegions: Int = IOS_REGION_BUDGET,
): FenceReconcileActions {
    val wanted = activeSessions
        .filter { it.geofenceId != null }
        .take(maxRegions)
    val wantedIds = wanted.mapNotNull { it.geofenceId }.toSet()
    return FenceReconcileActions(
        toRegister = wanted.filter { it.geofenceId !in monitoredIds },
        toRemove = monitoredIds - wantedIds,
    )
}

/** iOS caps an app at 20 monitored `CLRegion`s; one fence per active session keeps us far below,
 *  and the reconcile enforces it instead of trusting that. */
const val IOS_REGION_BUDGET = 20
