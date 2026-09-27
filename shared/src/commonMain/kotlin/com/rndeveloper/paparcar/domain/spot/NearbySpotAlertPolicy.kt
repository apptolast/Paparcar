package com.rndeveloper.paparcar.domain.spot

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * 500 m from YOUR BODY — about a six-minute walk.
 *
 * ⛔ **Same number as [com.rndeveloper.paparcar.domain.zone.ZONE_ALERT_MIN_RADIUS_METERS], and
 * deliberately a different constant.** That one is «the widest zone anyone can draw», so the zone
 * alert never looks at less ground than the largest zone that exists. This one is «how far I would
 * walk to a free space from where I am standing». Two questions that happen to share an answer today
 * — and the day one of them moves, folding them into one constant would move the other in silence.
 * The project already paid for this lesson with `ctaFitsInline`: two budgets, two constants.
 */
const val NEARBY_SPOT_ALERT_RADIUS_METERS = 500f

/**
 * The floor between two interruptions of this lane, DERIVED rather than picked.
 *
 * At 30 km/h (8,3 m/s) a driver crosses the whole 500 m circle in about 60 s, so the ground this
 * lane watches is completely renewed once a minute. A gap of 45 s therefore allows at most one alert
 * per fresh circle: short enough to still be useful to somebody going round the block looking for a
 * space — the case this lane exists for — and long enough that a dense street cannot turn the banner
 * into a flicker.
 *
 * ⛔ **A spot silenced by this gap is NOT recorded as told** (see [nearbySpotsWorthTelling]): it
 * waits and is announced after the gap. Recording it would make the pacing swallow spots instead of
 * ordering them, which is the difference between a queue and a leak.
 */
const val NEARBY_SPOT_ALERT_MIN_GAP_MS = 45_000L

/** What one look around the user decided: what to say now, and what to remember so it is not said twice. */
data class NearbySpotLook(
    /**
     * The one spot worth interrupting for, or null.
     *
     * ⛔ At most ONE, and the nearest. The banner is a sentence about a single free space, so a list
     * has nowhere to go; and the delivery slot replaces whatever it is showing, so emitting three
     * would mean showing the third and losing two without saying so.
     */
    val alert: Spot?,
    /** Spot ids to remember as already dealt with. Empty when nothing was decided. */
    val toRecord: List<Spot>,
    /** True when this look recorded the ground instead of reporting it — the lane's first. */
    val takingBaseline: Boolean,
)

/**
 * Is there a free space near the user right now that they have not been told about?
 * [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
 *
 * This is the lane the user asked for and never got: *«que te saliera un SPOT CERCA DE DONDE ESTÁS,
 * NO CERCA DE TU ZONA»*. The zone lane answers a different question — a place you saved, watched
 * while the app is closed, which is why it is Plus. This one is anchored to your body, only runs
 * with the map in front of you, and is therefore **free**: there is no vigilance to pay for when the
 * screen is already open and the position already being read.
 *
 * ## Why the anchor is the body and not the camera
 *
 * The map's own spot subscription follows the CAMERA
 * ([com.rndeveloper.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase], 5 km,
 * [NEARBY-A-DELIBERATE-LOOK-IS-NOT-A-PAN-001]) — pan to another city and it answers about that city,
 * correctly. Measuring this alert from there would announce spots «near» a place the user is only
 * looking at. So the caller passes the GPS fix, and a lane with no fix says nothing at all.
 *
 * ## What counts as news, and what the baseline is for
 *
 * Everything already inside the circle when the lane opens is recorded, not announced: the map is
 * showing those pins, and alerting them would teach the user to swipe the banner away — which is how
 * this kind of feature dies, not turned off, just ignored.
 *
 * ⛔ **The baseline records only what is INSIDE the circle**, and that is the choice that makes this
 * lane proximity-based rather than news-based. A spot that appears 3 km away is not recorded, so it
 * can still become an alert later, when the user has driven to within 500 m of it. Recording the
 * whole 5 km stream would be tidier and would have quietly killed the case this exists for: while
 * circling for a space, almost every spot was already there before you got close.
 *
 * @param alreadyTold spot ids this lane has already dealt with. Session-scoped by nature — the lane
 *        lives exactly as long as the map is on screen, so unlike the zone lane there is nothing for
 *        a worker to inherit and nothing to persist.
 * @param hasBaseline false is not "no spots": it is "this lane has not looked yet".
 * @param lastAlertAtMs when this lane last interrupted, or null if never. See
 *        [NEARBY_SPOT_ALERT_MIN_GAP_MS].
 */
fun nearbySpotsWorthTelling(
    anchor: GpsPoint,
    spots: List<Spot>,
    ownUserId: String?,
    alreadyTold: Set<String>,
    hasBaseline: Boolean,
    lastAlertAtMs: Long?,
    nowMs: Long,
): NearbySpotLook {
    val inCircle = spots
        .filter { isSpotWorthAnAlert(it, ownUserId, nowMs) }
        .filter { distanceFrom(anchor, it) <= NEARBY_SPOT_ALERT_RADIUS_METERS }

    if (!hasBaseline) {
        return NearbySpotLook(alert = null, toRecord = inCircle, takingBaseline = true)
    }

    val fresh = inCircle.filterNot { it.id in alreadyTold }
    if (fresh.isEmpty()) return NearbySpotLook(alert = null, toRecord = emptyList(), takingBaseline = false)

    // Paced, not dropped: nothing is recorded while the gap holds, so the spot is still news when it
    // lifts. See [NEARBY_SPOT_ALERT_MIN_GAP_MS].
    if (lastAlertAtMs != null && nowMs - lastAlertAtMs < NEARBY_SPOT_ALERT_MIN_GAP_MS) {
        return NearbySpotLook(alert = null, toRecord = emptyList(), takingBaseline = false)
    }

    val nearest = fresh.minByOrNull { distanceFrom(anchor, it) } ?: return NearbySpotLook(
        alert = null,
        toRecord = emptyList(),
        takingBaseline = false,
    )
    return NearbySpotLook(alert = nearest, toRecord = listOf(nearest), takingBaseline = false)
}

private fun distanceFrom(anchor: GpsPoint, spot: Spot): Double = haversineMeters(
    anchor.latitude,
    anchor.longitude,
    spot.location.latitude,
    spot.location.longitude,
)
