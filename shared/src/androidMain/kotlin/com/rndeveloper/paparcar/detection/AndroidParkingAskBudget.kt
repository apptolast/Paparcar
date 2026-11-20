package com.rndeveloper.paparcar.detection

import android.content.Context
import com.rndeveloper.paparcar.detection.worker.ParkingSafetyNetWorker
import com.rndeveloper.paparcar.domain.detection.ParkingAskBudget

/**
 * The per-car interruption budget, on the disk it already lived on.
 * [DET-AN-HONEST-CLOSE-THAT-GOT-IT-RIGHT-MUST-NOT-APOLOGISE-001]
 *
 * ⛔ **Same prefs file, same key prefix** as [ParkingSafetyNetWorker] wrote before this port
 * existed (`parking_safety_net` / `prompt_`). Both flows use the same store.
 * Historical fence-keyed stamps are not migrated to vehicle keys.
 *
 * SharedPreferences and not DataStore for the reason the anchors already use it: this is read and
 * written from a WorkManager worker and a foreground service on devices whose OEM kills the process
 * mid-tick, and the value must be there synchronously on the next start. [ANCHOR-PERSIST-001]
 *
 * What DID change is the key's meaning — fence → vehicle — so a car that re-parks keeps its budget
 * instead of minting a fresh one with every new pin. The old per-fence stamps are simply never read
 * again; the worker's janitor drops keys not owned by a vehicle.
 */
class AndroidParkingAskBudget(private val appContext: Context) : ParkingAskBudget {

    private fun prefs() = appContext.getSharedPreferences(
        ParkingSafetyNetWorker.PREFS_NAME, Context.MODE_PRIVATE,
    )

    override fun lastAskAtMs(vehicleId: String): Long? =
        prefs().getLong(ParkingSafetyNetWorker.ASK_KEY_PREFIX + vehicleId, 0L).takeIf { it > 0L }

    override fun stampAsk(vehicleId: String, atMs: Long) {
        check(prefs().edit().putLong(ParkingSafetyNetWorker.ASK_KEY_PREFIX + vehicleId, atMs).commit()) {
            "Could not persist parking ask budget"
        }
    }
}
