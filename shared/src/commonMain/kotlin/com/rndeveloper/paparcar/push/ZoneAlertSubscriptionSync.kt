package com.rndeveloper.paparcar.push

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.data.geohash.geohashQueryBounds
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.push.PushTokenProvider
import com.rndeveloper.paparcar.domain.repository.ZoneAlertSubscriptionRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.zone.zoneAlertSubscriptionsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Keeps the server's index in step with what this user actually wants watched.
 * [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
 *
 * ⛔ **This class IS the entitlement check.** The Cloud Function never asks whether anyone holds
 * Plus — it cannot, that lives in RevenueCat — so being in the index at all is the permission. The
 * three conditions that used to gate the stream gate the ROW instead: Plus ∧ the Settings switch ∧
 * a zone with its own opt-in. Lose any of them and the row goes, which pauses the alert **without
 * touching a single zone**. The same downgrade rule as always, moved to where the server can see it.
 *
 * ⛔ A stream, not a write at app start, for the reason [DET-WATCH-REACTIVATE-001] had to learn: a
 * clean install has an empty Room while the sync lands, so a single read on launch subscribes
 * nothing and stays wrong until the next launch.
 *
 * Lives in `commonMain` since [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]: of everything above, the
 * only Android-shaped part was where the token came from, and that is now [PushTokenProvider].
 */
class ZoneAlertSubscriptionSync(
    private val zoneRepository: ZoneRepository,
    private val subscriptionRepository: ZoneAlertSubscriptionRepository,
    private val observeIsPlus: ObserveIsPlusUseCase,
    private val appPreferences: AppPreferences,
    private val authRepository: AuthRepository,
    private val pushTokens: PushTokenProvider,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** Injected so a test can drive the stream: the token discipline below is the kind of rule that
     *  breaks in silence, and a class that owns a `Dispatchers.Default` scope cannot be asked. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    /** Bumped to force a re-emission when something outside these flows changes — a token rotation. */
    private val nudge = MutableStateFlow(0)

    fun start() {
        scope.launch {
            combine(
                zoneRepository.observeZones(),
                observeIsPlus(),
                appPreferences.observeNotifyZoneSpots(),
                nudge,
            ) { zones, isPlus, notify, _ ->
                Triple(zones.filter { it.notifyOnSpot }, isPlus, notify)
            }
                // The subscribed zones, the entitlement and the switch — nothing else. A zone moving
                // its radius DOES change the cells, so the whole triple is compared, not a count.
                .distinctUntilChanged()
                .catch { e -> PaparcarLogger.w(TAG, "zone/entitlement stream failed", e) }
                .collect { (zones, isPlus, notify) ->
                    syncOnce(zones, isPlus, notify)
                }
        }
    }

    /** Re-runs the sync against whatever is current — used when the push token rotates. */
    fun resyncNow() {
        nudge.value = nudge.value + 1
    }

    private suspend fun syncOnce(
        zones: List<Zone>,
        isPlus: Boolean,
        notificationsEnabled: Boolean,
    ) {
        val uid = authRepository.getCurrentSession()?.userId
        if (uid.isNullOrBlank()) {
            PaparcarLogger.d(TAG) { "· no session — nothing to subscribe" }
            return
        }

        // ⛔ Only asked for when there is something to subscribe. Requesting a token installs one on
        // a device that may never need it — on iOS it registers the app with APNs — and a token is a
        // device identifier: not fetching it for a free user who never turned this on is the honest
        // default.
        val token = if (isPlus && notificationsEnabled && zones.isNotEmpty()) {
            runCatching { pushTokens.currentToken() }
                .onFailure { PaparcarLogger.w(TAG, "could not obtain a push token", it) }
                .getOrNull()
        } else {
            null
        }

        val subs = zoneAlertSubscriptionsFor(
            uid = uid,
            zones = zones,
            isPlus = isPlus,
            notificationsEnabled = notificationsEnabled,
            tokens = listOfNotNull(token),
            nowMs = clock(),
            cellsOf = { lat, lon, radius -> geohashQueryBounds(lat, lon, radius).map { it.first } },
        )

        subscriptionRepository.replaceAll(uid, subs)
            .onFailure { PaparcarLogger.w(TAG, "could not sync zone alert subscriptions", it) }
    }

    private companion object {
        const val TAG = "ZoneAlertSubs"
    }
}
