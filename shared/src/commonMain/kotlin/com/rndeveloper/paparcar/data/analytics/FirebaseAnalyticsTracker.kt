package com.rndeveloper.paparcar.data.analytics

import com.rndeveloper.paparcar.data.geohash.encodeGeohash
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.analytics.AnalyticsUserProperty
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
                        event.armEvidence?.let { put(PARAM_ARM_EVIDENCE, it) }
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
                // [DET-EVERY-TRIGGER-REPORTS-WHAT-IT-PRODUCED-001] The denominator. Deliberately
                // WITHOUT a cell: a trigger firing is not a place, and the moment this event
                // carried one it would start reporting where the user is when nothing was even
                // detected — far more revealing than the events that DO carry a cell, and a new
                // data type on the Play Data Safety declaration.
                is AnalyticsEvent.TriggerFired -> log(
                    EVENT_TRIGGER_FIRED,
                    mapOf(
                        PARAM_TRIGGER to event.trigger.lowercase(),
                        PARAM_DISPOSITION to event.disposition.name.lowercase(),
                    ),
                )
                is AnalyticsEvent.SpotReported -> log(
                    EVENT_SPOT_REPORTED,
                    mapOf(PARAM_CELL to event.location.cell()),
                )
                is AnalyticsEvent.PaywallShown -> log(
                    EVENT_PAYWALL_SHOWN,
                    mapOf(PARAM_REASON to event.reason.name.lowercase()),
                )
                is AnalyticsEvent.FuelPriceMoveShown -> log(
                    EVENT_FUEL_PRICE_MOVE_SHOWN,
                    mapOf(
                        PARAM_FUEL_LANE to event.lane.name.lowercase(),
                        PARAM_DIRECTION to event.direction.name.lowercase(),
                    ),
                )
                is AnalyticsEvent.CheaperFuelNearbyShown -> log(
                    EVENT_CHEAPER_FUEL_NEARBY_SHOWN,
                    mapOf(PARAM_REFERENCE to event.reference.name.lowercase()),
                )
                is AnalyticsEvent.FuelStationFollowToggled -> log(
                    EVENT_FUEL_STATION_FOLLOW_TOGGLED,
                    mapOf(PARAM_FOLLOWED to event.followed.toString()),
                )
                is AnalyticsEvent.ZoneSpotAlertShown -> log(
                    EVENT_ZONE_SPOT_ALERT_SHOWN,
                    mapOf(
                        PARAM_CELL to event.location.cell(),
                        PARAM_CHANNEL to event.channel.name.lowercase(),
                    ),
                )
                is AnalyticsEvent.MonthRecapShown -> log(EVENT_MONTH_RECAP_SHOWN, emptyMap())
                is AnalyticsEvent.RefuelStopAnswered -> log(
                    EVENT_REFUEL_STOP_ANSWERED,
                    mapOf(PARAM_REFUELLED to event.refuelled.toString()),
                )
                is AnalyticsEvent.NotificationChannelToggled -> log(
                    EVENT_NOTIFICATION_CHANNEL_TOGGLED,
                    mapOf(
                        PARAM_CHANNEL to event.channel.name.lowercase(),
                        PARAM_ENABLED to event.enabled.toString(),
                    ),
                )
                is AnalyticsEvent.InstallAttributionResolved -> log(
                    EVENT_INSTALL_ATTRIBUTION_RESOLVED,
                    mapOf(PARAM_INSTALL_CHANNEL to event.channel.wire),
                )
            }
        }.onFailure { e ->
            PaparcarLogger.w(TAG, "analytics event dropped (${event::class.simpleName}): ${e.message}")
        }
    }

    override fun setUserProperty(property: AnalyticsUserProperty) {
        runCatching {
            when (property) {
                is AnalyticsUserProperty.DetectionState ->
                    Firebase.analytics.setUserProperty(PROPERTY_DETECTION_STATE, property.label.wire)
                is AnalyticsUserProperty.DetectionTier ->
                    Firebase.analytics.setUserProperty(
                        PROPERTY_DETECTION_TIER,
                        property.tier.name.lowercase(),
                    )
                is AnalyticsUserProperty.InstallChannelProperty ->
                    Firebase.analytics.setUserProperty(PROPERTY_INSTALL_CHANNEL, property.channel.wire)
            }
        }.onFailure { e ->
            PaparcarLogger.w(TAG, "analytics property dropped (${property::class.simpleName}): ${e.message}")
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

        /** [ANALYTICS-FUEL-HISTORY-ALERTS-COVERAGE-001] Gasolineras, historial y alertas: los tres
         *  sitios que no emitían nada. */
        const val EVENT_FUEL_PRICE_MOVE_SHOWN = "fuel_price_move_shown"
        const val EVENT_CHEAPER_FUEL_NEARBY_SHOWN = "fuel_cheaper_nearby_shown"
        const val EVENT_FUEL_STATION_FOLLOW_TOGGLED = "fuel_station_follow_toggled"
        const val EVENT_ZONE_SPOT_ALERT_SHOWN = "zone_spot_alert_shown"
        const val EVENT_MONTH_RECAP_SHOWN = "month_recap_shown"
        const val EVENT_REFUEL_STOP_ANSWERED = "refuel_stop_answered"
        const val EVENT_NOTIFICATION_CHANNEL_TOGGLED = "notification_channel_toggled"

        /** [MKT-INSTALL-ATTRIBUTION-CHANNEL-001] Fired once per install. */
        const val EVENT_INSTALL_ATTRIBUTION_RESOLVED = "install_attribution_resolved"

        /** [DET-EVERY-TRIGGER-REPORTS-WHAT-IT-PRODUCED-001] The attempts, so the successes above
         *  finally have something to be divided by. */
        const val EVENT_TRIGGER_FIRED = "det_trigger_fired"

        const val PARAM_CELL = "cell"
        const val PARAM_REASON = "reason"
        const val PARAM_SPOT_TYPE = "spot_type"
        const val PARAM_DETECTION_PATH = "detection_path"
        const val PARAM_END_REASON = "end_reason"

        /** [DET-EVERY-TRIGGER-REPORTS-WHAT-IT-PRODUCED-001] Which system got there, on both the
         *  numerator events and the denominator one. `detection_path` says how a park was
         *  CONFIRMED; these say what ARMED it and which lane it arrived on. */
        const val PARAM_ARM_EVIDENCE = "arm_evidence"
        const val PARAM_TRIGGER = "trigger"
        const val PARAM_DISPOSITION = "disposition"

        const val PARAM_FUEL_LANE = "fuel_lane"
        const val PARAM_DIRECTION = "direction"
        const val PARAM_REFERENCE = "reference"
        const val PARAM_FOLLOWED = "followed"
        const val PARAM_CHANNEL = "channel"
        const val PARAM_REFUELLED = "refuelled"
        const val PARAM_ENABLED = "enabled"
        const val PARAM_INSTALL_CHANNEL = "install_channel"

        /** [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001] The two durable dimensions. Their VALUES
         *  are closed vocabularies owned by the domain (`DetectionStateLabel.wire`) or by the two
         *  constants below — a free-form string here would make the property un-aggregatable. */
        const val PROPERTY_DETECTION_STATE = "detection_state"
        const val PROPERTY_DETECTION_TIER = "detection_tier"
        const val PROPERTY_INSTALL_CHANNEL = "install_channel"
    }
}
