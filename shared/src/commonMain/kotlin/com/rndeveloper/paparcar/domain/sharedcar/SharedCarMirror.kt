package com.rndeveloper.paparcar.domain.sharedcar

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SharedCarSession
import com.rndeveloper.paparcar.domain.model.SharedCarState
import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * WHAT travels to the group, decided in ONE pure place. [VEH-SHARED-001]
 *
 * A car two people drive is measured exactly like any other — same recorder, same lanes, same
 * evaluation — and what it measured is the group's (user, 15-09). So this is not a wall between the
 * driver and the group; it is three levels:
 *
 * 1. **The facts of the parking** — who, where, when, the street. Always.
 * 2. **The figures** — distance, time spent looking, whether a spot was freed. Always, because they
 *    are what the car's statistics are made of, and the statistics belong to everyone who drives it.
 * 3. **The drawn route** — only if its author's switch is on, and hidden on reading when it is
 *    turned off afterwards.
 *
 * ### Why level 3 changed (user, 16-09) — [PRIV-A-ROUTE-LEAVES-THE-PHONE-BY-ONE-DOOR-001]
 *
 * It used to travel ALWAYS and be filtered on reading, so that turning the switch back on brought
 * the old trips back instead of leaving a hole (user, 15-09). That is a good property and it was
 * bought at a price nobody had priced: the same geometry has a SECOND door, the personal one, where
 * the rule was the opposite — `syncRouteGeometry` keeps the polyline on the phone unless there is
 * explicit consent [PRIV-A-TRACE-IS-NOT-A-STAT-001]. One trace, two doors, opposite rules: a user
 * with the personal consent off — which is the default — uploaded their whole route anyway as soon
 * as the car was shared.
 *
 * The rule now: **each door asks its own permission, and neither uploads without it.** The personal
 * consent governs YOUR cloud history; this switch governs the GROUP's copy. What is lost is the
 * resurrection of trips recorded while the switch was off — they were never uploaded, so there is
 * nothing to bring back. What is kept is the undo for everything uploaded while it was on, because
 * visibility is still resolved on READING from the author's current `sharesRoutes`.
 *
 * ⛔ Still outside, and still a TYPE and not an `if`: `armEvidence`, `detectionPath`,
 * `tripMaxSpeedMps` and the plate. That is detection mechanics and on-device data — not withheld
 * out of privacy, but because it means nothing to whoever is driving and already lives whole in the
 * diagnostics. [PRIV-A-TRACE-IS-NOT-A-STAT-001]
 *
 * ⛔ The fix is still stripped of provenance: `speed`, `provider` and `satelliteCount` say how this
 * phone decided, which is the same detection mechanics as above.
 */
fun sharedCarMirrorPayload(
    session: UserParking,
    authorUid: String,
    authorName: String?,
    nowMs: Long,
    /**
     * The author's `shareRoutesInGroup` for THIS car. Not defaulted on purpose: a default here is
     * how the geometry travelled for two weeks without anybody deciding that it should.
     */
    shareRoutes: Boolean,
): SharedCarSession {
    return SharedCarSession(
        sessionId = session.id,
        authorUid = authorUid,
        authorName = authorName,
        location = session.location.sharedFix(),
        parkedAtMs = session.location.timestamp,
        endedAtMs = session.endedAtMs,
        isActive = session.isActive,
        address = session.address,
        placeInfo = session.placeInfo,
        // Figures: the car's, always.
        routeDistanceMeters = session.routeDistanceMeters,
        searchTimeMs = session.searchTimeMs,
        publishedSpot = session.publishedSpot,
        // Geometry: only with its own permission. `routeSnapped = false` beside a null polyline is
        // the established "no route" shape, the same one `withoutRouteGeometry()` writes on the
        // personal door — never the "recalculating" state, which needs a polyline to recalculate.
        routePolyline = session.routePolyline.takeIf { shareRoutes },
        routeSnapped = session.routeSnapped && shareRoutes,
        routeInferredSpans = session.routeInferredSpans.takeIf { shareRoutes },
        updatedAtMs = nowMs,
    )
}

/** The group's view of where the car stands after [session]. Same whitelist, one document. */
fun sharedCarStateOf(session: SharedCarSession): SharedCarState = SharedCarState(
    sessionId = session.sessionId,
    location = session.location,
    parkedAtMs = session.parkedAtMs,
    authorUid = session.authorUid,
    authorName = session.authorName,
    address = session.address,
    placeInfo = session.placeInfo,
    isParked = session.isActive,
    updatedAtMs = session.updatedAtMs,
)

/** Position without provenance: where the car is, not how the phone worked it out. */
private fun GpsPoint.sharedFix(): GpsPoint = GpsPoint(
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    timestamp = timestamp,
    speed = 0f,
)
