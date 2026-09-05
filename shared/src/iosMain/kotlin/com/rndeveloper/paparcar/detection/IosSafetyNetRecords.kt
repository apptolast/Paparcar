package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.detection.OpenDepartureAdjudication
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
 */
class IosSafetyNetRecords(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) {
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

    fun stampPrompt(geofenceId: String, atMs: Long) {
        defaults.setObject(atMs.toString(), forKey = KEY_NAMESPACE + PROMPT_PREFIX + geofenceId)
    }

    fun lastPromptAtMs(geofenceId: String): Long? =
        (defaults.objectForKey(KEY_NAMESPACE + PROMPT_PREFIX + geofenceId) as? String)?.toLongOrNull()

    /** Drop every slot keyed by a fence no active session owns — the mesh's own hygiene. */
    fun pruneAllExcept(activeGeofenceIds: Set<String>) {
        val prefixes = listOf(ANCHOR_PREFIX, CURE_PREFIX, ADJUDICATION_PREFIX, PROMPT_PREFIX)
        defaults.dictionaryRepresentation().keys
            .filterIsInstance<String>()
            .filter { key -> key.startsWith(KEY_NAMESPACE) }
            .forEach { key ->
                val local = key.removePrefix(KEY_NAMESPACE)
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
