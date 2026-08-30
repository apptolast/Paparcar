package com.rndeveloper.paparcar.domain.bluetooth

import com.rndeveloper.paparcar.domain.model.bluetooth.BluetoothDeviceInfo
import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic interface for querying Bluetooth state and bonded devices.
 *
 * Uses bonded (already-paired) devices only — no active scanning required.
 * This avoids the BLUETOOTH_SCAN permission and the battery cost of discovery.
 */
interface BluetoothScanner {
    /** Whether the device's Bluetooth adapter is currently enabled. */
    fun isBluetoothEnabled(): Boolean

    /**
     * Returns the list of Bluetooth devices currently bonded (paired) with this phone.
     * Requires BLUETOOTH_CONNECT permission on Android 12+ (API 31+).
     * Returns an empty list if Bluetooth is disabled or the permission is missing.
     */
    fun getBondedDevices(): List<BluetoothDeviceInfo>

    /**
     * True when the phone is currently CONNECTED (ACL link up) to the paired car of at least one of
     * [pairedVehicleIds] — not merely bonded. This is what hands detection to the deterministic
     * Bluetooth pipeline: only a connected car means "I'm driving THIS car", so the probabilistic
     * Coordinator is suppressed. A car that is only paired-and-enabled (you're driving a different,
     * non-BT car) must NOT hijack the strategy — the Coordinator + resident FGS handle that car.
     * [DET-BT-CONNECTED-NOT-PAIRED-001]
     */
    fun isConnectedToPairedCar(pairedVehicleIds: Set<String>): Boolean

    /**
     * The reactive twin of [isConnectedToPairedCar]: the paired cars whose link is up RIGHT NOW,
     * re-emitted on every ACL connect/disconnect edge.
     * [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001]
     *
     * The point-in-time read above answers the same question, but only when somebody asks — and the
     * ACL edge is not a source of any flow the app collects, so nothing asks. Anything that has to
     * *react* to getting into the car (the Home readiness banner, and through it the driving puck)
     * needs the edge pushed at it, not polled.
     *
     * Carries [BtConnection.connectedAtMs] and not just the ids because **a fleet can have more than
     * one paired car**, and then "which one am I in" has to be answered rather than guessed.
     *
     * Emits the current state immediately on collection, so a late subscriber (Home opened mid-drive)
     * is not left blind until the next edge.
     */
    fun observeConnectedPairedCars(): Flow<List<BtConnection>>
}

/**
 * A paired car whose ACL link is up, and when it came up.
 * [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001]
 *
 * @param connectedAtMs epoch-ms of the `ACL_CONNECTED` that opened this link, or `0` when there is
 *   no usable stamp on record (prefs cleared, app installed mid-engagement). Zero means "cannot be
 *   ordered", never "oldest" — a caller that has to rank two cars must treat it as unknown.
 */
data class BtConnection(val vehicleId: String, val connectedAtMs: Long)