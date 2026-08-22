package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * Why a session that timed out unattended ended the way it did — the diagnostics vocabulary, kept
 * verbatim so field traces stay comparable across builds. [DET-WALK-ENTERED-ANCHOR-ZONE-001]
 *
 * [nudgeSource] and [sessionOutcome] are derived from [key]; [decisionOutcome] is spelled out
 * because two of the historical labels do NOT follow the pattern (`UNATTENDED_UNPINNED_NUDGE` and
 * `UNATTENDED_WALK_ENTERED_NUDGE` drop the `_ANCHOR`). Renaming them would silently break every
 * saved trace and every memory note that quotes them.
 */
enum class UnattendedSaveReason(val key: String, val decisionOutcome: String) {
    NO_DRIVE("no_drive", "UNATTENDED_NO_DRIVE_NUDGE"),
    NO_DRIVE_EGRESS("no_drive_egress", "UNATTENDED_NO_DRIVE_EGRESS"),
    UNPINNED_ANCHOR("unpinned_anchor", "UNATTENDED_UNPINNED_NUDGE"),
    EGRESS_MISMATCH("egress_mismatch", "UNATTENDED_EGRESS_MISMATCH_NUDGE"),
    GAP_ANCHOR("gap_anchor", "UNATTENDED_GAP_ANCHOR_NUDGE"),
    WALK_ENTERED_ANCHOR("walk_entered_anchor", "UNATTENDED_WALK_ENTERED_NUDGE"),
    VEHICULAR_EGRESS("vehicular_egress", "UNATTENDED_VEHICULAR_EGRESS_NUDGE"),
    HUMAN_POWERED("human_powered", "UNATTENDED_HUMAN_POWERED_NUDGE"),
    ;

    val nudgeSource: String get() = "unattended_$key"
    val abortedOutcome: String get() = "aborted_unattended_$key"
}

/**
 * What the unattended timeout should do with the session. [DET-WALK-ENTERED-ANCHOR-ZONE-001]
 *
 * - [SaveExact]  — the anchor is trusted: pin the point, `unattended_timeout` provenance.
 * - [SaveZone]   — the anchor is doubtful but the doubt is BOUNDED: save an approximate area
 *                  covering it. The park is kept; only its precision degrades.
 * - [Ask]        — the error is unbounded (or nothing was measured): nudge the user, no pin.
 */
sealed interface UnattendedParkingSave {
    data object SaveExact : UnattendedParkingSave
    data class SaveZone(
        val reason: UnattendedSaveReason,
        val center: GpsPoint,
        val doubtMeters: Double,
    ) : UnattendedParkingSave

    /**
     * @param distanceMeters Optional measurement worth stamping on the trace. [DET-FROZEN-COUNTER-001]
     *   stamps HOW FAR the position outran the walk budget on the vehicular-egress ask — the number
     *   that used to live only in logcat.
     */
    data class Ask(
        val reason: UnattendedSaveReason,
        val distanceMeters: Double? = null,
    ) : UnattendedParkingSave
}

