package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * [DET-A-BT-CONNECT-IS-A-DEPARTURE-001] Has the paired car MEASURABLY driven away from the plaza it
 * was parked in, since the user got into it?
 *
 * A pure policy function, not an injected evaluator: it is consulted from one place, produces no
 * `detectionPath` of its own (the departure it triggers is published through the common door, which
 * stamps its own provenance) and is directly testable — the same shape as [freedSpotIsStillThere]
 * and [measuredDepartureAtMs]. [DET-VERDICT-NOT-PREDICATE-001]
 *
 * ## What it is for
 *
 * A Bluetooth CONNECT proves **boarding**, never a drive: getting in to fetch a phone charger, or
 * to turn the radio on and get out again, connects exactly the same. So the CONNECT only ARMS, and
 * this is the measurement that may confirm — the doctrine's *"the event NOMINATES, only measured
 * movement confirms"*, applied to the lane that had no departure watch at all.
 *
 * ## Why displacement and not speed
 *
 * ⛔ **A single credible-speed sample is not a drive.** One indoor Doppler mirage (14 km/h, acc
 * 21 m, on a motionless phone) already broke a fence and published a phantom plaza at somebody's
 * living room — [DET-DRIVE-PROOF-001] settled that *the corroboration is the track, not one
 * sample*. Here the track is the cheapest possible one: how far the phone has got from the PIN,
 * against how far a person could have walked in the same time.
 *
 * That arithmetic is [ParkingDetectionConfig.isBeyondPedestrianReach], which already describes
 * itself as *"the single physics check behind [DET-RIDE-PROOF-001]"* — it grants the fence radius
 * and the fix's own accuracy to the pedestrian before calling anything a drive, so a park at the
 * edge of a wide fence with a sloppy fix cannot pass by standing still.
 *
 * ## Two ways it refuses, both of them silence
 *
 * - **A stale fix.** The fused provider's first emission is routinely a cached last-known location
 *   — the trap `freshFixMaxAgeMs` exists for, after the field day it served the same coordinate
 *   with speed=0 for four minutes mid-drive. A cached fix from somewhere else, timestamped just
 *   after the CONNECT, is precisely the shape that would fake a drive-away from a parked car.
 * - **Not far enough yet.** Which is the ordinary answer for most of the watch, and the final one
 *   when the user never actually drove. The caller's job is then to stand down quietly: no plaza,
 *   no session closed, no question asked. [Asymmetric failure]
 *
 * @param pin where the car was parked ([com.rndeveloper.paparcar.domain.model.UserParking.location]).
 * @param current the fix being judged, live from the watch.
 * @param connectedAtMs the measured instant of boarding, from `BtConnectionStore`.
 * @param fenceRadiusMeters the session's own fence radius — granted to the pedestrian, because
 *   being anywhere inside the fence was always compatible with never having left.
 * @param nowMs wall clock, to judge the fix's own freshness.
 */
fun btDriveAwayIsMeasured(
    pin: GpsPoint,
    current: GpsPoint,
    connectedAtMs: Long,
    fenceRadiusMeters: Float,
    nowMs: Long,
    config: ParkingDetectionConfig,
): Boolean {
    if (nowMs - current.timestamp > config.freshFixMaxAgeMs) return false
    return config.isBeyondPedestrianReach(
        distanceMeters = haversineMeters(pin.latitude, pin.longitude, current.latitude, current.longitude),
        elapsedMs = current.timestamp - connectedAtMs,
        fenceRadiusMeters = fenceRadiusMeters,
        accuracyMeters = current.accuracy,
    )
}
