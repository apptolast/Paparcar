@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.detection.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rndeveloper.paparcar.detection.service.CoordinatorDetectionService
import com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.shouldArmAfterDeclinedBoarding
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.usecase.location.GetOneLocationUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * [DET-A-DECLINED-ARM-IS-NOT-SILENCE-001] The second look at a boarding the AR ladder declined.
 *
 * `EvaluateArEnterArmUseCase` answers `TickOnly` when a fresh `IN_VEHICLE_ENTER` arrives with its fix
 * outside the parked car's fence. That decline is right — AR fires on buses — but it used to be
 * followed by silence and `enterSentry` (GPS off), and the watchers left behind all share one
 * evaluator that answers `None` between the fence radius and 300 m. So nothing looked again until
 * the car was already far away (field 2026-08-30: 6 min 51 s and 3,6 km).
 *
 * This buys ONE fix, once, after [RELOOK_DELAY_SECONDS]. The delay is the point: the boarding fix
 * itself cannot decide (it read 0.22 m/s that day — still walking, or barely pulling away), so the
 * question is deliberately asked late enough for driving to exist and early enough to matter. The
 * verdict is [shouldArmAfterDeclinedBoarding], a pure function with its own tests; this worker only
 * does the I/O.
 *
 * Unique work per geofence with [ExistingWorkPolicy.REPLACE]: a burst of ENTERs at one park schedules
 * one look, not a queue of them.
 */
class DeclinedBoardingRelookWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val userParkingRepository: UserParkingRepository by inject()
    private val getOneLocation: GetOneLocationUseCase by inject()
    private val detectionRuntime: DetectionRuntimeState by inject()
    private val config: ParkingDetectionConfig by inject()

    override suspend fun doWork(): Result {
        val geofenceId = inputData.getString(KEY_GEOFENCE_ID) ?: return Result.success()

        // Something already took the trip — the geofence EXIT that normally arrives within 100 ms,
        // or the safety net. Nothing to rescue. Mirrors the `isRunning` skip every other worker that
        // can arm uses. [DET-ARRIVAL-DOUBLE-PIN-001]
        if (detectionRuntime.isRunning.value) {
            PaparcarLogger.d(DIAG, "■ live detection already running — nothing to re-look at")
            return Result.success()
        }

        // The park must still be there. If it was released while we waited, the boarding we declined
        // has already been explained by whatever released it.
        val session = userParkingRepository.getActiveSessionByGeofence(geofenceId)
            ?: run {
                PaparcarLogger.d(DIAG, "■ the park is gone — the boarding was already accounted for")
                return Result.success()
            }

        val fix = runCatching { getOneLocation(maxAgeMs = config.freshFixMaxAgeMs) }.getOrNull()

        if (!shouldArmAfterDeclinedBoarding(fix, session, config)) {
            PaparcarLogger.d(
                DIAG,
                "⊘ re-look says nothing is driving (speed=${fix?.speed ?: "?"}m/s acc=${fix?.accuracy ?: "?"}m) " +
                    "— the decline stands, and it cost one fix [DET-A-DECLINED-ARM-IS-NOT-SILENCE-001]",
            )
            return Result.success()
        }

        PaparcarLogger.d(
            DIAG,
            "✓ re-look MEASURED driving away from the car (speed=${fix?.speed}m/s acc=${fix?.accuracy}m) " +
                "— arming; the boarding nominated, the movement armed [DET-A-DECLINED-ARM-IS-NOT-SILENCE-001]",
        )
        // Through the service's own door, like every other lane. The arm carries
        // ArmEvidence.BoardedAwayFromCar → DriveAuthorization.None: follow the trip, never confirm a
        // park in silence. A bus ride costs one question.
        runCatching {
            CoordinatorDetectionService.startBoardedAwayArm(applicationContext, geofenceId)
        }.onFailure { e ->
            // Background FGS-start may be denied (Android 12+/OEM). Nothing is lost that was not
            // already lost before this ticket: the safety net remains the backstop it has always
            // been. No prompt here — we have not proven the user left THEIR car, only that something
            // is driving, and asking on that would be noise.
            PaparcarLogger.w(DIAG, "⊘ arm denied by the OS (${e.message}) — the safety net stays the backstop")
        }
        return Result.success()
    }

    companion object {
        const val TAG = "DeclinedBoardingRelookWorker"
        private const val DIAG = "PARKDIAG/Relook"
        private const val KEY_GEOFENCE_ID = "geofence_id"

        /**
         * How long after the declined boarding to look once.
         *
         * Chosen from the field trace, not from taste: at the ENTER the phone read 0.22 m/s, and the
         * drive was unmistakable well before the safety net finally noticed at +6 min 51 s. 90 s is
         * long enough for a car that just pulled away to be at credible driving speed, and short
         * enough that arming still buys most of the trip.
         */
        private const val RELOOK_DELAY_SECONDS = 90L

        fun enqueue(context: Context, geofenceId: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${TAG}_$geofenceId",
                ExistingWorkPolicy.REPLACE,
                buildRequest(geofenceId),
            )
        }

        fun buildRequest(geofenceId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DeclinedBoardingRelookWorker>()
                .setInputData(workDataOf(KEY_GEOFENCE_ID to geofenceId))
                .setInitialDelay(RELOOK_DELAY_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
    }
}
