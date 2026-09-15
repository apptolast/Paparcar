@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.zone

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.repository.ZoneWatchRepository
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.zone.zoneSpotsWorthTelling
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform
import kotlin.time.Clock

/** A free community spot just appeared inside one of the user's alert-subscribed zones. */
data class ZoneSpotAlert(
    val zoneId: String,
    val zoneName: String,
    val spot: Spot,
)

/**
 * The Plus zone-alert stream [MONEY-001]: emits once per NEW community spot inside a zone whose
 * [Zone.notifyOnSpot] is on, while the user holds Plus and the Settings master gate is open.
 *
 * The Plus gate is LIVE and it is the pause mechanism of the downgrade rule: losing Plus makes this
 * stream go quiet without touching any zone's opt-in, and regaining it resumes the same
 * subscriptions.
 *
 * ⛔ **This is now one of TWO lanes, and they share their memory.**
 * [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001] added the background look, and what used to live
 * here in a `var baselineTaken` and a `mutableSetOf` now lives in [ZoneWatchRepository] — because a
 * worker that starts from nothing every fifteen minutes cannot inherit either. The old in-memory
 * version also had a flaw this fixes for free: it reset its baseline on ANY zone edit, so renaming a
 * zone made every spot already inside it read as "already there" again.
 *
 * The decision itself is [zoneSpotsWorthTelling] — the same function the worker calls, so the two
 * lanes cannot come to disagree about what counts as news.
 */
class ObserveZoneSpotAlertsUseCase(
    private val zoneRepository: ZoneRepository,
    private val spotRepository: SpotRepository,
    private val watchRepository: ZoneWatchRepository,
    private val observeIsPlus: ObserveIsPlusUseCase,
    private val appPreferences: AppPreferences,
    private val authRepository: AuthRepository,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    operator fun invoke(): Flow<ZoneSpotAlert> =
        combine(zoneRepository.observeZones(), observeIsPlus()) { zones, isPlus ->
            if (isPlus) zones.filter { it.notifyOnSpot } else emptyList()
        }
            .distinctUntilChanged()
            .flatMapLatest { subscribed ->
                if (subscribed.isEmpty()) {
                    emptyFlow()
                } else {
                    val ownUserId = authRepository.getCurrentSession()?.userId
                    subscribed.map { zone -> zoneAlerts(zone, ownUserId) }.merge()
                }
            }

    private fun zoneAlerts(zone: Zone, ownUserId: String?): Flow<ZoneSpotAlert> {
        val centre = GpsPoint(
            latitude = zone.lat,
            longitude = zone.lon,
            accuracy = 0f,
            timestamp = 0L,
            speed = 0f,
        )
        return spotRepository.observeNearbySpots(centre, zone.radiusMeters.toDouble())
            .transform { spots ->
                val now = clock()
                val look = zoneSpotsWorthTelling(
                    zone = zone,
                    spots = spots,
                    ownUserId = ownUserId,
                    alreadyAlerted = watchRepository.alertedSpotIds(zone.id),
                    hasBaseline = watchRepository.hasBaseline(zone.id),
                    nowMs = now,
                )
                // Recorded BEFORE emitting, and recorded whether or not the switch lets it speak:
                // what must never happen twice is the announcement, and a spot swallowed by a
                // closed master gate must not come back the moment it reopens.
                watchRepository.recordLook(
                    zoneId = zone.id,
                    spots = look.toRecord,
                    nowMs = now,
                    markBaseline = look.takingBaseline,
                )
                if (!appPreferences.notifyZoneSpots) return@transform
                look.alertable.forEach { spot ->
                    emit(ZoneSpotAlert(zoneId = zone.id, zoneName = zone.name, spot = spot))
                }
            }
            .catch { e ->
                // Stream isolation: one zone's failing geo query must not kill the whole merge.
                PaparcarLogger.w(TAG, "zone-alert stream failed for zone=${zone.id}", e)
            }
    }

    private companion object {
        const val TAG = "ZoneSpotAlerts"
    }
}
