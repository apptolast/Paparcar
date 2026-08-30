package com.rndeveloper.paparcar.bluetooth

import android.content.Context
import android.content.SharedPreferences
import com.rndeveloper.paparcar.domain.bluetooth.BtConnection
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

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

    /**
     * [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001] The same set, pushed on every edge.
     *
     * The prefs change listener is the cheapest honest edge we have: the ACL receiver already writes
     * here, so a collector learns that the user got into the car at the exact moment the app does —
     * without a second source of truth to drift, and without holding a BluetoothProfile proxy open
     * for the lifetime of the screen. Emits the current set on collection so a subscriber that
     * arrives mid-drive is not blind until the next edge.
     *
     * ⚠ Android registers the change listener behind a WEAK reference, so a listener nobody holds is
     * collected mid-drive and the edges silently stop arriving. Here the `listener` local is captured
     * by [awaitClose], which lives as long as the flow does — that capture is what keeps it alive,
     * not an accident of style. Do not inline it into the register call.
     */
    fun observeConnected(context: Context): Flow<List<BtConnection>> = callbackFlow {
        val prefs = prefs(context)
        trySend(connected(context))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CONNECTED_SET || key == null) trySend(connected(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** The live links, each paired with the connect stamp this store already keeps for it. The two
     *  keys are written together by the receiver, so reading them together costs nothing and is what
     *  lets a caller tell TWO connected cars apart. */
    private fun connected(context: Context): List<BtConnection> =
        connectedVehicleIds(context).map { id ->
            BtConnection(vehicleId = id, connectedAtMs = lastConnectedAt(context, id) ?: 0L)
        }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "bt_identity"
    private const val KEY_PREFIX = "bt_connected_"
    private const val KEY_CONNECTED_SET = "bt_connected_now"
}
