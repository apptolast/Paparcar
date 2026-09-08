package com.rndeveloper.paparcar.data.analytics

import com.rndeveloper.paparcar.data.geohash.encodeGeohash
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics

/**
 * Firebase Analytics [AnalyticsTracker] via the GitLive KMP SDK. [ANALYTICS-EVENTS-001]
 *
 * The single place that turns a domain fact into a wire event — every name and param key lives in
 * this file. It is also the single place location is reduced: the [GpsPoint][com.rndeveloper.paparcar.domain.model.GpsPoint]
 * becomes a [CELL_PRECISION]-char geohash cell and nothing finer ever leaves the device. A geohash
 * prefix can always be truncated to a coarser cell later, so the aggregation layer is free to
 * choose any precision ≤ this one.
 *
 * Fire-and-forget: a failing SDK is logged and swallowed — detection and spot publishing never
 * learn analytics exists.
 */
class FirebaseAnalyticsTracker : AnalyticsTracker {

    override fun track(event: AnalyticsEvent) {
        runCatching {
            when (event) {
                is AnalyticsEvent.ParkingSessionStarted -> log(
                    EVENT_PARKING_SESSION_STARTED,
                    buildMap {
                        put(PARAM_CELL, event.location.cell())
                        put(PARAM_SPOT_TYPE, event.spotType.name.lowercase())
                        event.detectionPath?.let { put(PARAM_DETECTION_PATH, it) }
                    },
                )
                is AnalyticsEvent.ParkingSessionEnded -> log(
                    EVENT_PARKING_SESSION_ENDED,
                    mapOf(
                        PARAM_CELL to event.location.cell(),
                        PARAM_END_REASON to event.reason.name.lowercase(),
                    ),
                )
                is AnalyticsEvent.SpotReleased -> log(
                    EVENT_SPOT_RELEASED,
                    mapOf(PARAM_CELL to event.location.cell()),
                )
                is AnalyticsEvent.SpotReported -> log(
                    EVENT_SPOT_REPORTED,
                    mapOf(PARAM_CELL to event.location.cell()),
                )
                is AnalyticsEvent.PaywallShown -> log(
                    EVENT_PAYWALL_SHOWN,
                    mapOf(PARAM_REASON to event.reason.name.lowercase()),
                )
            }
        }.onFailure { e ->
            PaparcarLogger.w(TAG, "analytics event dropped (${event::class.simpleName}): ${e.message}")
        }
    }

    private fun log(name: String, params: Map<String, String>) {
        Firebase.analytics.logEvent(name, params)
    }

    private fun com.rndeveloper.paparcar.domain.model.GpsPoint.cell(): String =
        encodeGeohash(latitude, longitude, CELL_PRECISION)

    private companion object {
        const val TAG = "FirebaseAnalyticsTracker"

        /** ~150 m cells. Fine enough for neighbourhood-level analysis, coarse enough to not be a
         *  coordinate; truncatable to any coarser cell downstream. Choosing geohash here de facto
         *  fixes the future aggregation cell SYSTEM as geohash (consistent with the spots index) —
         *  noted in ROADMAP-POST-PLUS §5.3. */
        const val CELL_PRECISION = 7

        const val EVENT_PARKING_SESSION_STARTED = "parking_session_started"
        const val EVENT_PARKING_SESSION_ENDED = "parking_session_ended"
        const val EVENT_SPOT_RELEASED = "spot_released"
        const val EVENT_SPOT_REPORTED = "spot_reported"
        const val EVENT_PAYWALL_SHOWN = "paywall_shown"

        const val PARAM_CELL = "cell"
        const val PARAM_REASON = "reason"
        const val PARAM_SPOT_TYPE = "spot_type"
        const val PARAM_DETECTION_PATH = "detection_path"
        const val PARAM_END_REASON = "end_reason"
    }
}
