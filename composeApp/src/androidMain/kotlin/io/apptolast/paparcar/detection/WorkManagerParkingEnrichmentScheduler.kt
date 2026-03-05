package io.apptolast.paparcar.detection

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import io.apptolast.paparcar.detection.worker.EnrichParkingSessionWorker
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler

/**
 * Android implementation of [ParkingEnrichmentScheduler] backed by WorkManager.
 *
 * Uses [ExistingWorkPolicy.REPLACE] so a duplicate enqueue (e.g. after process restart)
 * always runs the latest coordinates.
 */
class WorkManagerParkingEnrichmentScheduler(
    private val context: Context,
) : ParkingEnrichmentScheduler {

    override fun schedule(sessionId: String, lat: Double, lon: Double) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${EnrichParkingSessionWorker.TAG}_$sessionId",
            ExistingWorkPolicy.REPLACE,
            EnrichParkingSessionWorker.buildRequest(sessionId, lat, lon),
        )
    }
}
