package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig

/**
 * [DET-DETECTION-PATH-IS-A-TYPE-001] **Which confirmation path placed this pin** — as a type that
 * carries its own consequences, instead of a string that three separate sites re-interpret.
 *
 * ## What a string was costing
 *
 * `detectionPath` is the answer to *"which trigger put this parking"*, and the whole field-forensics
 * method rests on it. It was a bare `String`, and every question asked of it was answered by
 * spelling it out again somewhere else:
 *
 *  - **Who detected it** was decided by `startsWith("bt")` — a PREFIX. Any future path beginning
 *    with those two letters would be filed as the Bluetooth strategy, and the fall-through claimed
 *    `Assisted` for anything unrecognised **even though `Unknown` exists**: a new path was
 *    attributed to the Coordinator by default, in the very field the user opens to ask who put a
 *    wrong pin there.
 *  - **How reliable it is** was decided by `pathLabel == "kinematic+egress"`, with everything else
 *    falling to the maximum. A path added tomorrow would be born stamped 0.90 without its author
 *    ever choosing that.
 *  - **What the label even is** drifted: production emits `"vehicleExit+window+egress"`, while
 *    `UserParking`'s own KDoc, the repository fake and the preview data all say `"vehicle-exit"` —
 *    a provenance the app has never once written. Nothing could catch that, because there was no
 *    place where the set of paths was stated.
 *
 * ## The rule this type enforces
 *
 * **A new path does not compile until its author answers**: which strategy it belongs to, and what
 * it may claim about reliability. Same shape as `SessionOutcome` and `ArmEvidence`, which cured the
 * same defect in their own corners.
 *
 * The [label] stays the wire format — it is persisted in Room and in Firestore and read by history —
 * so this type is the single place that OWNS those strings rather than a migration of them.
 */
sealed interface DetectionPath {

    /** The persisted string. One definition, instead of a literal at each producer. */
    val label: String

    /** Which strategy the user is told placed this pin. [ParkingDetectionSource] */
    val source: ParkingDetectionSource

    /**
     * What this path may stamp when it confirms live, or `null` when it is not a live-confirm path
     * (the clock, reconciliation and user-placed paths carry a reliability decided at their own
     * site, and answering here would be a second opinion).
     */
    fun confirmReliability(config: ParkingDetectionConfig): Float?

    /**
     * [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] **May the app withdraw a pin this path placed,
     * on its own, when its own measurements go on to refute it?**
     *
     * The third question this type owns, and the one that decides whether a parking the app has
     * already concluded never happened stays in the user's history. It is `true` for exactly one
     * path today, and the reason is not "that is the one that burned us" — it is that
     * [SafetyNetBackfill] is the only pin placed with **no live session behind it**: a
     * reconstruction from stale evidence, which is precisely the class of claim a later measurement
     * can overturn. Every other path had a session that watched something.
     *
     * ⛔ **`false` is not a synonym of "trustworthy".** [UnattendedZone] is a guess bounded by its
     * own doubt and [BtTimeout] lost its corroboration, and both still answer `false`: they were
     * placed by a session that measured a drive and a rest, so a later departure from them is an
     * ordinary end, not a refutation. And the user-placed paths answer `false` for a different
     * reason again — nothing the app measures outranks the user's own hand
     * ([DET-ASSERTION-OUTRANKS-INFERENCE-001]). The user withdrawing their OWN pin does not come
     * through here at all; a revert is an instruction, not a verdict.
     */
    val mayBeWithdrawnByTheApp: Boolean

    // ── Coordinator live-confirm paths ──────────────────────────────────────────────────────────

    /** Pedestrian steps plus egress displacement — the path that plants the most pins. */
    data object StepsEgress : DetectionPath {
        override val label = "steps+egress"
        override val source = ParkingDetectionSource.Assisted
        override fun confirmReliability(config: ParkingDetectionConfig) = config.reliabilityVehicleExit
        /** `false` - a live session measured the walk away from the car. */
        override val mayBeWithdrawnByTheApp = false
    }

    /** Pedestrian-band GPS fixes walking away from a frozen anchor — the mute-counter peer. */
    data object KinematicEgress : DetectionPath {
        override val label = "kinematic+egress"
        override val source = ParkingDetectionSource.Assisted
        override fun confirmReliability(config: ParkingDetectionConfig) = config.reliabilityKinematicEgress
        /** `false` - a live session measured the walk away from the car. */
        override val mayBeWithdrawnByTheApp = false
    }

