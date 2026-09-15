package com.rndeveloper.paparcar.detection.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.usecase.stats.MonthRecapOutcome
import com.rndeveloper.paparcar.domain.usecase.stats.RunMonthRecapUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * The daily knock on the door during the first days of a new month.
 * [STATS-PLUS-THE-RECAP-KNOCKS-001]
 *
 * ⛔ **Daily, and the use case decides.** A periodic that fired monthly would be a clock this app
 * cannot keep — WorkManager guarantees no exact instant, a phone off for a day would miss the only
 * window, and "was it told already" would still have to be answered somewhere. So the worker ticks
 * every day, cheaply, and `RunMonthRecapUseCase` answers whether this tick is the one: the same
 * split `FuelPriceWatchWorker` uses. Seven possible ticks and one notification, because the month is
 * marked as told before the words are built.
 *
 * Deciding nothing here also keeps the locale where it belongs: this class turns a verdict into
 * words, and words are a platform decision.
 */
class MonthRecapWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val runRecap: RunMonthRecapUseCase by inject()
    private val notificationPort: AppNotificationManager by inject()

    override suspend fun doWork(): Result = when (val outcome = runRecap()) {
        is MonthRecapOutcome.Skipped -> {
            PaparcarLogger.d(TAG) { "· no recap (${outcome.reason})" }
            Result.success()
        }

        is MonthRecapOutcome.Tell -> {
            val stats = outcome.stats
            notificationPort.showMonthRecap(
                parkings = stats.parkings,
                // Money is formatted here for the same reason the words are: the decimal separator
                // is the reader's, not the domain's.
                spendText = stats.costEstimate?.let { "${formatMoney(it.amount)} ${it.currency}" },
                searchTimeMs = stats.searchTime?.value,
            )
            PaparcarLogger.d(TAG) { "▶ told the closed month (${stats.parkings} parkings)" }
            Result.success()
        }
    }

    private fun formatMoney(value: Double): String {
        val cents = (value * CENTS).roundToInt()
        return "${cents / CENTS.toInt()}.${(cents % CENTS.toInt()).toString().padStart(2, '0')}"
    }

    companion object {
        const val TAG = "MonthRecapWorker"
        private const val INTERVAL_HOURS = 24L
        private const val CENTS = 100.0

        /**
         * Installs the daily tick. Unique and KEEP, so app starts do not reset its clock — the
         * window is seven days wide and losing a day to a re-enqueue on every launch would make the
         * recap depend on how often the app is opened, which is the very thing it exists to avoid.
         */
        fun schedule(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MonthRecapWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                    .addTag(TAG)
                    .build(),
            )
        }
    }
}
