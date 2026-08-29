package com.rndeveloper.paparcar.detection

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.rndeveloper.paparcar.detection.worker.ReportSpotWorker
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.service.ReportSpotScheduler

/**
 * Android implementation of [ReportSpotScheduler] backed by WorkManager.
 *
 * Uses [ExistingWorkPolicy.REPLACE] so a duplicate enqueue for the same spot
 * (e.g. after a process restart) always runs with the freshest data.
 */
class WorkManagerReportSpotScheduler(
    private val context: Context,
) : ReportSpotScheduler {

    override fun enqueueReportSpot(
        spotId: String,
        lat: Double,
        lon: Double,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
        spotType: SpotType,
        confidence: Float,
        sizeCategory: VehicleSize?,
        carbodyType: CarbodyType?,
        reportedBy: String?,
        provisional: Boolean,
    ) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${ReportSpotWorker.TAG}_$spotId",
            ExistingWorkPolicy.REPLACE,
            ReportSpotWorker.buildRequest(spotId, lat, lon, address, placeInfo, spotType, confidence, sizeCategory, carbodyType, reportedBy, provisional),
        )
    }
}
