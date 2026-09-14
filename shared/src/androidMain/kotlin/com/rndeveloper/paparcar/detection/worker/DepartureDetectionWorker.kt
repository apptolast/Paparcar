@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.detection.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rndeveloper.paparcar.detection.service.CoordinatorDetectionService
import com.rndeveloper.paparcar.domain.usecase.parking.DepartureCheckOutcome
import com.rndeveloper.paparcar.domain.usecase.parking.RunDepartureCheckUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Decides whether a geofence-exit represents a real car departure and, if confirmed,
 * processes all side-effects.
 *
 * Pure translation layer: input parsing + [DepartureCheckOutcome] → WorkManager [Result].
 * The whole decide → retry-policy → fallthrough-guard → live-session-upgrade → process
 * sequence lives in [RunDepartureCheckUseCase] (domain, commonTest-covered). [DET-SOLID-001]
 */
class DepartureDetectionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val runDepartureCheck: RunDepartureCheckUseCase by inject()

    override suspend fun doWork(): Result {
        val geofenceId = inputData.getString(KEY_GEOFENCE_ID)
            ?: return Result.success()

        // When this trigger fired. A missing key legitimately reads as "now": the trigger IS firing
        // now, and this value only anchors the independence gate.
        val triggerAtMs = inputData.getLong(KEY_TRIGGER_AT, -1L)
            .takeIf { it > 0L } ?: Clock.System.now().toEpochMilliseconds()
        // [DET-AN-EXIT-IS-DATED-BY-THE-FIX-THAT-CROSSED-IT-001] When the car was MEASURED to leave.
        // ⛔ A missing key reads as NULL, never as `now`. This line used to be the same `?: now` as
        // the one above, which is how "we do not know the hour" became "it just happened" — and a
        // plaza freed hours ago got advertised as fresh. `null` is carried to the freshness gate,
        // which already reads it as "an unknown hour is not a recent hour" and declines to publish.
        val departedAtMs = inputData.getLong(KEY_DEPARTED_AT, -1L).takeIf { it > 0L }
        val preconfirmed = inputData.getBoolean(KEY_PRECONFIRMED, false)

        return when (
            val outcome = runDepartureCheck(
                geofenceId,
                triggerAtMs = triggerAtMs,
                departedAtMs = departedAtMs,
                attempt = runAttemptCount,
                preconfirmed = preconfirmed,
            )
        ) {
            DepartureCheckOutcome.Retry,
            DepartureCheckOutcome.ProcessFailedRetry -> Result.retry()
            DepartureCheckOutcome.Dismissed -> {
                // A delivered EXIT judged false (walking/GPS drift) leaves Play Services' fence
                // state OUTSIDE — the fence is still registered and the next real drive-away would
                // be silent. Say so: the cure's next tick inside repairs it immediately, rebuilding
                // the state from a fresh fix. [DET-ANCHOR-FREEZE-001 F4]
                // [DET-FENCE-REREGISTER-BY-CAUSE-001 §B] This used to DELETE the cure's throttle
                // key to say the same thing, which made a cause indistinguishable from "never cured"
                // and from "freshly installed". Now it stamps the cause itself.
                ParkingSafetyNetWorker.markFenceStatePoisoned(applicationContext, geofenceId)
                Result.success()
            }
            is DepartureCheckOutcome.Processed -> {
                // [DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001] The check measured the departure
                // and nobody is following the trip: arm the follower through the service's own door.
                // Best-effort like the re-look's arm — a background FGS-start may be denied
                // (Android 12+/OEM), and nothing is lost that was not already lost before this
                // ticket: the safety net stays the backstop it has always been.
                outcome.followTrip?.let { handoff ->
                    runCatching {
                        CoordinatorDetectionService.startDepartureFollowerArm(
                            applicationContext,
                            geofenceId = handoff.geofenceId,
                            speedKmh = handoff.speedKmh,
                            accuracyM = handoff.accuracyM,
                        )
                    }.onFailure { e ->
                        PaparcarLogger.w(DIAG, "⊘ follower arm denied by the OS (${e.message}) — the safety net stays the backstop")
                    }
                }
                Result.success()
            }
        }
    }

    companion object {
        const val TAG = "DepartureDetectionWorker"
        private const val DIAG = "PARKDIAG/Depart"
        private const val KEY_GEOFENCE_ID = "geofence_id"
        /** When the trigger FIRED (our scheduler's clock) — anchors the independence gate. */
        private const val KEY_TRIGGER_AT = "exit_timestamp_ms"
        /** [DET-AN-EXIT-IS-DATED-BY-THE-FIX-THAT-CROSSED-IT-001] When the car was MEASURED to leave,
         *  or [NO_MEASURED_DEPARTURE] when nothing measured it. Distinct from [KEY_TRIGGER_AT]
         *  because they answer different questions and coincide only while triggers arrive on time. */
        private const val KEY_DEPARTED_AT = "departed_at_ms"
        /** Departure already proven by the parked-state reconcile (step budget) — the check must
         *  not re-decide by live speed, only process. [DET-RECONCILE-001] */
        private const val KEY_PRECONFIRMED = "preconfirmed"

        /** Sentinel for "no measured departure instant": `workData` has no nullable Long, and a
         *  key simply left out would read back as the `getLong` default — the same silent
         *  "absent means fine" that this ticket exists to kill. Written explicitly, read back as
         *  null by `takeIf { it > 0L }`. Same trick as `ReportSpotWorker`'s NaN for floats. */
        private const val NO_MEASURED_DEPARTURE = -1L

        /** Backoff for [DepartureCheckOutcome.Retry] — with EXPONENTIAL 15s the inconclusive
         *  retries fire at ~15s/30s/60s (see [RunDepartureCheckUseCase.MAX_INCONCLUSIVE_ATTEMPTS]). */
        private const val INITIAL_BACKOFF_SECONDS = 15L

        /**
         * @param triggerAtMs  when this trigger fired — `System.currentTimeMillis()` at the intake.
         * @param departedAtMs the MEASURED instant the car left, or null. Callers derive it through
         *   [com.rndeveloper.paparcar.domain.detection.measuredDepartureAtMs]. **No default**: every
         *   dispatch site must answer, because the one that did not is the one that published
         *   ghosts. [DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001]
         */
        fun buildRequest(
            geofenceId: String,
            triggerAtMs: Long,
            departedAtMs: Long?,
            preconfirmed: Boolean = false,
        ): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DepartureDetectionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_GEOFENCE_ID to geofenceId,
                        KEY_TRIGGER_AT to triggerAtMs,
                        KEY_DEPARTED_AT to (departedAtMs ?: NO_MEASURED_DEPARTURE),
                        KEY_PRECONFIRMED to preconfirmed,
                    )
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
    }
}
