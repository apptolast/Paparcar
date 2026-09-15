package com.rndeveloper.paparcar.detection.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.usecase.zone.RunZoneSpotWatchUseCase
import com.rndeveloper.paparcar.domain.usecase.zone.ZoneWatchOutcome
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * The background half of the Plus zone alert.
 * [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]
 *
 * Fifteen minutes is WorkManager's floor, and the TTL measured in [SpotTtlPolicy] is what makes it
 * worth using: an automatic spot lives **two hours**, so the worst case is a free space announced
 * fifteen minutes late with an hour and three quarters still on its clock. A PROVISIONAL spot lives
 * twelve minutes and is therefore excluded at the policy — this lane cannot reach it, and saying so
 * is better than a promise that structurally cannot be kept.
 *
 * ⚠️ Without a battery exemption, Doze stretches that floor a long way at night. The exemption is
 * already asked for by detection and is NOT asked for again here.
 *
 * Decides nothing: [RunZoneSpotWatchUseCase] gates, looks and remembers.
 */
class ZoneSpotWatchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val runWatch: RunZoneSpotWatchUseCase by inject()
    private val notificationPort: AppNotificationManager by inject()

    override suspend fun doWork(): Result = when (val outcome = runWatch()) {
        is ZoneWatchOutcome.Skipped -> {
            PaparcarLogger.d(TAG) { "· skipped (${outcome.reason})" }
            Result.success()
        }

        // Retry, not failure: a look we did not get must not leave a zone marked as seen.
        is ZoneWatchOutcome.Unavailable -> {
            PaparcarLogger.d(TAG) { "· could not look (${outcome.reason}) — retrying" }
            Result.retry()
        }

        is ZoneWatchOutcome.Looked -> {
            // ⛔ ONE notification, the nearest-to-newest first. Three spots appearing in one
            // quarter of an hour must not become three cards: the id is shared, so a second alert
            // REPLACES the first rather than stacking on it — the same choke point and the same
            // notification id the live lane already uses, so a spot announced by the stream and
            // then found again by this worker cannot be announced twice.
            outcome.alerts.firstOrNull()?.let { alert ->
                notificationPort.showZoneSpotAlert(
                    zoneName = alert.zoneName,
                    latitude = alert.spot.location.latitude,
                    longitude = alert.spot.location.longitude,
                )
            }
            PaparcarLogger.d(TAG) {
                "▶ looked at ${outcome.zonesLooked} zone(s), ${outcome.alerts.size} new spot(s)"
            }
            Result.success()
        }
    }

    companion object {
        const val TAG = "ZoneSpotWatchWorker"

        /** WorkManager's own floor. A shorter number is not honoured, it is silently raised. */
        private const val INTERVAL_MINUTES = 15L

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<ZoneSpotWatchWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()

        /**
         * Installs the periodic while some zone asks to be watched, and removes it when none does.
         *
         * ⛔ Scheduling is not gating. Whether the user holds Plus is read live inside the use case
         * at every run; this only decides whether a clock exists at all, because a job that wakes
         * four times an hour to find nothing subscribed spends the shared background quota Android
         * 16 meters per app. ⚠️ It deliberately does NOT read the entitlement: a scheduler that
         * cancelled on downgrade would be a second place deciding Plus, and the two would drift.
         */
        fun syncSchedule(workManager: WorkManager, hasSubscribedZones: Boolean) {
            if (hasSubscribedZones) {
                workManager.enqueueUniquePeriodicWork(
                    TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    buildPeriodicRequest(),
                )
            } else {
                workManager.cancelUniqueWork(TAG)
            }
        }
    }
}
