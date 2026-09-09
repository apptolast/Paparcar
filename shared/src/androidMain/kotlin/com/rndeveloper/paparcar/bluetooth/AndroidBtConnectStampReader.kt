package com.rndeveloper.paparcar.bluetooth

import android.content.Context
import com.rndeveloper.paparcar.domain.detection.ports.BtConnectStamp
import com.rndeveloper.paparcar.domain.detection.ports.BtConnectStampReader

/**
 * [DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001] The Android half of [BtConnectStampReader]: reads
 * the two keys the ACL receiver writes together in [BtConnectionStore].
 *
 * Both are read in one call because they are one fact — an instant without its place would let a
 * caller measure a ride from nowhere, which is why the receiver writes them in a single `edit`.
 */
class AndroidBtConnectStampReader(private val context: Context) : BtConnectStampReader {

    override suspend fun lastConnect(vehicleId: String): BtConnectStamp? {
        val atMs = BtConnectionStore.lastConnectedAt(context, vehicleId) ?: return null
        return BtConnectStamp(atMs = atMs, fix = BtConnectionStore.lastConnectFix(context, vehicleId))
    }
}
