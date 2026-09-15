package com.rndeveloper.paparcar.domain.zone

import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * Whether a spot is one this zone's owner should ever be told about.
 * [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]
 *
 * ⛔ A top-level function and not a private method, because from this ticket there are **two**
 * callers — the live stream while the app is open, and the worker while it is not. Two copies of
 * this rule is a divergence waiting to happen, and the shape it would take is the worst one: the
 * two surfaces disagreeing about what counts as news. [DET-VERDICT-NOT-PREDICATE-001]
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
): Boolean = when {
    // Withdrawn: the app stopped believing in it. [DET-HANDOFF-NOT-MANUAL-001 §B.3]
    spot.status == SpotStatus.RETRACTED -> false

    // ⛔ A PROVISIONAL spot lives 12 minutes and the background look runs every 15, so by the time
    // this path could tell anyone, the spot is gone. Excluded HERE and not only in the worker, so
    // the two lanes cannot disagree — and stated rather than silently missed: promising a class of
    // alert that structurally cannot arrive is worse than not offering it.
    spot.status == SpotStatus.PROVISIONAL -> false

    // Your own published spot is the place YOU just left — never an alert.
    spot.reportedBy == ownUserId -> false

    // Already dead by the time we looked.
    spot.expiresAt != 0L && spot.expiresAt <= nowMs -> false

    // The geo query overfetches by geohash bounds; the zone circle is the contract.
    else -> haversineMeters(
        zone.lat, zone.lon, spot.location.latitude, spot.location.longitude,
    ) <= zone.radiusMeters
}

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
