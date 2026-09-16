package com.rndeveloper.paparcar.push

import android.content.Context
import androidx.work.WorkManager
import com.rndeveloper.paparcar.detection.worker.GeofenceJanitorWorker
import com.rndeveloper.paparcar.domain.push.SharedCarFenceRefresher

/**
 * En Android, «pasa ya» es una pasada extra del janitor.
 * [DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001]
 *
 * ⛔ El janitor sigue siendo el ÚNICO sitio que registra vallas; esto solo le dice que no espere a
 * su siguiente ventana de quince minutos.
 */
class JanitorSharedCarFenceRefresher(
    private val context: Context,
) : SharedCarFenceRefresher {

    override fun refreshNow() {
        GeofenceJanitorWorker.enqueueOnce(
            WorkManager.getInstance(context),
            GeofenceJanitorWorker.TRIGGER_SHARED_CAR_PUSH,
        )
    }
}
