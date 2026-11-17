package com.rndeveloper.paparcar.detection.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.notification.FuelMoveLane
import com.rndeveloper.paparcar.domain.usecase.fuel.CheaperReference
import com.rndeveloper.paparcar.domain.usecase.fuel.FuelPriceMove
import com.rndeveloper.paparcar.domain.usecase.fuel.FuelWatchOutcome
import com.rndeveloper.paparcar.domain.usecase.fuel.PriceMoveDirection
import com.rndeveloper.paparcar.domain.usecase.fuel.RunFuelPriceWatchUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.pumpGallonForCurrency
import com.rndeveloper.paparcar.presentation.home.model.pumpPriceLabel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The daily look at the prices this driver has a stake in.
 * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * ⛔ **Daily, not every fifteen minutes.** A pump changes price about once a day, and the zone-alert
 * cadence would spend battery and a shared Android 16 job quota re-reading a number that has not
 * moved. The cost of being late here is a saving noticed in the evening instead of the morning; the
 * cost of being late on a free spot is the spot [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]. Two
 * different questions, two different clocks.
 *
 * Deciding nothing: [RunFuelPriceWatchUseCase] gates, records and asks the two verdicts. This class
 * turns a verdict into words — which is a locale decision, and therefore a platform one.
 */
class FuelPriceWatchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val runWatch: RunFuelPriceWatchUseCase by inject()
    private val notificationPort: AppNotificationManager by inject()

    override suspend fun doWork(): Result = when (val outcome = runWatch()) {
        is FuelWatchOutcome.Skipped -> {
            PaparcarLogger.d(TAG) { "· skipped (${outcome.reason})" }
            Result.success()
        }

        // ⛔ Retry, not failure: no fix or a feed that did not answer is a look we did not get, and
        // the series must not grow a hole we then read as "the price stayed put".
        is FuelWatchOutcome.Unavailable -> {
            PaparcarLogger.d(TAG) { "· could not look (${outcome.reason}) — retrying" }
            Result.retry()
        }

        is FuelWatchOutcome.Looked -> {
            // Un aviso por carril, y los dos pueden salir el mismo día: hablan de cosas distintas.
            // [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
            notifyMoves(outcome.followedMoves, FuelMoveLane.FOLLOWED)
            notifyMoves(outcome.nearbyMoves, FuelMoveLane.NEARBY)
            notifyCheaper(outcome)
            Result.success()
        }
    }

    private fun notifyMoves(moves: List<FuelPriceMove>, lane: FuelMoveLane) {
        // The biggest mover leads; the rest are counted, never stacked into their own cards.
        val lead = moves.maxByOrNull { abs(it.deltaPerUnit) } ?: return
        notificationPort.showFuelPriceMove(
            stationName = lead.station.name,
            deltaLabel = Money(abs(lead.deltaPerUnit), lead.current.perUnit.currency).pumpPriceLabel(),
            wentUp = lead.direction == PriceMoveDirection.UP,
            otherMovesCount = moves.size - 1,
            latitude = lead.station.latitude,
            longitude = lead.station.longitude,
            // La unidad la dice la MONEDA del precio, no la región del móvil: la frase tiene que
            // decir lo mismo que la cifra. [FUEL-A-PRICE-IS-QUOTED-WHERE-IT-IS-SOLD-001]
            quotesPerGallon = pumpGallonForCurrency(lead.current.perUnit.currency) != null,
            lane = lane,
        )
    }

    private fun notifyCheaper(outcome: FuelWatchOutcome.Looked) {
        val cheaper = outcome.cheaper ?: return
        notificationPort.showCheaperFuelNearby(
            stationName = cheaper.station.name,
            savingLabel = Money(cheaper.savingPerUnit, cheaper.price.currency).pumpPriceLabel(),
            // Null when the reference was the area's median — the words for that live where the
            // string table does. A followed station with no name in its feed reads the same way,
            // which is right: neither can be quoted by name.
            referenceName = cheaper.referenceName.takeIf { cheaper.reference == CheaperReference.FOLLOWED },
            latitude = cheaper.station.latitude,
            longitude = cheaper.station.longitude,
            quotesPerGallon = pumpGallonForCurrency(cheaper.price.currency) != null,
        )
    }

    companion object {
        const val TAG = "FuelPriceWatchWorker"
        private const val INTERVAL_HOURS = 24L

        // [BATT-006] GetNearbyFuelStationsUseCase hits the fuel-price feeds (Minetur/MTS-K/Italy/
        // Germany/Overpass) — without this, Doze/OEM scheduling still fires the worker with no
        // network, and the run is wasted CPU + wakeup for a result that was always going to be
        // Unavailable.
        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<FuelPriceWatchWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(TAG)
                .build()

        /**
         * Installs the daily periodic, or removes it when there is nothing to watch.
         *
         * ⛔ **Scheduling is not gating.** The Plus check lives inside the use case and is read live,
         * so a user who lapses stops being notified on the very next run whatever this said. This
         * only decides whether a clock exists at all — a periodic that wakes daily to discover there
         * is nothing followed spends the shared background quota Android 16 meters per app.
         */
        fun syncSchedule(workManager: WorkManager, hasSomethingToWatch: Boolean) {
            if (hasSomethingToWatch) {
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
