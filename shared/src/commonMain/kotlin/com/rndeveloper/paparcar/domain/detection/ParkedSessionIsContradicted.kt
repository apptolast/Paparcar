package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * Does the paired car's last Bluetooth CONNECT say the parked session is talking about a place the
 * car has already left? [DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001]
 *
 * The spatial twin of [freedSpotIsStillThere]: that one asks whether the freed plaza is still there
 * IN TIME, this one whether it is where we think it is IN SPACE. Both gate the same act —
 * advertising a plaza to strangers — and both answer `false` when they cannot know.
 *
 * ## The case
 *
 * The app holds a session at A. Somebody else takes the car and parks it at B; no event reaches the
 * phone, so A stays active and stays wrong. When a departure is eventually processed, the freed spot
 * is built from `session.location` — it goes out at **A**, where the car has not been for hours.
 * Measured in `ProcessConfirmedDepartureUseCaseTest`: the published spot lands over a kilometre from
 * the place actually freed.
 *
 * A phantom plaza is the false positive the asymmetric-error rule exists to prevent, and this one
 * cannot be caught downstream: the plaza at A *was* real, so nothing about the coordinates looks
 * wrong. What is wrong is the HOUR — it was freed by someone else, at a time nobody witnessed — and
 * the freshness gate cannot see it, because it measures from OUR exit.
 *
 * ## Why the CONNECT is the authority, and the session is not
 *
 * A Bluetooth CONNECT proves the phone was within radio range of that car. It is not a trip, but it
 * is a MEASUREMENT of where the car was, and it beats what a session merely REMEMBERS. The session's
 * pin is the better measurement when the two agree — it was chosen pin-grade, while the connect fix
 * is a cached last-known — so this function only asks whether they CONTRADICT each other, never
 * which coordinate to use.
 *
 * ## Fails closed, three times over
 *
 * With no connect on record, no fix, or a connect that PREDATES the session, the answer is `false` —
 * publish exactly as before. A connect older than the park describes a previous trip and says
 * nothing about where the car is now, which is the same rule the safety net's identity gate already
 * applies (`lastBtConnectedAtMs < sessionStartMs`, [DET-BT-IDENTITY-GATE-001]).
 *
 * The cost of a wrong `true` is a plaza we decline to publish — a false negative, the safe side. The
 * cost of a wrong `false` is a stranger walking to a space that was taken hours ago.
 *
 * @param sessionLocation the pin the session would publish. Its `timestamp` is the park instant.
 * @param connectFix cached fix stamped when the paired car connected, or null when none was held.
 * @param connectedAtMs instant of that connect, or null when there is no connect on record.
 */
fun parkedSessionIsContradictedByBtConnect(
    sessionLocation: GpsPoint,
    connectFix: GpsPoint?,
    connectedAtMs: Long?,
    config: ParkingDetectionConfig,
): Boolean {
    if (connectFix == null || connectedAtMs == null) return false
    if (connectedAtMs < sessionLocation.timestamp) return false

    val distanceMeters = haversineMeters(
        sessionLocation.latitude, sessionLocation.longitude,
        connectFix.latitude, connectFix.longitude,
    )
    // Both fixes' error bars, plus the slack the link itself needs: the phone can pair from the
    // pavement, so a connect legitimately happens metres away from the pin without the car having
    // moved at all. Under the sum of the three, the two readings are the same place.
    val toleranceMeters = config.btConnectContradictsSessionMeters +
        sessionLocation.accuracy + connectFix.accuracy
    return distanceMeters > toleranceMeters
}
