package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.detection.OpenDepartureAdjudication
import com.rndeveloper.paparcar.domain.detection.ParkingAskBudget
import platform.Foundation.NSUserDefaults

/**
 * The safety-net mesh's durable memory on iOS — NSUserDefaults twins of the Android worker's
 * prefs slots, same key layout so the two platforms' diagnostics read alike.
 * [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001]
 *
 * Slots: the ANCHOR (when the body was last provably near the car — the cure writes it, the
 * step budget measures from it), the CURE stamp (fence re-registration throttle), the departure
 * ADJUDICATION (one fact, one dispatch [DET-TWO-DISPATCHES]), and the PROMPT throttle.
 *
 * The witness slot (`last_witnessed_*`) has NO iOS twin on purpose: it exists to vouch for a
 * cumulative counter's liveness, and iOS has no cumulative counter to vouch for.
 *
 * ⛔ The PROMPT slot is this platform's [ParkingAskBudget], so the interruption door in
 * `commonMain` works here without a second store — and the honest close, which runs on both
 * platforms, is rationed by the same clock on both.
 * [DET-AN-HONEST-CLOSE-THAT-GOT-IT-RIGHT-MUST-NOT-APOLOGISE-001]
 */
class IosSafetyNetRecords(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ParkingAskBudget {
    fun writeAnchor(geofenceId: String, atMs: Long) {
        defaults.setObject(atMs.toString(), forKey = KEY_NAMESPACE + ANCHOR_PREFIX + geofenceId)
    }

    fun lastSeenNearCarAtMs(geofenceId: String): Long? =
        (defaults.objectForKey(KEY_NAMESPACE + ANCHOR_PREFIX + geofenceId) as? String)?.toLongOrNull()

    fun stampCure(geofenceId: String, atMs: Long) {
        defaults.setObject(atMs.toString(), forKey = KEY_NAMESPACE + CURE_PREFIX + geofenceId)
    }

    fun lastCureAtMs(geofenceId: String): Long? =
        (defaults.objectForKey(KEY_NAMESPACE + CURE_PREFIX + geofenceId) as? String)?.toLongOrNull()

    fun writeAdjudication(geofenceId: String, open: OpenDepartureAdjudication) {
        defaults.setObject(
            "${open.openedAtMs},${open.preconfirmed}",
            forKey = KEY_NAMESPACE + ADJUDICATION_PREFIX + geofenceId,
        )
    }

    fun openAdjudication(geofenceId: String): OpenDepartureAdjudication? {
        val raw = defaults.objectForKey(KEY_NAMESPACE + ADJUDICATION_PREFIX + geofenceId) as? String
            ?: return null
        val parts = raw.split(",")
        val atMs = parts.getOrNull(0)?.toLongOrNull() ?: return null
        return OpenDepartureAdjudication(atMs, parts.getOrNull(1)?.toBooleanStrictOrNull() ?: false)
    }

    /** ⛔ Keyed by VEHICLE since [DET-AN-HONEST-CLOSE-THAT-GOT-IT-RIGHT-MUST-NOT-APOLOGISE-001]:
     *  a budget keyed by fence cannot throttle a door that mints a fence every time it fires.
     *  Legacy fence stamps are not migrated; new stamps outlive session replacement. */
    override fun stampAsk(vehicleId: String, atMs: Long) {
        defaults.setObject(atMs.toString(), forKey = KEY_NAMESPACE + PROMPT_PREFIX + vehicleId)
    }

    override fun lastAskAtMs(vehicleId: String): Long? =
        (defaults.objectForKey(KEY_NAMESPACE + PROMPT_PREFIX + vehicleId) as? String)?.toLongOrNull()

    /** Drop every slot keyed by a fence no active session owns — the mesh's own hygiene.
     *
     *  ⛔ [PROMPT_PREFIX] is NOT in that sweep any more: it is keyed by vehicle and must outlive
     *  the session it was spent in, or the next close mints a fresh budget.
     *  [DET-AN-HONEST-CLOSE-THAT-GOT-IT-RIGHT-MUST-NOT-APOLOGISE-001] */
    fun pruneAllExcept(activeGeofenceIds: Set<String>, activeVehicleIds: Set<String>?) {
        val prefixes = listOf(ANCHOR_PREFIX, CURE_PREFIX, ADJUDICATION_PREFIX)
        defaults.dictionaryRepresentation().keys
            .filterIsInstance<String>()
            .filter { key -> key.startsWith(KEY_NAMESPACE) }
            .forEach { key ->
                val local = key.removePrefix(KEY_NAMESPACE)
                if (local.startsWith(PROMPT_PREFIX)) {
                    if (activeVehicleIds != null && local.removePrefix(PROMPT_PREFIX) !in activeVehicleIds) {
                        defaults.removeObjectForKey(key)
                    }
                    return@forEach
                }
                val prefix = prefixes.firstOrNull { local.startsWith(it) } ?: return@forEach
                if (local.removePrefix(prefix) !in activeGeofenceIds) defaults.removeObjectForKey(key)
            }
    }

    private companion object {
        const val KEY_NAMESPACE = "parking_safety_net."
        const val ANCHOR_PREFIX = "anchor_at_"
        const val CURE_PREFIX = "cure_registered_"
        const val ADJUDICATION_PREFIX = "adjudication_"
        const val PROMPT_PREFIX = "prompt_"
    }
}
