package com.rndeveloper.paparcar.domain.spot

/**
 * What SHAPE the community spot freed by a departing session may take.
 * [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]
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
 * - **Zone above the ceiling** → [Shape.Withheld], conservative silence: sending a seeker to scan
 *   a 250 m circle is the same credibility damage as a ghost pin, on foot. The user's own history
 *   keeps the session either way — only the community publication is gated.
 *
 * The witnessed departure that triggers publication validates the AREA claim (the car measurably
 * drove out of the fence centred on the zone), never the centre coordinate — which is why the zone
 * spot is honest and the exact pin was not.
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

    /** The shape a freed spot is allowed to be published with. */
    sealed interface Shape {
        /** Exact session → exact spot, the session's own confidence. */
        data class Exact(val confidence: Float) : Shape

        /** Bounded zone session → zone spot: area + capped confidence + provisional lifetime. */
        data class Zone(val radiusMeters: Float, val confidence: Float) : Shape

        /** Zone too wide to be worth a stranger's walk → nothing is published. */
        data object Withheld : Shape
    }

    /**
     * The shape for a session with the given doubt radius and reliability.
     *
     * @param zoneRadiusMeters [com.rndeveloper.paparcar.domain.model.UserParking.zoneRadiusMeters]
     *   — null for an exact pin.
     * @param detectionReliability the session's stamped reliability; null (legacy callers with no
     *   session) reads as full confidence, exactly as the publish paths defaulted before.
     */
    fun shapeFor(zoneRadiusMeters: Float?, detectionReliability: Float?): Shape {
        val confidence = detectionReliability ?: 1f
        return when {
            zoneRadiusMeters == null -> Shape.Exact(confidence)
            zoneRadiusMeters <= ZONE_PUBLISH_MAX_RADIUS_METERS ->
                Shape.Zone(zoneRadiusMeters, minOf(confidence, ZONE_MAX_CONFIDENCE))
            else -> Shape.Withheld
        }
    }
}