/** Everything the verdict depends on, as plain values — replayable from a recorded trace. */
data class UnattendedSaveInput(
    /** Drive-proof-gated session peak (`ParkingDetectionState.maxSpeedMps`). */
    val maxSpeedMps: Float,
    /** Raw (un-corroborated) session peak — a vehicular HINT, never a proof on its own. */
    val pendingMaxSpeedMps: Float,
    /**
     * [DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001] How many fixes this session saw at real driving speed
     * with credible accuracy. [pendingMaxSpeedMps] is a PEAK — one sample — and a receiver
     * converging out of a cold start emits exactly one: field 2026-08-16 23:52 (Oppo) reported
     * 42 km/h at acc 11,5 m on the third fix with the user on foot for the entire session, and that
     * one sample was enough to buy an approximate zone here after every confirm path had correctly
     * refused. A count separates it from a drive the look-back window merely failed to corroborate,
     * which is the case [DET-NODRIVE-ZONE-001] exists for.
     */
    val credibleDrivingFixes: Int,
    /** `bestStopLocation`: where the car is believed to rest. */
    val anchor: GpsPoint?,
    /** The fix being processed when the response timeout fired. */
    val currentFix: GpsPoint,
    /** Where the egress walk was BORN (`egressOriginFix`). */
    val egressOriginFix: GpsPoint?,
    val stepCount: Int,
    val sessionSawSteps: Boolean,
    val vehicleExitConfirmed: Boolean,
    /** LOCKED (egress steps) or FROZEN (matured end-of-drive stop). */
    val anchorPinned: Boolean,
    /**
     * [DET-GAP-ANCHOR-ZONE-001] Size of the GPS hole the anchor's stop opened through, in ms, or
     * `0L` when the stop was witnessed normally. The MAGNITUDE, not the fact: a hole has a duration,
     * and the phone can only have covered the car→anchor offset ON FOOT inside it, so the duration
     * bounds that offset. The predecessor of this field was a Boolean, which could not tell a 45-s
     * hole from an hour and therefore treated every gap-born anchor as unboundable — losing the
     * Redmi's fully measured 25-min drive on field 2026-08-17 (session `1786970028118`).
     */
    val anchorGapMs: Long,
    /** The anchor was captured at a stop walk-band fixes led into. */
    val anchorWalkEntered: Boolean,
    /** Step events counted during that walk-in — a bound on the offset when the counter was ALIVE. */
    val anchorStepEventsAtCapture: Int,
    /**
     * [DET-WALK-ENTERED-ANCHOR-ZONE-001] MEASURED bound on the same offset: metres between the
     * first fix of the walk-band run that led into the capture and the anchor itself. Available
     * whether or not the step counter said a word — which is the whole point, because the counter
     * being mute is exactly the case that used to lose the park.
     */
    val anchorWalkInSpanMeters: Double,
    /** `isEgressBornAtAnchor` — false when the egress was born away from the anchor. */
    val egressBornAtAnchor: Boolean,
    /** `egressExceedsWalkReach` — the position OUTRAN the steps: a vehicle covered that ground. */
    val egressExceedsWalkReach: Boolean,
    /**
     * [DET-CAR-REST-CLOCK-001] How long the CAR has provably been at rest: ms since the PINNED
     * anchor's stop opened (`now - anchorCapturedAtStop`), `0` when no pinned anchor exists. The
     * phone's own stop tracker is NOT a witness of the car after egress — it follows the walker,
     * and a single walking-band Doppler whisper resets it with no accuracy gate. Field 2026-08-18
     * ~20:00 (Calle Góndola), Redmi session `1787075310656`: 26,2 min of measured driving, then 15
     * minutes of indoor GPS noise (phantom 2–10 km/h at 100–266 m) kept that tracker under 15 s
     * while the car rested at the anchor the whole time, and the licence below read the wrong
     * clock — third home-arrival FN in three days, each one layer deeper in the same well. A
     * pinned anchor is the honest witness by construction: its stop was opened by the car coming
     * to rest, and only re-measured real driving clears it.
     */
    val anchorRestMs: Long,
    /**
     * [DET-BIKE-NOT-A-CAR-001] The movement this session measured was made under HUMAN power (see
     * `domain/detection/isHumanPoweredRide`). Field 2026-08-16 11:08Z (Samsung): a 59-minute bike
     * ride to Los Toruños landed HERE, not on any auto-confirm path — `unattended_zone_unpinned_
     * anchor`, 4,8 km from a Mercedes that never moved. Vetoing only the confirm paths would have
     * left the phantom exactly where it was.
     */
    val humanPoweredRide: Boolean = false,
)

