@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.zone

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.repository.ZoneWatchRepository
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.zone.zoneSpotsWorthTelling
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/** What one background look produced. [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001] */
sealed class ZoneWatchOutcome {
    /** Nothing to do: not Plus, the switch is off, or no zone asks to be watched. */
    data class Skipped(val reason: String) : ZoneWatchOutcome()

    /** Looked. An empty list is a real answer — it means nothing new appeared. */
    data class Looked(val alerts: List<ZoneSpotAlert>, val zonesLooked: Int) : ZoneWatchOutcome()

    /** Could not look. Nothing recorded, so no zone is left marked as seen without being seen. */
    data class Unavailable(val reason: String) : ZoneWatchOutcome()
}

/**
 * One background look at every subscribed zone. [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]
 *
 * The other half of [ObserveZoneSpotAlertsUseCase]: the same question asked from a worker instead of
 * from a living process, deciding with the same pure rule and remembering in the same table. What it
 * exists for is the case the stream structurally cannot cover — the app is closed, which is exactly
 * when an alert is worth anything at all.
 *
 * ⛔ **The Plus gate is read HERE and live**, not at scheduling time. A lapsed subscription stops
 * the alerts on the very next run whatever the scheduler decided, and — the rule that matters on
 * the way down — **nothing is deleted**: not a zone's `notifyOnSpot`, not a recorded alert. Regaining
 * Plus resumes the same watch rather than a fresh one that re-announces the street. [MONEY-001]
 */
class RunZoneSpotWatchUseCase(
    private val zoneRepository: ZoneRepository,
    private val spotRepository: SpotRepository,
    private val watchRepository: ZoneWatchRepository,
    private val observeIsPlus: ObserveIsPlusUseCase,
    private val appPreferences: AppPreferences,
    private val authRepository: AuthRepository,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    suspend operator fun invoke(): ZoneWatchOutcome {
        if (!observeIsPlus().first()) return ZoneWatchOutcome.Skipped("not-plus")
        if (!appPreferences.notifyZoneSpots) return ZoneWatchOutcome.Skipped("switch-off")

        val subscribed = zoneRepository.observeZones().first().filter { it.notifyOnSpot }
        if (subscribed.isEmpty()) return ZoneWatchOutcome.Skipped("no-subscribed-zones")

        val now = clock()
        watchRepository.prune(now)
        val ownUserId = authRepository.getCurrentSession()?.userId

        val alerts = mutableListOf<ZoneSpotAlert>()
        var looked = 0
        subscribed.forEach { zone ->
            val centre = GpsPoint(
                latitude = zone.lat,
                longitude = zone.lon,
                accuracy = 0f,
                timestamp = 0L,
                speed = 0f,
            )
            // ⛔ ONE emission, not a collect: a worker that subscribes to a live query never
            // returns, and WorkManager would kill it mid-look.
            val spots = runCatching {
                spotRepository.observeNearbySpots(centre, zone.radiusMeters.toDouble()).first()
            }.getOrElse { e ->
                // One zone's failing query must not cost the others their look — the same stream
                // isolation the live lane already practises, applied per zone instead of per merge.
                PaparcarLogger.w(TAG, "zone ${zone.id} could not be looked at", e)
                return@forEach
            }
            looked++

            val look = zoneSpotsWorthTelling(
                zone = zone,
                spots = spots,
                ownUserId = ownUserId,
                alreadyAlerted = watchRepository.alertedSpotIds(zone.id),
                hasBaseline = watchRepository.hasBaseline(zone.id),
                nowMs = now,
            )
            watchRepository.recordLook(
                zoneId = zone.id,
                spots = look.toRecord,
                nowMs = now,
                markBaseline = look.takingBaseline,
            )
            alerts += look.alertable.map { spot ->
                ZoneSpotAlert(zoneId = zone.id, zoneName = zone.name, spot = spot)
            }
        }

        return if (looked == 0) {
            ZoneWatchOutcome.Unavailable("no-zone-answered")
        } else {
            ZoneWatchOutcome.Looked(alerts = alerts, zonesLooked = looked)
        }
    }

    private companion object {
        const val TAG = "ZoneSpotWatch"
    }
}
