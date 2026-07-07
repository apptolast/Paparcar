package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Backfills the NEW parking after a step-budget departure verdict. [DET-RECONCILE-001]
 *
 * When the reconcile proves a trip happened while the process slept (fresh anchor + displacement
 * without the steps to walk it), the departure side releases the OLD spot — but the trip also
 * ENDED somewhere, and the wake-up fix bounds where: the user has walked at most
 * stepsSinceAnchor × stride from the just-parked car (~8 m in the 2026-07-06 Oppo trace, 10
 * steps). Below [ParkingDetectionConfig.backfillMaxSteps] that bound beats losing the parking
 * altogether, so the session is confirmed at the fix with LOW reliability and the standard
 * saved-confirm card (ACK / REVERT) for correction.
 *
 * Runs CHAINED AFTER [DepartureDetectionWorker] (WorkManager continuation): confirm replaces the
 * active session per vehicle, so running before the departure processed would make the old
 * session unresolvable by geofenceId — the spot would silently skip publishing.
 */
class ParkingBackfillWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val confirmParking: ConfirmParkingUseCase by inject()
    private val vehicleRepository: VehicleRepository by inject()
    private val notificationPort: AppNotificationManager by inject()

    override suspend fun doWork(): Result {
        val lat = inputData.getDouble(KEY_LAT, Double.NaN).takeIf { !it.isNaN() } ?: return Result.success()
        val lon = inputData.getDouble(KEY_LON, Double.NaN).takeIf { !it.isNaN() } ?: return Result.success()
        val accuracy = inputData.getFloat(KEY_ACCURACY, 50f)
        val fixTimestampMs = inputData.getLong(KEY_FIX_TIMESTAMP, System.currentTimeMillis())
        val reliability = inputData.getFloat(KEY_RELIABILITY, 0.5f)
        // The departed session's vehicle — the one that provably just moved.
        val vehicleId = inputData.getString(KEY_VEHICLE_ID)
            ?: vehicleRepository.observeActiveVehicle().firstOrNull()?.id

        val result = confirmParking(
            location = GpsPoint(lat, lon, accuracy, fixTimestampMs, 0f),
            detectionReliability = reliability,
            vehicleId = vehicleId,
        )
        result
            .onSuccess { saved ->
                PaparcarLogger.d(DIAG, "✓ backfilled parking at $lat,$lon (reliability=$reliability) session=${saved.id}")
                // Same visible, revertible card every auto-confirm posts — an invisible save the
                // user can't correct would be worse than asking.
                val vehicleName: String? = runCatching {
                    vehicleRepository.observeActiveVehicle().firstOrNull()
                        ?.let { v -> v.displayName(fallback = "").takeIf { n -> n.isNotBlank() } }
                }.getOrNull()
                notificationPort.showParkingSavedConfirm(
                    parkingId = saved.id,
                    vehicleName = vehicleName,
                    latitude = saved.location.latitude,
                    longitude = saved.location.longitude,
                )
            }
            .onFailure { e -> PaparcarLogger.w(DIAG, "⊘ backfill confirm refused (${e.message}) — user marks manually") }
        // Never retry: a refused backfill (guard veto, no vehicle) means the estimate wasn't
        // trustworthy enough to insist on — the Home CTA remains the manual path.
        return Result.success()
    }

    companion object {
        const val TAG = "ParkingBackfillWorker"
        private const val DIAG = "PARKDIAG/Backfill"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_ACCURACY = "accuracy"
        private const val KEY_FIX_TIMESTAMP = "fix_timestamp_ms"
        private const val KEY_RELIABILITY = "reliability"
        private const val KEY_VEHICLE_ID = "vehicle_id"

        fun buildRequest(
            fix: GpsPoint,
            vehicleId: String?,
            reliability: Float,
        ): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ParkingBackfillWorker>()
                .setInputData(
                    workDataOf(
                        KEY_LAT to fix.latitude,
                        KEY_LON to fix.longitude,
                        KEY_ACCURACY to fix.accuracy,
                        KEY_FIX_TIMESTAMP to fix.timestamp,
                        KEY_RELIABILITY to reliability,
                        KEY_VEHICLE_ID to vehicleId,
                    )
                )
                .addTag(TAG)
                .build()
    }
}
