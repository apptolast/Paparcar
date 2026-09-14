package com.rndeveloper.paparcar.domain.spot

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * What SHAPE the community spot freed by a departing session may take — **and whether it may be
 * published at all**. [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]
 * [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001]
 *
 * ## The invariant
 *
 * *A published spot inherits the session's shape (point, or zone with its radius) and a confidence
 * bounded by that shape.* An approximate session (`UserParking.zoneRadiusMeters != null`) is the
 * detection saying, honestly, "the car rested somewhere in this circle" — the map draws it, the
 * peek explains it. Publication used to erase that honesty at the community boundary: the freed
 * spot went out as an exact point at the circle's centre, with the session's raw reliability —
 * which is 1.0 when a prompt's "Sí" confirmed it, because the answer proves the FACT of parking,
 * never the coordinate [DET-USER-YES-IS-NOT-A-COORDINATE-001].
 *
 * Measured over the 2026-08-29→09-01 field corpus, ~1 in 6 confirms ended as a zone (r=60–250 m,
 * doubt up to 495 m), none of them at an underground garage — a zone is "rest unwitnessed", not a
 * place category. So the policy reads the session's own measurement, not any geography:
 *
 * - **Exact session** → exact spot, unchanged.
 * - **Zone within [ZONE_PUBLISH_MAX_RADIUS_METERS]** → a ZONE spot: same document plus the radius,
 *   confidence capped at [ZONE_MAX_CONFIDENCE], and the provisional (short) lifetime — the
 *   industry-standard degradation (a park-out event published as area-level probability), and the
 *   asymmetric-failure doctrine applied to the seeker: an area with low odds sends nobody on a
 *   wild pin chase.
 * - **Zone above the ceiling** → [WithholdReason.AREA_TOO_WIDE], conservative silence: sending a
 *   seeker to scan a 250 m circle is the same credibility damage as a ghost pin, on foot. The
 *   user's own history keeps the session either way — only the community publication is gated.
 *
 * The witnessed departure that triggers publication validates the AREA claim (the car measurably
 * drove out of the fence centred on the zone), never the centre coordinate — which is why the zone
 * spot is honest and the exact pin was not.
 *
 * ## …and the user's own will, asked WHEN it is acted on
 *
 * [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] A park inside one of the user's
 * PRIVATE zones publishes nothing — that was always the rule, but it lived as a term
 * (`session.privateZoneId == null`) copied into three of the four doors out to the community and
 * simply missing from the fourth (the manual "Me voy"), which published the plaza of a private
 * garage. Written once, here, it cannot be missing from a door again.
 *
 * The move that matters is not the deduplication, it is the **moment**: `privateZoneId` was a
 * verdict SEALED at park time ([com.rndeveloper.paparcar.domain.usecase.parking.ConfirmParkingUseCase]),
 * so a zone drawn while the car was already parked protected nothing, and a zone deleted afterwards
 * kept silencing a park forever. The question has a live answer and is asked at the only instant it
 * is acted on — publication — so no re-evaluation pass has to chase zone create / edit / move /
 * resize / delete across every active session. `privateZoneId` survives as PROVENANCE (which zone
 * it was when the car arrived), never again as the decision.
 *
 * ⚠️ Private zone is checked BEFORE the area ceiling: both withhold, but the reason is read by the
 * log, by the release dialog's copy and by the history detail, and the user's explicit will is the
 * truer account of why nothing went out.
 */
object SpotReleasePolicy {

    /** How far the community can be asked to look. A zone the size of a street block (the measured
     *  walk-entered zones, r=60 m) is scannable from its centre; past this ceiling the "spot" is a
     *  neighbourhood, not a plaza. Sized at the egress-birth floor (150 m) — the same scale the
     *  detector treats as "still near the car" on a sparse stream. ⚠️ Calibratable: revisit with
     *  the next field corpus (backlog doc, "Pendiente de campo"). */
    const val ZONE_PUBLISH_MAX_RADIUS_METERS = 150f

    /** A zone spot never claims more than a coin flip, whatever the session's reliability says:
     *  the reliability speaks about the PARK being real (fact), the radius about WHERE (place),
     *  and the confidence shown to a seeker must be bounded by the weaker of the two. In
     *  particular a user-confirmed zone (reliability 1.0) stays capped — the "Sí" proved the park,
     *  not the spot. */
    const val ZONE_MAX_CONFIDENCE = 0.5f

