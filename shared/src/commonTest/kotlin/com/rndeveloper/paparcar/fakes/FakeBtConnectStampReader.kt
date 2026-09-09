package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.detection.ports.BtConnectStamp
import com.rndeveloper.paparcar.domain.detection.ports.BtConnectStampReader
import com.rndeveloper.paparcar.domain.model.GpsPoint

/**
 * [DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001] The paired car's CONNECT stamp, settable per test.
 * Empty by default: a vehicle with no stamp is the normal case for everything that is not the
 * Bluetooth lane, and the rules must behave exactly as before for it.
 */
class FakeBtConnectStampReader(
    private val stamps: MutableMap<String, BtConnectStamp> = mutableMapOf(),
) : BtConnectStampReader {

    fun stamp(vehicleId: String, atMs: Long, fix: GpsPoint?) {
        stamps[vehicleId] = BtConnectStamp(atMs = atMs, fix = fix)
    }

    override suspend fun lastConnect(vehicleId: String): BtConnectStamp? = stamps[vehicleId]
}
