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
 * 3. **The drawn route** — always stored too, and SHOWN according to its author's switch. The
 *    geometry has to be there for the group's figures to be recomputable and, above all, so that
 *    turning the switch back on brings the old trips back instead of leaving a permanent hole
 *    (user, 15-09). Visibility is therefore a READING decision, resolved from the author's current
 *    `sharesRoutes`, not a frozen stamp on each session — a stamp could not be undone.
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
        // Geometry: also always. Whether the group SEES it is decided when the history is read,
        // from the author's switch — see `SharedCarMember.sharesRoutes`.
        routePolyline = session.routePolyline,
        routeSnapped = session.routeSnapped,
        routeInferredSpans = session.routeInferredSpans,
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