    /** An AR vehicle EXIT plus the observation window plus egress displacement. */
    data object VehicleExitWindow : DetectionPath {
        override val label = "vehicleExit+window+egress"
        override val source = ParkingDetectionSource.Assisted
        override fun confirmReliability(config: ParkingDetectionConfig) = config.reliabilityVehicleExit
        /** `false` - a live session measured the exit and the egress. */
        override val mayBeWithdrawnByTheApp = false
    }

    // ── Clock and reconciliation paths ──────────────────────────────────────────────────────────

    /** The unattended response timeout saved an exact pin. */
    data object UnattendedTimeout : DetectionPath {
        override val label = "unattended_timeout"
        override val source = ParkingDetectionSource.Assisted
        override fun confirmReliability(config: ParkingDetectionConfig): Float? = null
        /** `false` - the session measured a drive and a rest; the clock only ended the wait. */
        override val mayBeWithdrawnByTheApp = false
    }

    /**
     * The unattended timeout saved an AREA, and the [reasonKey] says which doubt bounded it
     * (`gap_anchor`, `walk_entered_anchor`, …).
     *
     * ⚠️ This is the one family whose label is COMPOSED, and it is the reason this is a sealed
     * interface rather than an enum. `DetectionEffectExecutor` builds it as
     * `"unattended_zone_${reason.key}"`, so the set is open by construction — but it is open on a
     * rule this type OWNS, which is the opposite of guessing a prefix. It is also the only prefix
     * match in [ofLabel], and it has its own test.
     */
    data class UnattendedZone(val reasonKey: String) : DetectionPath {
        override val label = LABEL_PREFIX + reasonKey
        override val source = ParkingDetectionSource.Assisted
        override fun confirmReliability(config: ParkingDetectionConfig): Float? = null
        /** `false` - same as the exact one - the doubt is already in the radius, not in whether it happened. */
        override val mayBeWithdrawnByTheApp = false

        companion object { const val LABEL_PREFIX = "unattended_zone_" }
    }

    /** The 15-minute safety net reconstructed a pin with no live session. */
    data object SafetyNetBackfill : DetectionPath {
        override val label = "safety_net_backfill"
        override val source = ParkingDetectionSource.Assisted
        override fun confirmReliability(config: ParkingDetectionConfig): Float? = null
        /** `true` - reconstructed with NO live session, from evidence that may be hours stale. */
        override val mayBeWithdrawnByTheApp = true
    }

    /** The honest close stood behind a POINT: the fix was sharp enough that a pin says more than an
     *  area would, so it carries no radius. [DET-HONEST-CLOSE-001] */
    data object ClosedApproximatePin : DetectionPath {
        override val label = "closed_approximate_pin"
        override val source = ParkingDetectionSource.Assisted
        override fun confirmReliability(config: ParkingDetectionConfig): Float? = null
        /** `false` - the honest close drew what its own session witnessed. */
        override val mayBeWithdrawnByTheApp = false
    }

    /**
     * The honest close drew the AREA it was willing to stand behind — the sibling of
     * [ClosedApproximatePin], and the one that was missing.
     *
     * `RunHonestCloseUseCase` has emitted this label since it gained the zone branch, but the type
     * never existed, so [ofLabel] returned null for it and the session carrying the MOST doubt of
     * any — a real area, drawn with a radius — was the one attributed to `Unknown` in the very
     * screen a user opens to ask who placed a pin. Its exact-pin sibling resolved fine, which is
     * what kept the hole quiet: the family looked covered. Field 2026-08-30, Oppo, 23:48:
     * `closed_approximate_zone` with a 196.5 m radius, provenance unknown.
     */
    data object ClosedApproximateZone : DetectionPath {
        override val label = "closed_approximate_zone"
        override val source = ParkingDetectionSource.Assisted
        override fun confirmReliability(config: ParkingDetectionConfig): Float? = null

        /** Same answer as [ClosedApproximatePin], for the same reason: a session watched a drive and
         *  a rest and then drew what it was willing to stand behind. A later departure from it is an
         *  ordinary end, not a refutation — the area IS the doubt, already declared. */
        override val mayBeWithdrawnByTheApp = false
    }

    // ── Bluetooth strategy ──────────────────────────────────────────────────────────────────────

    /** BT disconnect with the walk-away corroborated. */
    data object Bt : DetectionPath {
        override val label = "bt"
        override val source = ParkingDetectionSource.Bluetooth
        override fun confirmReliability(config: ParkingDetectionConfig): Float? = null
        /** `false` - the disconnect and the displacement were both measured. */
        override val mayBeWithdrawnByTheApp = false
    }

