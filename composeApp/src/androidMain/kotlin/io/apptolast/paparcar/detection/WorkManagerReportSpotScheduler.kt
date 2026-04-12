package io.apptolast.paparcar.detection

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import io.apptolast.paparcar.detection.worker.ReportSpotWorker
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.service.ReportSpotScheduler

/**
 * Android implementation of [ReportSpotScheduler] backed by WorkManager.
 *
 * Uses [ExistingWorkPolicy.REPLACE] so a duplicate enqueue for the same spot
 * (e.g. after a process restart) always runs with the freshest data.
 */
class WorkManagerReportSpotScheduler(
    private val context: Context,
) : ReportSpotScheduler {

    override fun schedule(
        spotId: String,
        lat: Double,
        lon: Double,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
        spotType: SpotType,
        confidence: Float,
        sizeCategory: VehicleSize?,
    ) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${ReportSpotWorker.TAG}_$spotId",
            ExistingWorkPolicy.REPLACE,
            ReportSpotWorker.buildRequest(spotId, lat, lon, address, placeInfo, spotType, confidence, sizeCategory),
        )
    }
}
