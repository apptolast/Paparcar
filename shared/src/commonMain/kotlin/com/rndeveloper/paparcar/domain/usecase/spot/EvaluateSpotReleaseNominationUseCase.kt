package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.detection.PendingSpotRelease
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy

/**
 * [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] **May this boarding tell the neighbourhood that a plaza
 * is about to free up?**
 *
 * A VERDICT, not a predicate: its answer names a `SpotStatus`, changes what strangers see on the
 * map, and its refusal reasons are the vocabulary a field diagnosis will quote when a releasing
 * spot did or did not go out. So it is a use case of its own, with its own test.
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * ## What it is NOT allowed to be
 *
 * It does not decide that a departure happened — nothing here measures movement, and nothing here
 * may. *The event nominates, only measured movement confirms.* What this authorises is a
 * publication that **says in its own status** that the car is still in the space. The confirmation
 * ladder is untouched: the promotion to a real spot still goes through the ordinary departure path.
 *
 * ## The four refusals, and why each exists
 *
 * - **no live plaza** — the boarded car is not parked anywhere we know of. Nothing to free.
 * - **already nominated** — a live nomination for this plaza is already out there. Re-publishing
 *   would be harmless on the wire (REPLACE, same id) but it would also reset nothing and hide the
 *   flapping: a dongle that connects twenty times a day would nominate twenty times, and the
 *   diagnostics would show twenty publications for one boarding.
 * - **private zone / area too wide** — asked through the same [SpotReleasePolicy] gate the real
 *   publication uses, so a garage cannot leak here either. ⛔ Asked of the zones **as they are
 *   now**, never of the stamp the park left behind.
 * - **approximate park** — the session only knows its car's rest to a radius. A releasing AREA is
 *   two doubts stacked ("somewhere around here, and not free yet"), which is more than a stranger
 *   can act on. Zone parks keep publishing normally at the real departure.
 *
 * The Bluetooth rebound gate is NOT in this list on purpose: whether a CONNECT is a genuine
 * boarding or a flapping head unit is the BT lane's own question, already answered by
 * [DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001], and its answer arrives here as
 * [boardingIsCredible]. Re-deriving it would be a second place to get it wrong.
 */
class EvaluateSpotReleaseNominationUseCase {

    operator fun invoke(
        /** The boarded vehicle's live parking session, or null when it holds no plaza. */
        session: UserParking?,
        /** The nomination already on record, if any. */
        pending: PendingSpotRelease?,
        /** The user's private zones **as they are now** (`getPrivateZonesSnapshot()`). */
        privateZones: List<Zone>,
        /**
         * Does the lane that saw this boarding vouch for it? The BT lane answers with its
         * anti-oscillation rule; the AR lane answers `true`, because `EvaluateArEnterArmUseCase`
         * has already required the boarding to be tied to THIS car's own fence.
         */
        boardingIsCredible: Boolean,
        nowMs: Long,
    ): SpotReleaseNomination {
        if (!boardingIsCredible) return SpotReleaseNomination.Skip(REASON_NOT_A_BOARDING)
        if (session == null || !session.isActive) return SpotReleaseNomination.Skip(REASON_NO_PLAZA)
        // A departure was already deduced from this session and published its own spot; this
        // boarding is not news about the plaza, it is news about the same trip.
        if (session.provisionalDepartureAtMs != null) return SpotReleaseNomination.Skip(REASON_ALREADY_PUBLISHED)
        // ⛔ **One nomination per parking session, and NO clock in this question.**
        //
        // This used to be bounded by the releasing TTL, so a second boarding four minutes later
        // published the plaza again. That is wrong for the case the Bluetooth lane actually lives
        // in: a parked car whose link DROPS and comes back — a dongle losing the phone, a head unit
        // waking up — is not somebody boarding twice. It is the same car, in the same space, that
        // has not moved. The anti-oscillation gate upstream only sees a reconnect while the hunt is
        // still open (~30 s); a drop that returns ten minutes later walks straight past it.
        //
        // A plaza is released ONCE. After the first nomination the only two things that may happen
        // to that document are the promotion the real departure writes and the withdrawal — never a
        // second "se libera ahora" about a car that is still standing there.
        //
        // The cost is named and accepted: if the first nomination expires and the driver really
        // leaves twenty minutes later, that departure gets no head start. It still publishes its
        // spot through the ordinary path. A missed head start is the cheap failure; a plaza that
        // keeps re-announcing itself is the expensive one.
        //
        // This is the same split [DET-RETRACT-DENIED-FOREVER-001] already drew for the deduced
        // departure: *"is there a document out there to withdraw"* is a time-bounded question,
        // *"has this session already spent its one publication"* is not.
        if (pending != null && pending.spotId == session.id) {
            return SpotReleaseNomination.Skip(REASON_ALREADY_NOMINATED)
        }
        if (session.isApproximate) return SpotReleaseNomination.Skip(REASON_APPROXIMATE_PARK)

        val shape = SpotReleasePolicy.shapeFor(
            location = session.location,
            zoneRadiusMeters = session.zoneRadiusMeters,
            detectionReliability = session.detectionReliability,
            privateZones = privateZones,
        )
        if (shape is SpotReleasePolicy.Shape.Withheld) {
            return SpotReleaseNomination.Skip(shape.reason.name.lowercase())
        }
        return SpotReleaseNomination.Publish(session = session, shape = shape)
    }

    companion object {
        const val REASON_NOT_A_BOARDING = "not_a_boarding"
        const val REASON_NO_PLAZA = "no_plaza"
        const val REASON_ALREADY_PUBLISHED = "already_published"
        const val REASON_ALREADY_NOMINATED = "already_nominated"
        const val REASON_APPROXIMATE_PARK = "approximate_park"
    }
}

/** The verdict of [EvaluateSpotReleaseNominationUseCase]. */
sealed interface SpotReleaseNomination {
    /** Publish [session]'s plaza as releasing, with the shape the release policy allows. */
    data class Publish(
        val session: UserParking,
        val shape: SpotReleasePolicy.Shape,
    ) : SpotReleaseNomination

    /** Say nothing, and say WHY — the reason is diagnostics vocabulary, not a log line. */
    data class Skip(val reason: String) : SpotReleaseNomination
}