    /** Why nothing is published. Two very different accounts that used to collapse into one silent
     *  `Withheld`: one is the app declining to send a stranger across a neighbourhood, the other is
     *  the user having said this place is theirs.
     *  [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] */
    enum class WithholdReason {
        /** The park falls inside one of the user's private zones. */
        PRIVATE_ZONE,

        /** The session's doubt circle is wider than [ZONE_PUBLISH_MAX_RADIUS_METERS]. */
        AREA_TOO_WIDE,
    }

    /** The shape a freed spot is allowed to be published with. */
    sealed interface Shape {
        /** Exact session → exact spot, the session's own confidence. */
        data class Exact(val confidence: Float) : Shape

        /** Bounded zone session → zone spot: area + capped confidence + provisional lifetime. */
        data class Zone(val radiusMeters: Float, val confidence: Float) : Shape

        /**
         * Nothing is published, and [reason] says why.
         *
         * ⚠️ A `data class` on purpose, where it used to be a `data object`: carrying the reason is
         * half the point, and turning every `shape == Shape.Withheld` into `shape is Shape.Withheld`
         * is what made the compiler enumerate the four publish paths this ticket had to sweep.
         */
        data class Withheld(val reason: WithholdReason) : Shape
    }

    /**
     * The user's private zone containing [location], or null when the park is not inside one.
     *
     * The shared predicate behind the gate — also the one
     * [com.rndeveloper.paparcar.domain.usecase.parking.ConfirmParkingUseCase] stamps provenance
     * with, and the one the release dialog and the history detail name the zone from. A pure
     * top-level-shaped helper on the policy that already owns the publication verdict, NOT a use
     * case: it decides nothing on its own and appears in no diagnostic vocabulary.
     * [DET-VERDICT-NOT-PREDICATE-001]
     *
     * @param privateZones the user's zones **already filtered to `isPrivate`** (what
     *   `ZoneRepository.getPrivateZonesSnapshot()` returns). Callers holding the full list —
     *   the UI reads `HomeState.zones` — filter at their own edge.
     */
    fun privateZoneAt(location: GpsPoint, privateZones: List<Zone>): Zone? =
        privateZones.firstOrNull { zone ->
            haversineMeters(location.latitude, location.longitude, zone.lat, zone.lon) <= zone.radiusMeters
        }

    /**
     * The shape for a session at [location] with the given doubt radius and reliability.
     *
     * @param zoneRadiusMeters [com.rndeveloper.paparcar.domain.model.UserParking.zoneRadiusMeters]
     *   — null for an exact pin.
     * @param detectionReliability the session's stamped reliability; null (legacy callers with no
     *   session) reads as full confidence, exactly as the publish paths defaulted before.
     * @param privateZones the user's private zones **as they are now**, not as they were when the
     *   car arrived — see the class KDoc. Empty is a legitimate answer ("no zones, nothing to
     *   withhold for"), which is also the safe reading when a repository read fails.
     */
    fun shapeFor(
        location: GpsPoint,
        zoneRadiusMeters: Float?,
        detectionReliability: Float?,
        privateZones: List<Zone>,
    ): Shape {
        if (privateZoneAt(location, privateZones) != null) {
            return Shape.Withheld(WithholdReason.PRIVATE_ZONE)
        }
        val confidence = detectionReliability ?: 1f
        return when {
            zoneRadiusMeters == null -> Shape.Exact(confidence)
            zoneRadiusMeters <= ZONE_PUBLISH_MAX_RADIUS_METERS ->
                Shape.Zone(zoneRadiusMeters, minOf(confidence, ZONE_MAX_CONFIDENCE))
            else -> Shape.Withheld(WithholdReason.AREA_TOO_WIDE)
        }
    }

    /** The confidence to publish [this] with, or 0 when it publishes nothing. Folded here so the
     *  four publish paths stop repeating the same three-branch `when` (whose `Withheld` arm each
     *  had to mark "unreachable — gated above"). */
    fun Shape.publishConfidence(): Float = when (this) {
        is Shape.Exact -> confidence
        is Shape.Zone -> confidence
        is Shape.Withheld -> 0f
    }

    /** The radius to publish [this] with — non-null only for a zone spot. */
    fun Shape.publishRadiusMeters(): Float? = (this as? Shape.Zone)?.radiusMeters

    /** Whether [this] lets a community spot out at all. */
    fun Shape.publishes(): Boolean = this !is Shape.Withheld
}
