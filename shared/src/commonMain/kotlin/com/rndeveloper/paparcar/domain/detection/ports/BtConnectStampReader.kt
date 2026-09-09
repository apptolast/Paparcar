package com.rndeveloper.paparcar.domain.detection.ports

import com.rndeveloper.paparcar.domain.model.GpsPoint

/**
 * What the paired car's last Bluetooth CONNECT recorded: WHEN it happened and WHERE the phone was.
 * [DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001]
 *
 * [fix] is nullable and [atMs] is not, on purpose: a connect always has an instant (the receiver
 * stamps it), while the position is a cached last-known read that may legitimately not exist. A
 * stamp with no fix still says the user got into that car; it just cannot say where.
 */
data class BtConnectStamp(val atMs: Long, val fix: GpsPoint?)

/**
 * Reads the connect stamp of a vehicle's paired device. [DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001]
 *
 * A port because the stamp lives in platform storage (SharedPreferences, written by a manifest
 * BroadcastReceiver so it survives the OEM process kills), while the decision that reads it is a
 * pure rule in commonMain. Every consumer takes it as NULLABLE: a vehicle with no paired MAC, a
 * platform with no Bluetooth lane and every existing test double all answer "no stamp", which the
 * rules already treat as "cannot know" — the safe side.
 */
interface BtConnectStampReader {
    suspend fun lastConnect(vehicleId: String): BtConnectStamp?
}
