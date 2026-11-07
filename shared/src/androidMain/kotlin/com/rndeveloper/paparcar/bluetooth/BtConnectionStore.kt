package com.rndeveloper.paparcar.bluetooth

import android.content.Context
import android.content.SharedPreferences
import com.rndeveloper.paparcar.domain.bluetooth.BtConnection
import com.rndeveloper.paparcar.domain.model.GpsPoint
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json

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

    /**
     * Stamp a fresh ACL connection to [vehicleId]'s paired device. Called by the BT receiver.
     *
     * [fix] is where the phone was when the car connected — the ORIGIN the disconnect will measure
     * the ride against. [DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001] It is a cached last-known read,
     * so it costs no GPS session and may legitimately be null (nothing cached, permission denied);
     * null is recorded as absence, never as zero, because a (0,0) origin would fabricate a
     * displacement across half the planet.
     *
     * Both keys are written in ONE edit so the stamp and its origin can never disagree: a reader
     * that found a time without a place would measure a ride from nowhere.
     */
    fun recordConnected(context: Context, vehicleId: String, atMs: Long, fix: GpsPoint?) {
        val encoded = fix?.let { runCatching { Json.encodeToString(GpsPoint.serializer(), it) }.getOrNull() }
        prefs(context).edit {
            putLong(KEY_PREFIX + vehicleId, atMs)
            if (encoded != null) putString(KEY_FIX_PREFIX + vehicleId, encoded)
            // A connect with no origin must ERASE the previous one — otherwise the next disconnect
            // measures this ride from the place the car connected two trips ago.
            else remove(KEY_FIX_PREFIX + vehicleId)
        }
    }

    /** Epoch-ms of the last recorded connection to [vehicleId]'s device, or null if never. */
    fun lastConnectedAt(context: Context, vehicleId: String): Long? =
        prefs(context).getLong(KEY_PREFIX + vehicleId, 0L).takeIf { it > 0L }

    /**
     * Where the phone was at the last recorded connection to [vehicleId]'s device, or null when
     * none was cached (or the stored value no longer decodes — an app upgrade that changed the fix
     * shape must degrade to "unmeasurable", never to a crash in a BroadcastReceiver).
     * [DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001]
     */
    fun lastConnectFix(context: Context, vehicleId: String): GpsPoint? =
        prefs(context).getString(KEY_FIX_PREFIX + vehicleId, null)
            ?.let { runCatching { Json.decodeFromString(GpsPoint.serializer(), it) }.getOrNull() }

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

    /**
     * Mark [vehicleId]'s paired device as no longer connected, **and stamp when**. Called on
     * ACL_DISCONNECTED.
     *
     * [DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001] El sello es lo que deja al árbitro distinguir
     * una reconexión-rebote de un embarque de verdad: sin él, un manos libres que se cae y vuelve
     * cada pocos segundos vetaba —una y otra vez— la sesión del coordinator que estaba a punto de
     * confirmar el aparcamiento. Disco, y no memoria, por la misma razón que el sello de connect: el
     * flanco llega a un BroadcastReceiver que corre a través de las muertes de proceso del OEM, y un
     * bus en memoria estaría vacío en cada despertar.
     *
     * Ambas claves en UNA edición, como en [recordConnected]: un lector que encontrase el set sin la
     * hora (o al revés) leería el flanco a medias.
     */
    fun markDisconnected(context: Context, vehicleId: String, atMs: Long) {
        val next = connectedVehicleIds(context) - vehicleId
        prefs(context).edit {
            putStringSet(KEY_CONNECTED_SET, next)
            putLong(KEY_DISCONNECT_PREFIX + vehicleId, atMs)
        }
    }

    /** Epoch-ms of the last recorded DISCONNECT from [vehicleId]'s device, or null if never.
     *  [DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001] */
    fun lastDisconnectedAt(context: Context, vehicleId: String): Long? =
        prefs(context).getLong(KEY_DISCONNECT_PREFIX + vehicleId, 0L).takeIf { it > 0L }

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
            if (key == KEY_CONNECTED_SET || key == KEY_MANUAL_FINISH || key?.startsWith(KEY_PREFIX) == true || key == null) trySend(connected(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** The live links, each paired with the connect stamp this store already keeps for it. The two
     *  keys are written together by the receiver, so reading them together costs nothing and is what
     *  lets a caller tell TWO connected cars apart. */
    private fun connected(context: Context): List<BtConnection> =
        connectedVehicleIds(context).filterNot { isTripFinished(context, it) }.map { id ->
            BtConnection(vehicleId = id, connectedAtMs = lastConnectedAt(context, id) ?: 0L)
        }

    /** A manual resolution consumes the current ride, including a disconnect delivered later. */
    fun finishCurrentTrips(context: Context, atMs: Long) {
        prefs(context).edit { putLong(KEY_MANUAL_FINISH, atMs) }
    }

    fun isTripFinished(context: Context, vehicleId: String): Boolean {
        val finishedAt = prefs(context).getLong(KEY_MANUAL_FINISH, 0L)
        val connectedAt = lastConnectedAt(context, vehicleId) ?: return false
        return finishedAt > 0L && connectedAt <= finishedAt
    }

    private const val KEY_MANUAL_FINISH = "manual_trip_finished_at"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "bt_identity"
    private const val KEY_PREFIX = "bt_connected_"
    private const val KEY_CONNECTED_SET = "bt_connected_now"
    /** [DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001] Origin fix of the current engagement, JSON-encoded
     *  next to its timestamp. One key per vehicle, same join as the stamp above. */
    private const val KEY_FIX_PREFIX = "bt_connect_fix_"
    /** [DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001] Hora del último flanco de desconexión, la
     *  referencia contra la que se mide si el siguiente connect es un rebote del enlace. */
    private const val KEY_DISCONNECT_PREFIX = "bt_disconnected_"
}
