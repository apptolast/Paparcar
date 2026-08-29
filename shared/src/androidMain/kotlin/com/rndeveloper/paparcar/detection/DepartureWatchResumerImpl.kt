package com.rndeveloper.paparcar.detection

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.rndeveloper.paparcar.detection.service.CoordinatorDetectionService
import com.rndeveloper.paparcar.domain.detection.ports.DepartureWatchResumer
import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDepartureWatchGapUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Starts the Coordinator service with [CoordinatorDetectionService.ACTION_RESUME_SENTRY]: the action
 * carries no work of its own, so the service's idle epilogue makes the final sentry-vs-stop call.
 * Callers are foreground by construction (visible Activity / user tap), which is what makes the
 * foreground-service start legal on Android 12+. [DET-WATCH-REACTIVATE-001]
 *
 * The guard below asks [ObserveDepartureWatchGapUseCase] — the same stream that wakes the automatic
 * lane — instead of re-reading sessions, strategy and preferences on its own. Re-deriving the verdict
 * is how this class used to decline a gap the stream had just reported: a `first()` on a Room flow
 * that had not delivered yet answered "nothing parked", and since the gap boolean never changed,
 * nothing retried for the rest of the app session. One question, one answerer.
 * [DET-WATCH-RESUME-RACE-001]
 */
class DepartureWatchResumerImpl(
    private val context: Context,
    private val observeDepartureWatchGap: ObserveDepartureWatchGapUseCase,
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

        // null = the gap could not be read in time (a source that never delivered). That is NOT the
        // same as "there is nothing to watch", and the log must not blur the two.
        val hasGap = withTimeoutOrNull(GATE_READ_TIMEOUT_MS) {
            runCatching { observeDepartureWatchGap.current() }
                .onFailure { PaparcarLogger.w(TAG, "resume($source) — watch state unreadable", it) }
                .getOrNull()
        }

        when {
            hasGap == true -> Unit
            hasGap == false -> {
                PaparcarLogger.d(TAG, "resume($source) declined — no watch gap to close")
                return false
            }
            // Unreadable state. An explicit tap still fires: the service epilogue re-decides with the
            // same pure rule and stops itself if there is nothing to watch, so the worst case is a
            // service that shuts down on its own — far better than a button that does nothing. An
            // automatic attempt stays put, because a silent FGS nobody asked for is the wrong bet.
            force -> PaparcarLogger.w(TAG, "resume($source) — state unknown, dispatching anyway (explicit)")
            else -> {
                PaparcarLogger.w(TAG, "resume($source) declined — state unknown")
                return false
            }
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

        /** Quiet period between AUTOMATIC attempts. The gate above is the same stream the service
         *  epilogue agrees with, so a start that immediately stops itself should be impossible; this
         *  is the backstop that keeps such a disagreement from becoming a resume loop. */
        const val AUTOMATIC_RETRY_COOLDOWN_MS = 60_000L

        /** How long the gate may take to produce its first real reading. Generous enough for Room to
         *  open on a cold start, short enough that a CTA tap never leaves the user without feedback. */
        const val GATE_READ_TIMEOUT_MS = 3_000L
    }
}
