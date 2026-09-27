package com.rndeveloper.paparcar.domain.zone

import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.spot.isSpotWorthAnAlert
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * How far the ALERT looks, which is not how big the zone is.
 * [ZONE-A-LOOK-THAT-READS-THE-CACHE-IS-NOT-A-LOOK-001]
 *
 * The two numbers answer different questions and only one of them was being asked:
 * - [Zone.radiusMeters] answers **«where is my place»** — a pin with a tolerance. It goes from 50 m
 *   to 500 m and a user who drags it down to 50 is marking a doorway, not declaring that they only
 *   want to hear about spots inside their doorway.
 * - The alert needs **«how far would I walk from a free space to that place»**, which is a different
 *   question from how far the MAP looks: `ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS` is
 *   5 km (it said 2 km here until 27-09, and had been 5 since NEARBY-A-DELIBERATE-LOOK-IS-NOT-A-PAN-001
 *   — a stale number in a comment, which is the cheapest kind of lie to leave behind).
 *
 * So the alert gets a FLOOR and not a multiplier: `max` honours the user who deliberately drew a
 * wide zone (500 m stays 500 m) and rescues the one who drew a pin. Adding to an explicit choice
 * would be deciding for them; leaving a 50 m zone alone is an alert that can never fire.
 *
 * ⚠️ Whatever this returns is the circle the policy clips to AND the radius the query asks for —
 * widening one without the other changes nothing at all, and the version that widens only the query
 * is the expensive half.
 */
fun zoneAlertRadiusMeters(zone: Zone): Float =
    maxOf(zone.radiusMeters, ZONE_ALERT_MIN_RADIUS_METERS)

/**
 * 500 m — the widest zone anyone can draw, so the alert never looks at less ground than the largest
 * zone that exists, and still a walk (~6 min) rather than a drive. ⛔ Not 2 km: a spot 2 km from
 * home is not a spot near home, it is a different trip.
 */
const val ZONE_ALERT_MIN_RADIUS_METERS = 500f

/**
 * Whether a spot is one this zone's owner should ever be told about.
 * [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]
 *
 * ⛔ A top-level function and not a private method, because from this ticket there are **two**
 * callers — the live stream while the app is open, and the worker while it is not. Two copies of
 * this rule is a divergence waiting to happen, and the shape it would take is the worst one: the
 * two surfaces disagreeing about what counts as news. [DET-VERDICT-NOT-PREDICATE-001]
 *
 * ⛔ **This function is now only about the ZONE.** The vetoes that had nothing to do with a circle —
 * withdrawn, provisional and releasing spots, an expired one, and above all *your own spot is never
 * your alert* — moved to [isSpotWorthAnAlert] when the nearby lane was born and needed the very same
 * list. They were never zone rules; they were sitting in the only file that happened to need them.
 * [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
 *
 * @param nowMs used only to drop spots that have already expired. A spot whose TTL ran out between
 *        the query and this call is not news; without the clock the worker would notify about a
 *        free space that stopped existing before the phone buzzed.
 */
fun isAlertableInZone(
    spot: Spot,
    zone: Zone,
    ownUserId: String?,
    nowMs: Long,
): Boolean = isSpotWorthAnAlert(spot, ownUserId, nowMs) &&
    // The geo query overfetches by geohash bounds; the ALERT circle is the contract — and it is
    // deliberately not the zone's own, see [zoneAlertRadiusMeters].
    haversineMeters(
        zone.lat, zone.lon, spot.location.latitude, spot.location.longitude,
    ) <= zoneAlertRadiusMeters(zone)

/** What one look at one zone decided: what to say, and what to write down so it is not said twice. */
data class ZoneWatchLook(
    /** Spots worth interrupting for. Empty on the first look at a zone — see [takingBaseline]. */
    val alertable: List<Spot>,
    /**
     * Every spot id that must be persisted as "seen", alerts included.
     *
     * ⛔ Written down whether or not it was announced. On the first look nothing is announced and
     * everything is recorded — that IS the baseline — and recording only the announced ones would
     * make the first background run dump the whole zone the next time round.
     */
    val toRecord: List<Spot>,
    /** True when this look was the zone's first: it recorded the ground instead of reporting it. */
    val takingBaseline: Boolean,
)

/**
 * What to tell the user about one zone, given what is there and what has already been said.
 * [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]
 *
 * Pure and synchronous: the reading and the writing are the caller's, so the SAME decision serves a
 * long-lived stream and a worker that starts from nothing every fifteen minutes. That is the whole
 * point — the old rule lived in a `var baselineTaken` and a `mutableSetOf` inside the stream, which
 * a worker cannot inherit: it would either take a fresh baseline every run and **never alert**, or
 * skip it and **dump the zone every quarter of an hour**.
 *
 * @param hasBaseline whether this zone has ever been looked at. False is not "no spots": it is
 *        "nobody has asked yet", the same distinction the whole nearby layer already makes.
 */
fun zoneSpotsWorthTelling(
    zone: Zone,
    spots: List<Spot>,
    ownUserId: String?,
    alreadyAlerted: Set<String>,
    hasBaseline: Boolean,
    nowMs: Long,
): ZoneWatchLook {
    val inZone = spots.filter { isAlertableInZone(it, zone, ownUserId, nowMs) }
    return if (!hasBaseline) {
        // Alerting everything already on the map would teach the user to swipe these away, which is
        // how a vigilance feature dies: not turned off, just ignored.
        ZoneWatchLook(alertable = emptyList(), toRecord = inZone, takingBaseline = true)
    } else {
        val fresh = inZone.filterNot { it.id in alreadyAlerted }
        ZoneWatchLook(alertable = fresh, toRecord = fresh, takingBaseline = false)
    }
}
