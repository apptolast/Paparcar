package io.apptolast.paparcar.detection

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.apptolast.paparcar.detection.service.CoordinatorDetectionService
import io.apptolast.paparcar.domain.detection.DepartureWatchResumer
import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.detection.PostDetectionLifecycle
import io.apptolast.paparcar.domain.detection.resolvePostDetectionLifecycle
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.first

/**
 * Starts the Coordinator service with [CoordinatorDetectionService.ACTION_RESUME_SENTRY]: the action
 * carries no work of its own, so the service's idle epilogue makes the final sentry-vs-stop call.
 * Callers are foreground by construction (visible Activity / user tap), which is what makes the
 * foreground-service start legal on Android 12+. [DET-WATCH-REACTIVATE-001]
 */
class DepartureWatchResumerImpl(
    private val context: Context,
    private val userParkingRepository: UserParkingRepository,
    private val strategyResolver: ParkingStrategyResolver,
    private val appPreferences: AppPreferences,
) : DepartureWatchResumer {

    /** Elapsed-time stamp of the last AUTOMATIC attempt; 0 = never. Only automatic callers are
     *  throttled — an explicit tap always tries. */
    private var lastAutomaticAttemptElapsedMs = 0L

    override suspend fun resume(source: String, force: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (!force &&
            lastAutomaticAttemptElapsedMs != 0L &&
            now - lastAutomaticAttemptElapsedMs < AUTOMATIC_RETRY_COOLDOWN_MS
        ) {
            PaparcarLogger.d(TAG, "resume($source) skipped — automatic retry cooling down")
            return false
        }

        // Same gate as the service's own epilogue, read fresh: nothing parked / detection off /
        // Bluetooth owns the car → there is no watcher to rebuild, and starting one would only flash
        // a foreground notification with no purpose.
        val hasParkedSession = runCatching {
            userParkingRepository.observeActiveSessions().first().isNotEmpty()
        }.getOrDefault(false)
        val strategy = runCatching { strategyResolver.resolve() }.getOrDefault(ParkingStrategy.COORDINATOR)
        val lifecycle = resolvePostDetectionLifecycle(
            autoDetectEnabled = appPreferences.autoDetectParking,
            hasParkedSession = hasParkedSession,
            strategy = strategy,
        )
        if (lifecycle != PostDetectionLifecycle.EnterSentry) {
            PaparcarLogger.d(TAG, "resume($source) declined — parked=$hasParkedSession strategy=$strategy")
            return false
        }

        if (!force) lastAutomaticAttemptElapsedMs = now
        return runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CoordinatorDetectionService::class.java)
                    .setAction(CoordinatorDetectionService.ACTION_RESUME_SENTRY)
                    // Who asked travels with the intent so the service log names the sender.
                    .putExtra(CoordinatorDetectionService.EXTRA_RESUME_SOURCE, source),
            )
        }.onSuccess {
            PaparcarLogger.d(TAG, "resume($source) → RESUME_SENTRY dispatched")
        }.onFailure {
            // The process was not foreground-eligible after all (Android 12+ blocks the start). The
            // safety-net worker and the significant-motion trigger still cover the session, so this is
            // a degraded watch, not a lost one — and the caller turns it into honest feedback instead
            // of a mute button.
            PaparcarLogger.w(TAG, "resume($source) refused by the OS: ${it.message}")
        }.isSuccess
    }

    private companion object {
        const val TAG = "DepartureWatch"

        /** Quiet period between AUTOMATIC attempts. The gate above matches the service's epilogue, so
         *  a start that immediately stops itself should be impossible; this is the backstop that keeps
         *  such a disagreement from becoming a resume loop. */
        const val AUTOMATIC_RETRY_COOLDOWN_MS = 60_000L
    }
}
