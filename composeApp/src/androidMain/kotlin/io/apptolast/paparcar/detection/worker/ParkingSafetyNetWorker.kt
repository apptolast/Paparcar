@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.apptolast.paparcar.detection.SignificantMotionMonitor
import io.apptolast.paparcar.domain.detection.DetectionRuntimeState
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.EvaluateSafetyNetCheckUseCase
import io.apptolast.paparcar.domain.usecase.parking.SafetyNetAction
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The parked-session safety net: the one departure guarantee that does not depend on Play
 * Services delivering anything. [DET-SAFETY-NET-001]
 *
 * Runs every 15 min while the app is installed (WorkManager survives process death and OEM kills
 * of the app process) and on demand from the significant-motion hardware trigger
 * ([SignificantMotionMonitor] → [enqueueCheckNow]). When a session is parked and detection is
 * idle it samples ONE active balanced-priority fix and lets the pure evaluator decide
 * ([EvaluateSafetyNetCheckUseCase]):
 *
 *  - **The fix itself is half the job.** Play Services' geofencing engine only updates its
 *    INSIDE/OUTSIDE state when a fix reaches the fused provider; with the phone still and no app
 *    requesting location it starves for tens of minutes. Every tick feeds it.
 *  - **Inside the fence → cure.** Re-register the geofence (idempotent, `FLAG_UPDATE_CURRENT`,
 *    no initial trigger) so a state poisoned OUTSIDE by a false walking-EXIT is rebuilt INSIDE —
 *    without this, the next real drive-away produces no EXIT transition at all (field incident
 *    2026-07-04, Calle Gavia).
 *  - **Far + vehicle evidence → dispatch** the normal [DepartureDetectionWorker] pipeline (its
 *    verdict/retry/publish/clear machinery is the single brain for departures).
 *  - **Far, no evidence → prompt.** Distance alone NEVER releases (walked / bus / friend's car —
 *    BUG-WALK-DEPART-001); the "still parked?" notification lets the user disambiguate, throttled
 *    so it cannot nag.
 *
 * Each tick also mirrors the parked-idle state onto the significant-motion trigger, which is the
 * immediacy layer of the same net (the sensor listener dies with the process; this worker is what
 * resurrects it). Replaces the old `DetectionHeartbeatWorker` watchdog, whose passive-fix
 * isolation predates DET-SOLID-001 — false EXITs are now dismissed cleanly by the worker chain,
 * so provoking the geofence engine with an active fix is exactly what we want.
 */
class ParkingSafetyNetWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val userParkingRepository: UserParkingRepository by inject()
    private val foregroundNotificationProvider: ForegroundNotificationProvider by inject()
    private val getOneLocation: GetOneLocationUseCase by inject()
    private val notificationPort: AppNotificationManager by inject()
    private val detectionRuntime: DetectionRuntimeState by inject()
    private val departureEventBus: DepartureEventBus by inject()
    private val evaluateSafetyNetCheck: EvaluateSafetyNetCheckUseCase by inject()
    private val geofenceManager: GeofenceManager by inject()
    private val significantMotionMonitor: SignificantMotionMonitor by inject()
    private val detectionEventLogger: DetectionEventLogger by inject()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            AppNotificationManager.DETECTION_NOTIFICATION_ID,
            foregroundNotificationProvider.buildDetectionNotification(),
        )

    override suspend fun doWork(): Result {
        val sessions = runCatching { userParkingRepository.observeActiveSessions().firstOrNull().orEmpty() }
            .getOrElse {
                PaparcarLogger.e(TAG, "✗ failed to read active sessions", it)
                return Result.success()
            }

        // [DET-SIGMOTION-001] Mirror the parked-idle state onto the hardware trigger on EVERY
        // tick — this is also what re-arms it after a process kill or after it fired one-shot.
        val parkedAndIdle = sessions.isNotEmpty() && !detectionRuntime.isRunning.value
        runCatching { significantMotionMonitor.sync(shouldBeArmed = parkedAndIdle) }

        if (sessions.isEmpty()) {
            dismissPrompt()
            return Result.success()
        }
        // Mid-trip: a live coordinator session owns the situation; a repark also self-heals the
        // old session (replaceActiveSession per vehicle). Don't second-guess it.
        if (detectionRuntime.isRunning.value) {
            PaparcarLogger.d(TAG, "■ detection running — skipping check")
            return Result.success()
        }
        if (!hasLocationPermission()) {
            PaparcarLogger.w(TAG, "■ no location permission — skipping check")
            return Result.success()
        }

        // ACTIVE fix on purpose (not passive last-known): feeding the fused provider is what keeps
        // the geofencing engine's state machine alive while the phone sits in Doze.
        val fix = runCatching { getOneLocation() }.getOrNull()
        if (fix == null) {
            PaparcarLogger.d(TAG, "■ no fix within timeout — nothing to evaluate this tick")
            return Result.success()
        }

        val now = System.currentTimeMillis()
        var anyPromptActive = false

        // Drop anchors of geofences that no longer have an active session (departed / reverted).
        val liveGeofenceIds = sessions.mapNotNullTo(mutableSetOf()) { it.geofenceId }
        lastSeenNearCarAtMs.keys.retainAll(liveGeofenceIds)

        for (session in sessions) {
            val action = evaluateSafetyNetCheck(
                session = session,
                fix = fix,
                lastVehicleEnteredAt = departureEventBus.lastVehicleEnteredAt,
                lastSeenNearCarAtMs = session.geofenceId?.let { lastSeenNearCarAtMs[it] },
                nowMs = now,
            )
            when (action) {
                is SafetyNetAction.CureGeofence -> {
                    // Position anchor: the phone is provably AT the car right now — this is what
                    // authorises a later far+evidence auto-dispatch (movement started at the car).
                    lastSeenNearCarAtMs[action.geofenceId] = now
                    PaparcarLogger.d(TAG, "▶ inside fence — re-registering geofence=${action.geofenceId} (cure)")
                    val result = geofenceManager.createGeofence(
                        geofenceId = action.geofenceId,
                        latitude = session.location.latitude,
                        longitude = session.location.longitude,
                        radiusMeters = action.radiusMeters,
                    )
                    runCatching {
                        detectionEventLogger.log(
                            DetectionEvent.GeofenceRegistration(
                                sessionId = action.geofenceId,
                                timestampMs = now,
                                success = result.isSuccess,
                                radiusMeters = action.radiusMeters,
                                location = fix,
                            )
                        )
                    }
                }

                is SafetyNetAction.DispatchDeparture -> {
                    PaparcarLogger.d(TAG, "▶ far with vehicle evidence — dispatching departure geofence=${action.geofenceId}")
                    WorkManager.getInstance(appContext).enqueueUniqueWork(
                        "${DepartureDetectionWorker.TAG}_${action.geofenceId}",
                        ExistingWorkPolicy.REPLACE,
                        DepartureDetectionWorker.buildRequest(geofenceId = action.geofenceId, exitTimestampMs = now),
                    )
                    logVerdict(action.geofenceId, verdict = "safety_net_dispatch", fixSpeedKmh = fix.speed * KMH_PER_MPS, now = now)
                }

                is SafetyNetAction.PromptStillParked -> {
                    anyPromptActive = true
                    val lastPromptAt = lastPromptAtMs[action.geofenceId] ?: 0L
                    if (now - lastPromptAt >= PROMPT_THROTTLE_MS) {
                        PaparcarLogger.d(TAG, "▶ far without evidence — still-parked prompt geofence=${action.geofenceId}")
                        lastPromptAtMs[action.geofenceId] = now
                        notificationPort.showStillParkedPrompt(
                            geofenceId = action.geofenceId,
                            latitude = session.location.latitude,
                            longitude = session.location.longitude,
                        )
                        logVerdict(action.geofenceId, verdict = "safety_net_prompt", fixSpeedKmh = fix.speed * KMH_PER_MPS, now = now)
                    }
                }

                SafetyNetAction.None -> Unit
            }
        }

        // Back near the car (or ambiguity resolved) → any lingering prompt is stale.
        if (!anyPromptActive) dismissPrompt()

        return Result.success()
    }

    private suspend fun logVerdict(geofenceId: String, verdict: String, fixSpeedKmh: Float, now: Long) {
        runCatching {
            detectionEventLogger.log(
                DetectionEvent.DepartureVerdict(
                    sessionId = geofenceId,
                    timestampMs = now,
                    verdict = verdict,
                    source = "safety-net",
                    speedKmh = fixSpeedKmh,
                    enterAgeMs = departureEventBus.lastVehicleEnteredAt?.let { now - it },
                )
            )
        }
    }

    private fun dismissPrompt() =
        notificationPort.dismiss(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID)

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val TAG = "ParkingSafetyNetWorker"
        /** Unique-work name of the pre-[DET-SAFETY-NET-001] watchdog — cancelled on enqueue so the
         *  renamed class never leaves a stale periodic pointing at a missing worker. */
        private const val LEGACY_TAG = "DetectionHeartbeatWorker"
        private const val INTERVAL_MINUTES = 15L
        private const val KMH_PER_MPS = 3.6f

        /** Min interval between "still parked?" prompts per geofence. In-memory (process-lifetime):
         *  a process kill may allow one early re-prompt hours later — acceptable, and the throttle
         *  never suppresses the FIRST prompt after a missed departure. */
        private const val PROMPT_THROTTLE_MS = 6 * 60 * 60 * 1_000L
        private val lastPromptAtMs = ConcurrentHashMap<String, Long>()

        /** Position anchor per geofence: when a safety-net fix last landed INSIDE that fence.
         *  In-memory on purpose — losing it on process death degrades far+evidence to the human
         *  prompt, never to a phantom auto-release (fail-safe direction). */
        private val lastSeenNearCarAtMs = ConcurrentHashMap<String, Long>()

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<ParkingSafetyNetWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()

        fun enqueueKeep(workManager: WorkManager) {
            workManager.cancelUniqueWork(LEGACY_TAG)
            workManager.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                buildPeriodicRequest(),
            )
        }

        /**
         * Immediate one-shot check, distinct from the 15-min periodic: enqueued by the
         * significant-motion trigger [DET-SIGMOTION-001] and by detection teardown (so the sensor
         * is re-armed seconds after a park instead of waiting for the next periodic tick).
         * Expedited where quota allows — a sensor callback cannot legally start an FGS on
         * Android 12+, and expedited work is the sanctioned fast lane.
         */
        fun enqueueCheckNow(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                "${TAG}_now",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ParkingSafetyNetWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(TAG)
                    .build(),
            )
        }
    }
}
