@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.rndeveloper.paparcar.domain.usecase.zone

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform

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
 * The Plus gate is LIVE and it is the pause mechanism of the downgrade rule: losing Plus makes
 * this stream go quiet without touching any zone's opt-in, and regaining it resumes the same
 * subscriptions. Deliberately client-side for 1.x: it runs while the app process is alive (the
 * AppViewModel collects it), because geofences are frozen and push fan-out needs a backend —
 * see ZONE-SUBSCRIBE-001 for the background delivery follow-up.
 */
class ObserveZoneSpotAlertsUseCase(
    private val zoneRepository: ZoneRepository,
    private val spotRepository: SpotRepository,
    private val observeIsPlus: ObserveIsPlusUseCase,
    private val appPreferences: AppPreferences,
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): Flow<ZoneSpotAlert> =
        combine(zoneRepository.observeZones(), observeIsPlus()) { zones, isPlus ->
            if (isPlus) zones.filter { it.notifyOnSpot } else emptyList()
        }
            // Any zone edit re-enters flatMapLatest and resets each zone's baseline — safe
            // direction: after a reset, existing spots read as "already there", never as news.
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
        var baselineTaken = false
        val alerted = mutableSetOf<String>()
        val center = GpsPoint(
            latitude = zone.lat,
            longitude = zone.lon,
            accuracy = 0f,
            timestamp = 0L,
            speed = 0f,
        )
        return spotRepository.observeNearbySpots(center, zone.radiusMeters.toDouble())
            .transform { spots ->
                val inZone = spots.filter { spot -> spot.isAlertable(zone, ownUserId) }
                if (!baselineTaken) {
                    // Spots that already existed when the subscription started are not NEWS —
                    // alerting the whole map on cold start would teach the user to swipe these away.
                    baselineTaken = true
                    alerted += inZone.map { it.id }
                    return@transform
                }
                inZone.forEach { spot ->
                    // Swallowed (not deferred) while the Settings master gate is closed: turning
                    // it back on must not dump every spot that appeared meanwhile.
                    if (alerted.add(spot.id) && appPreferences.notifyZoneSpots) {
                        emit(ZoneSpotAlert(zoneId = zone.id, zoneName = zone.name, spot = spot))
                    }
                }
            }
            .catch { e ->
                // Stream isolation: one zone's failing geo query must not kill the whole merge.
                PaparcarLogger.w(TAG, "zone-alert stream failed for zone=${zone.id}", e)
            }
    }

    private fun Spot.isAlertable(zone: Zone, ownUserId: String?): Boolean =
        status != SpotStatus.RETRACTED &&
            // Your own published spot is the place YOU just left — never an alert.
            reportedBy != ownUserId &&
            // The geo query overfetches by geohash bounds; the zone circle is the contract.
            haversineMeters(zone.lat, zone.lon, location.latitude, location.longitude) <= zone.radiusMeters

    private companion object {
        const val TAG = "ZoneSpotAlerts"
    }
}