/**
 * The unattended-timeout verdict, extracted from `CoordinatorParkingDetector`'s response-timeout
 * branch so the seven-way precedence lives in one testable place. [DET-WALK-ENTERED-ANCHOR-ZONE-001]
 *
 * **The invariant it now enforces.** Once the session has PROVEN driving and the car has come to a
 * sustained rest, a parking exists — that much is no longer in question. What the anchor taints
 * decide is the SHAPE of what we record (exact point → bounded area), never whether we record
 * anything at all. Losing a measured park is only acceptable when the error is *unbounded*: an
 * anchor entered through a GPS hole (the car may have driven arbitrarily far inside it) or a
 * displacement that outran the steps (the car provably left).
 *
 * **Why this exists.** Field 2026-08-16 22:23Z, Redmi session `1786918991116`: 25,6 min and 96,7
 * km/h of fully measured driving, 47 driving fixes, home reached — and zero pins. The final slow
 * parking manoeuvre spent the walk-fix budget, which tainted the anchor as walk-entered; the same
 * mute step counter that made the taint unavoidable *also* gated the honest-zone fallback
 * (`anchorSawStepsAtCapture`), so the one remedy for the doubt was closed by the doubt itself. The
 * doubt was bounded all along — by the GPS positions of the very fixes that raised it.
 *
 * Asymmetric failure is preserved where it belongs: a session that never measured driving still
 * ends in a nudge, and both unbounded-error branches still refuse to pin.
 */
class EvaluateUnattendedParkingSaveUseCase(private val config: ParkingDetectionConfig) {