    /** BT disconnect whose walk watch expired. Same strategy, less corroboration. */
    data object BtTimeout : DetectionPath {
        override val label = "bt_timeout"
        override val source = ParkingDetectionSource.Bluetooth
        override fun confirmReliability(config: ParkingDetectionConfig): Float? = null
        /** `false` - the disconnect was measured; only its corroboration expired. */
        override val mayBeWithdrawnByTheApp = false
    }

    // ── Placed by the user ──────────────────────────────────────────────────────────────────────

    /** The user answered a detection prompt. Keeps `AUTO_DETECTED` so the freed spot publishes with
     *  the auto TTL, but the hand that placed it was theirs. [DET-NUDGE-PIN-PROVENANCE-001] */
    data object UserAnswered : DetectionPath {
        override val label = "user"
        override val source = ParkingDetectionSource.Manual
        override fun confirmReliability(config: ParkingDetectionConfig): Float? = null
        /** `false` - the hand was the user's. */
        override val mayBeWithdrawnByTheApp = false
    }

    /** The user placed the pin by hand. */
    data object ManualPin : DetectionPath {
        override val label = "manual"
        override val source = ParkingDetectionSource.Manual
        override fun confirmReliability(config: ParkingDetectionConfig): Float? = null
        /** `false` - the hand was the user's. */
        override val mayBeWithdrawnByTheApp = false
    }

    /**
     * The user DRAGGED an existing pin to where the car really is.
     *
     * Distinct from [ManualPin] on purpose: that one was born by hand, this one was born by the
     * detector and then corrected. Keeping them apart is the whole point of the field — a trace
     * that reads `manual` cannot tell "the app never saw this park" from "the app saw it and got it
     * wrong", and only the second is a detection failure worth chasing.
     *
     * Before this existed the drag changed the coordinates and left the path untouched, so a pin the
     * user had placed still claimed to be `unattended_zone_gap_anchor` — the pin lied about who put
     * it there, which is exactly what [DET-PIN-PROVENANCE-001] exists to prevent. Field 2026-08-30,
     * Redmi 19:32:44.
     */
    data object UserMovedPin : DetectionPath {
        override val label = "user_moved"
        override val source = ParkingDetectionSource.Manual
        override fun confirmReliability(config: ParkingDetectionConfig): Float? = null

        /** `false`, and emphatically so: this pin is where the user's own hand put it, so nothing the
         *  app measures afterwards outranks it [DET-ASSERTION-OUTRANKS-INFERENCE-001]. A drag is the
         *  user CORRECTING the app — letting the app then withdraw the correction would invert the
         *  whole relationship. */
        override val mayBeWithdrawnByTheApp = false
    }

    /** The user answered a nudge notification. */
    data object Nudge : DetectionPath {
        override val label = "nudge"
        override val source = ParkingDetectionSource.Manual
        override fun confirmReliability(config: ParkingDetectionConfig): Float? = null
        /** `false` - the hand was the user's. */
        override val mayBeWithdrawnByTheApp = false
    }

    companion object {

        /** Every path with a fixed label. [UnattendedZone] is absent on purpose — it composes. */
        val fixedLabelPaths: List<DetectionPath> = listOf(
            StepsEgress, KinematicEgress, VehicleExitWindow,
            UnattendedTimeout, SafetyNetBackfill,
            ClosedApproximatePin, ClosedApproximateZone,
            Bt, BtTimeout,
            UserAnswered, ManualPin, UserMovedPin, Nudge,
        )

        /**
         * The persisted string back to its type, or **null when nothing recognises it**.
         *
         * ⛔ Fails CLOSED, and that is the whole point. The predicate this replaces ended in
         * `else -> Assisted`: an unknown path was attributed to the Coordinator strategy, in the
         * exact field a user opens to ask who placed a pin they did not expect. A null here reaches
         * the user as `Unknown`, which claims nothing — asymmetric failure applies to what we tell
         * the user too.
         */
        fun ofLabel(label: String?): DetectionPath? {
            if (label.isNullOrBlank()) return null
            fixedLabelPaths.firstOrNull { it.label == label }?.let { return it }
            return if (label.startsWith(UnattendedZone.LABEL_PREFIX)) {
                UnattendedZone(label.removePrefix(UnattendedZone.LABEL_PREFIX))
            } else {
                null
            }
        }
    }
}
