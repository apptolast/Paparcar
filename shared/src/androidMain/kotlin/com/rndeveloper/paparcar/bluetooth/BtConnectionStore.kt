package com.rndeveloper.paparcar.bluetooth

import android.content.Context
import androidx.core.content.edit

/**
 * Disk-backed record of the last time Bluetooth connected to each vehicle's paired car MAC.
 * [DET-BT-IDENTITY-GATE-001]
 *
 * The parked-session safety net reads this as the identity proof for a BT-paired vehicle: an
 * auto-release it RECONSTRUCTS (step budget / AR / physics) may only fire when the BT connected to
 * this car at or after it parked — otherwise the far movement was a ride in ANOTHER vehicle boarded
 * next to the parked car (field 2026-07-18, Redmi). SharedPreferences on purpose: the write happens
 * in a BroadcastReceiver and the read in a WorkManager worker, both of which run across the OEM
 * process kills the safety net is built around, so an in-memory bus would be empty on every wake-up.
 *
 * Keyed by vehicleId (the join the receiver already resolves from the device MAC, and the safety
 * net already has on each active session) — NOT by MAC, so it survives a user re-pairing the car.
 */
object BtConnectionStore {

    /** Stamp a fresh ACL connection to [vehicleId]'s paired device. Called by the BT receiver. */
    fun recordConnected(context: Context, vehicleId: String, atMs: Long) {
        prefs(context).edit { putLong(KEY_PREFIX + vehicleId, atMs) }
    }

    /** Epoch-ms of the last recorded connection to [vehicleId]'s device, or null if never. */
    fun lastConnectedAt(context: Context, vehicleId: String): Long? =
        prefs(context).getLong(KEY_PREFIX + vehicleId, 0L).takeIf { it > 0L }

    // ── Live connection STATE (which paired cars are connected right now) ──────────────────────────
    // [DET-BT-CONNECTED-NOT-PAIRED-001] The strategy resolver reads this to let BLUETOOTH own
    // detection ONLY while actually connected to a paired car — merely having one paired no longer
    // hijacks the strategy when you drive a different, non-BT car. Driven by the ACL receiver's
    // connect/disconnect edges (a manifest receiver → fires across the OEM process kills, same
    // rationale as the identity stamp above), so this is our own ground truth, not a live poll.

    /** Mark [vehicleId]'s paired device as currently connected. Called on ACL_CONNECTED. */
    fun markConnected(context: Context, vehicleId: String) {
        val next = connectedVehicleIds(context) + vehicleId
        prefs(context).edit { putStringSet(KEY_CONNECTED_SET, next) }
    }

    /** Mark [vehicleId]'s paired device as no longer connected. Called on ACL_DISCONNECTED. */
    fun markDisconnected(context: Context, vehicleId: String) {
        val next = connectedVehicleIds(context) - vehicleId
        prefs(context).edit { putStringSet(KEY_CONNECTED_SET, next) }
    }

    /** The vehicleIds whose paired car is connected right now (per the last ACL edge). */
    fun connectedVehicleIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_CONNECTED_SET, emptySet()).orEmpty().toSet()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "bt_identity"
    private const val KEY_PREFIX = "bt_connected_"
    private const val KEY_CONNECTED_SET = "bt_connected_now"
}