    operator fun invoke(input: UnattendedSaveInput): UnattendedParkingSave {
        // [DET-BIKE-NOT-A-CAR-001] First, because it is not a doubt about the anchor — it is
        // evidence that the CAR never moved. Nothing may be recorded: no pin, no area. The old pin
        // is still true and must stand. Every kinematic clause below would happily certify a
        // bicycle, which is exactly how a beach path 4,8 km from the car became a parking.
        if (input.humanPoweredRide) return UnattendedParkingSave.Ask(UnattendedSaveReason.HUMAN_POWERED)

        val anchor = input.anchor
        val anchorToCurrentMeters = anchor?.let {
            haversineMeters(it.latitude, it.longitude, input.currentFix.latitude, input.currentFix.longitude)
        } ?: 0.0
        val steppedWalkBound = input.stepCount * config.anchorStrideMeters.toDouble()

        // [DET-AR-FIRST-001 F3] A pin needs MEASURED in-session driving or an explicit user answer.
        // Seeded evidence is authority to RELEASE the old spot, never to PLACE a new one: a session
        // armed after the trip ended follows the PEDESTRIAN, and its anchor is wherever the body
        // stopped (field 2026-07-10, Redmi: a pin in the user's living room).
        val measuredDriving = input.maxSpeedMps >= config.minimumTripSpeedMps
        if (!measuredDriving) {
            // [DET-NODRIVE-ZONE-001] An EXIT delivered kilometres late births the session AT the
            // destination: the driving is already over and the track can never corroborate it. A
            // LIVE counter with egress-scale steps, a real displacement, and an in-session
            // vehicular signal together separate that real park from the indoor mirage.
            val liveEgress = input.sessionSawSteps &&
                input.stepCount >= config.anchorLockEgressSteps &&
                anchorToCurrentMeters >= config.minEgressDisplacementMeters
            // [DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001] An AR vehicle-exit is external evidence that a
            // vehicle was involved. A raw speed PEAK is not — on its own it is one sample, and one
            // sample is exactly what a cold-start Doppler spike produces. A drive this branch is
            // meant to rescue still emits a run of them.
            val vehicularSignal = input.vehicleExitConfirmed ||
                (input.pendingMaxSpeedMps >= config.minimumTripSpeedMps &&
                    input.credibleDrivingFixes >= config.rawDriveSignalMinFixes)
            return if (anchor != null && liveEgress && vehicularSignal) {
                UnattendedParkingSave.SaveZone(
                    reason = UnattendedSaveReason.NO_DRIVE_EGRESS,
                    center = anchor,
                    doubtMeters = maxOf(anchorToCurrentMeters, steppedWalkBound),
                )
            } else {
                UnattendedParkingSave.Ask(UnattendedSaveReason.NO_DRIVE)
            }
        }

        // ── From here on the session PROVED driving. A parking exists. ────────────────────────

        // [DET-ANCHOR-FREEZE-001] An unattended save may only trust a PINNED anchor as an exact
        // point; an unpinned one is wherever the user's body last stood (field 2026-07-11, Redmi:
        // the walk home dragged it to the front door). [DET-FROZEN-COUNTER-001] A zone is only
        // honest when the doubt is BOUNDABLE, and here only a LIVE counter can bound it: an
        // unpinned anchor was never witnessed at rest, so it travels WITH the walker and the GPS
        // run that led into it says nothing about how far the car is. A mute counter therefore
        // keeps the nudge-only exit (regression: a 260 m mute walk would centre a 60 m zone at the
        // front door). Deliberately NOT relaxed by [DET-WALK-ENTERED-ANCHOR-ZONE-001].
        if (!input.anchorPinned) {
            return zoneOrAsk(
                reason = UnattendedSaveReason.UNPINNED_ANCHOR,
                center = anchor,
                doubtMeters = maxOf(anchorToCurrentMeters, steppedWalkBound),
                bounded = input.sessionSawSteps,
            )
        }

        // [DET-ANCHOR-EGRESS-001] A pinned anchor whose egress was born somewhere else is an
        // intermediate-stop anchor (field 2026-07-15: frozen at a traffic light 1.11 km before the
        // real park). WHO centers the zone follows counter liveness: a LIVE counter means the walk
        // provably started at the birth, so that is the car; a MUTE counter makes the "birth" a
        // kinematic guess along the walker's trail, so the FROZEN anchor is the better centre.
        // Either way the radius covers the OTHER candidate, so the truth stays inside.
        if (!input.egressBornAtAnchor) {
            val birth = input.egressOriginFix
            val center = if (input.sessionSawSteps) birth ?: anchor else anchor ?: birth
            val birthToAnchorMeters = if (birth != null && anchor != null) {
                haversineMeters(birth.latitude, birth.longitude, anchor.latitude, anchor.longitude)
            } else {
                0.0
            }
            return zoneOrAsk(
                reason = UnattendedSaveReason.EGRESS_MISMATCH,
                center = center,
                doubtMeters = birthToAnchorMeters,
                bounded = true,
            )
        }

        // [DET-CONFIRM-FRESHNESS-001] Evidence must still be TRUE at pin time: during the prompt
        // window the car may have driven off (a pick-up / errand stop). If the current fix sits
        // beyond what the counted steps could walk from the anchor, a VEHICLE covered that ground
        // since the decision — the honest exit is the nudge, never a pin at a spot the car
        // provably left. This one is not a precision doubt; it is evidence of ABSENCE.
        // [DET-GAP-ANCHOR-ZONE-001] Which is why it is tested HERE, above every branch that draws a
        // zone, instead of after them: no radius is honest about a place the car has left, so the
        // rule outranks all three precision doubts rather than being restated inside each. It used
        // to sit last, where the walk-entered zone already shadowed it.
        if (input.egressExceedsWalkReach) {
            return UnattendedParkingSave.Ask(
                reason = UnattendedSaveReason.VEHICULAR_EGRESS,
                distanceMeters = anchorToCurrentMeters,
            )
        }

        // [DET-GAP-ANCHOR-001] A GAP-ENTERED anchor was never seen coming to rest: the stream died
        // at driving speed and the anchor is merely the first fix on the far side of the hole, so it
        // may be a drive-past point (field 2026-07-29, Av. Sanlúcar: a 100-s MIUI hole ended in one
        // speed-0 fix mid-route and the pin landed 315 m before the real park).
        // [DET-GAP-ANCHOR-ZONE-001] The taint stands; losing the park over it does not. Two facts
        // the old Boolean threw away bound this:
        //  · The hole has a DURATION. Whatever the car did inside it, the phone can only have
        //    covered the car→anchor offset ON FOOT, so that offset is at most the hole times
        //    pedestrian pace. Deliberately conservative — it assumes the whole hole was spent
        //    walking, which also assumes the car braked from driving speed instantly.
        //  · The drive-past hypothesis dies on a SUSTAINED REST. A car passing a point does not
        //    then rest there for minutes; Av. Sanlúcar resumed driving, which is exactly what this
        //    condition separates. Rest also implies no re-measured driving, since real driving
        //    clears the anchor and resets the stop clock.
        // Field 2026-08-17, Redmi session `1786970028118`: a 126-s hole at 42 km/h, then 14,7 min
        // at rest 40 m from where the Oppo pinned the same trip — and the park was thrown away.
        if (input.anchorGapMs > 0L) {
            // [DET-CAR-REST-CLOCK-001] The rest is the ANCHOR's, not the phone's: the drive-past
            // hypothesis is about the CAR resting, and the pedestrian wandering off (or indoor
            // noise impersonating them) says nothing against it.
            val walkableInsideHole = io.apptolast.paparcar.domain.detection.walkableInsideGapMeters(
                input.anchorGapMs, config.maxPedestrianSpeedMps,
            )
            val sustainedStop = input.anchorRestMs >= config.sustainedStopForSaveMs
            return zoneOrAsk(
                reason = UnattendedSaveReason.GAP_ANCHOR,
                center = anchor,
                doubtMeters = walkableInsideHole,
                bounded = sustainedStop,
            )
        }

        // [DET-CREDIBLE-DRIVE-001] A WALK-ENTERED anchor is the pedestrian's standing spot, not the
        // car's rest — pinning it exactly plants the pin wherever the user walked to (field
        // 2026-07-15, Camelias: the house door, 37 m from the car). The taint stands.
        // [DET-WALK-ENTERED-ANCHOR-ZONE-001] What no longer stands is losing the park over it. The
        // walked-in offset is bounded by whichever witness spoke: counted steps (live counter) or
        // the GPS span of the walk-band run itself (always). The old gate demanded the FORMER and
        // therefore lost every park whose device had a mute counter — the Redmi's 97 km/h drive.
        if (input.anchorWalkEntered) {
            val steppedBound = input.anchorStepEventsAtCapture * config.anchorStrideMeters.toDouble()
            val doubt = maxOf(steppedBound, input.anchorWalkInSpanMeters)
            // The licence the user granted on 2026-08-17: proven driving plus a sustained rest
            // means "it is obvious we moved by car and then parked". From there a bounded doubt may
            // only cost precision. Unlike the unpinned case above, this anchor WAS witnessed at
            // rest — the taint is only about the walk-band run that led into the capture, and that
            // run has a measured origin whether or not the step counter ever spoke.
            // [DET-CAR-REST-CLOCK-001] The rest that earns the licence is the CAR's, witnessed by
            // the anchor's own stop — the phone's tracker follows the walker home and reads ~0.
            val sustainedStop = input.anchorRestMs >= config.sustainedStopForSaveMs
            return zoneOrAsk(
                reason = UnattendedSaveReason.WALK_ENTERED_ANCHOR,
                center = anchor,
                doubtMeters = doubt,
                bounded = doubt > 0.0 && sustainedStop,
            )
        }

        return UnattendedParkingSave.SaveExact
    }

    /**
     * [DET-WALK-ENTERED-ANCHOR-ZONE-001] The single place the "degrade precision, never lose the
     * park" rule is spelled out: an area needs somewhere to centre it and a doubt that can be
     * bounded. Anything missing falls back to asking.
     */
    private fun zoneOrAsk(
        reason: UnattendedSaveReason,
        center: GpsPoint?,
        doubtMeters: Double,
        bounded: Boolean,
    ): UnattendedParkingSave =
        if (center != null && bounded) {
            UnattendedParkingSave.SaveZone(reason, center, doubtMeters)
        } else {
            UnattendedParkingSave.Ask(reason)
        }

}
