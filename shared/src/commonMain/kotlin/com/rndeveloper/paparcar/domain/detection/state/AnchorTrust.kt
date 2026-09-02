package com.rndeveloper.paparcar.domain.detection.state

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * What was true about the WALK-IN at the instant the anchor bound to its stop. [DET-CREDIBLE-DRIVE-001]
 *
 * Five witnesses of one event, and that is exactly why they are one value: they must be sealed
 * together or they describe different captures. Before P2.5 each carried its own copy of the rebind
 * condition — the same `anchorStopOfRecord != capturedAtStop` written out **five times** — so a sixth
 * witness had to REMEMBER to repeat it, and nothing failed if it did not.
 *
 * ⚠️ **The clearing side is asymmetric, and it is PRESERVED rather than fixed.** When the anchor
 * goes away, [walkInSpanMeters] and [gapMs] are reset and [walkFixes], [stepEvents] and [sawSteps]
 * are **not**. That looks like an oversight — five fields cleared by hand in a forty-field `copy` —
 * and it is not unobservable: `isAnchorWalkEntered` reads the three survivors WITHOUT requiring an
 * anchor, and both of its callers can be reached with none (the unattended timeout, above all). So
 * zeroing them would flip a walk-entered verdict to "clean" in exactly the case where the anchor is
 * missing, which is the case the asymmetric-failure doctrine says to treat with most suspicion.
 *
 * A move does not get to make that call. [clearedWithAnchor] reproduces today's behaviour exactly
 * and names it, so the question can be asked on its own with a replay behind it.
 *
 * @property walkFixes How much WALKING led into the stop the anchor belongs to. Above the freeze
 *   budget the anchor is WALK-ENTERED: the pedestrian's standing spot, not the car's rest (field
 *   2026-07-15, Camelias-Oppo: the walk back from a reposition ended frozen at the house door 37 m
 *   from the car).
 * @property stepEvents Step EVENTS the walk-in produced. A person walking into a stop fires them on
 *   the way; a car's final parking maneuver fires none. [DET-CONFIRM-FRESHNESS-001]
 * @property sawSteps Whether the counter had already proven itself ALIVE by then — a taint without
 *   step corroboration only means something when the counter COULD have testified. Snapshot at
 *   capture, so a counter that wakes late cannot retroactively soften a taint earned in silence.
 * @property walkInSpanMeters Metres between the walk run's first fix and the anchor: a MEASURED
 *   bound on how far the walk-in could have dragged it. [stepEvents] bounds the same offset but only
 *   speaks when the counter is alive; this is GPS geometry and always does — which is what let the
 *   unattended fallback stop losing every park on a mute-counter device (field 2026-08-16, Redmi:
 *   25.6 min at 96.7 km/h, home reached, zero pins). [DET-WALK-ENTERED-ANCHOR-ZONE-001]
 * @property gapMs Size of the GPS hole the anchor's stop was entered through, or 0 when it was
 *   witnessed normally. The MAGNITUDE rather than the fact, because the hole's duration is what
 *   bounds the doubt it creates. [DET-GAP-ANCHOR-001][DET-GAP-ANCHOR-ZONE-001]
 */
data class AnchorCapture(
    val walkFixes: Int = 0,
    val stepEvents: Int = 0,
    val sawSteps: Boolean = false,
    val walkInSpanMeters: Double = 0.0,
    val gapMs: Long = 0L,
) {
    /** Whether the anchor carries the GAP-ENTERED taint — derived from [gapMs] so the fact and its
     *  magnitude can never disagree. */
    val gapEntered: Boolean get() = gapMs > 0L

    /**
     * What survives the anchor going away — **today's behaviour, reproduced deliberately.**
     *
     * The two MEASUREMENTS ([walkInSpanMeters], [gapMs]) die with the anchor they measured; the
     * three witnesses of the walk-in survive, and `isAnchorWalkEntered` can still read them with no
     * anchor in sight. Nothing re-stamps them until the next rebind, so between an anchor's death
     * and the next one's birth a verdict is answered from a previous stop's testimony.
     *
     * Whether that is right is a real question with a real risk on both sides, and it needs a
     * directed replay — not a refactor step. See `docs/backlog/det-state-anchor-trust-001.md`.
     */
    fun clearedWithAnchor(): AnchorCapture = copy(walkInSpanMeters = 0.0, gapMs = 0L)
}

/**
 * The "entered on foot" odometer: how many pedestrian-band fixes since the last resolved CAR
 * movement, and where that run began. [DET-ANCHOR-FREEZE-001][DET-WALK-ENTERED-ANCHOR-ZONE-001]
 */
data class WalkIn(
    val fixesSinceDriving: Int = 0,
    val runOriginFix: GpsPoint? = null,
)

/**
 * Where the egress walk was BORN: the fix at which the first egress evidence appeared with an
 * anchor set. A genuine egress is born AT the car, so a birth far from the anchor proves the anchor
 * belongs to an intermediate stop — field 2026-07-15, frozen at a traffic light 1.11 km before the
 * real park, with the destination walk confirming kinematic+egress AT the light.
 * [DET-ANCHOR-EGRESS-001]
 *
 * @property stepCountAtBirth Steps already counted when [originFix] was recorded — they widen the
 *   allowed birth distance, because the user may have walked a few steps before the first post-pin
 *   fix arrived on a sparse stream.
 */
data class EgressBirth(
    val originFix: GpsPoint,
    val stepCountAtBirth: Int,
)

/**
 * [09 §5] **Where the car is, and how much that claim is worth** — the fifth and largest sub-state.
 *
 * It owns the anchor, the stop it was captured at, every taint on it, the stop-window fixes, the
 * walk-in odometer, the egress birth and the reposition streak.
 *
 * ## The heart of this step: one rebind instead of five
 *
 * [rebind] is the transition the five copied conditions become. Before it, sealing the capture was
 * five independent `if (stopOfRecord != capturedAtStop)` expressions scattered through a forty-field
 * `copy`; a witness added later had to remember to be sealed, and a witness removed left its
 * condition behind. Now the capture either binds as a whole or does not exist.
 *
 * This changes no behaviour whatsoever. What it removes is a class of future bug — the same class
 * `EgressEvidence`'s three reset rules removed one step earlier.
 *
 * ## The boundary
 *
 * The anchor owns itself; **the steps are PRESENTED to it, never copied into it** [07 §2.4]. Every
 * transition below therefore takes the step counters as arguments. `EgressEvidence` receives the
 * anchor's pinned state the same way, which makes the traffic symmetric and the REDUCTION ORDER
 * something that has to be declared — see P2.6.
 *
 * @property anchor The best (lowest-accuracy) fix recorded while the vehicle was stopped: the
 *   parked-car position, and the origin egress displacement is measured from.
 * @property capturedAtStop `stopStartedAt` of the stop during which [anchor] was captured. A LOCKED
 *   anchor may only be refined while still in that same stop — a LATER stop is the pedestrian
 *   standing still, never the car. [ANCHOR-LOCK-001]
 * @property frozenByRest The stop matured past the freeze bar after measured driving, so the car
 *   provably came to rest here. Behaves like a locked anchor WITHOUT needing the step stream — the
 *   guard for hardware whose counter delivers late or never (field 2026-07-11, Redmi: zero steps for
 *   the whole walk home, and the unlocked anchor followed the pedestrian to the front door).
 *   [DET-ANCHOR-FREEZE-001]
 * @property kinematicEgressFixes QUALITY pedestrian-band fixes seen while the anchor is frozen — the
 *   GPS-measured egress walk, the mute-counter peer of the step proof. Survives walk pauses (a
 *   crossing); only a resolved CAR movement resets it. [DET-KINEMATIC-EGRESS-001]
 * @property stopEnteredAfterGapMs Size of the hole the CURRENT stop opened through. Recomputed every
 *   time a stop opens; read at rebind, where it becomes [AnchorCapture.gapMs].
 * @property stopEvidenceSince When the current stop's UNREFUTED stillness run began. Equal to
 *   [stopStartedAt] while the stop's own track never contradicts it; advanced to the refuting fix
 *   whenever [DET-STOP-MUST-BE-STILL-IN-SPACE-001] proves the car was still moving. The stop keeps
 *   its clock — scoring and prompts still read [stopStartedAt] (asking is the cheap side of the
 *   asymmetric-failure doctrine) — but PROOF restarts here: time-maturity and the capture window
 *   measure from this instant, so refuted stillness leaves no inheritance (field 2026-08-28: a stop
 *   refuted 4× matured by TIME mid-route and the pin landed inside the house).
 *   [DET-REFUTED-STILLNESS-CANNOT-MATURE-AN-ANCHOR-001]
 * @property repositionStreak Consecutive fixes that look like a parking maneuver rather than a
 *   drive. [PARKING-001]
 * @property realDriveStreak Consecutive credible fixes at or above trip speed. A PINNED anchor is a
 *   rest this session actually witnessed, and one sample does not overturn a witness — the run must
 *   reach `pinnedAnchorRealDriveFixes` before real driving clears it (field 2026-08-27, Calle del
 *   Vivero). Any fix that is not a real drive breaks it, and every stopped fix resets it, exactly
 *   like [repositionStreak]. [DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001]
 * @property sustainedDepartureStreak Consecutive fixes whose sustained-departure geometry held —
 *   the same invariant one lane over: the DISPLACEMENT verdict used to overturn a pinned anchor on
 *   ONE sample, which [realDriveStreak] had just forbidden for the Doppler lane. Field 2026-08-28
 *   01:11:04 (Redmi): a single fix, already rejected as driving by the accuracy gate
 *   (acc 81.8 > 50), resolved CAR by displacement in the same beat and cleared a frozen anchor +
 *   stop + Notified phase + walk-in odometer. The run must reach `pinnedAnchorRealDriveFixes`
 *   before displacement clears a pinned anchor — a mirage burst is a lone sample by shape, a real
 *   departure keeps re-measuring on the next fix. Reset like the others.
 *   [DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001]
 */
data class AnchorTrust(
    val anchor: GpsPoint? = null,
    val capturedAtStop: Long? = null,
    val capture: AnchorCapture = AnchorCapture(),
    val frozenByRest: Boolean = false,
    val stopStartedAt: Long? = null,
    val stopWindowFixes: List<GpsPoint> = emptyList(),
    val stopEnteredAfterGapMs: Long = 0L,
    val stopEvidenceSince: Long? = null,
    val walkIn: WalkIn = WalkIn(),
    val kinematicEgressFixes: Int = 0,
    val egressBirth: EgressBirth? = null,
    val repositionStreak: Int = 0,
    val realDriveStreak: Int = 0,
    val sustainedDepartureStreak: Int = 0,
) {

    /**
     * [DET-NO-CLOCK-PLANTS-A-PIN-001] The most GPS-accurate fix the stop window actually WITNESSED,
     * or null when the window collected nothing.
     *
     * Distinct from [anchor] on purpose. The anchor is *where the stop was declared to begin*; this
     * is *the best look the session ever got at the place it came to rest*. On a healthy stop the
     * two coincide. On a GAP-ENTERED stop they do not, and the difference is a false positive: field
     * 2026-08-30 01:34, the anchor was the first fix out of a 2 min 16 s hole (acc 25 m) and **not
     * one of the 215 fixes that followed came back within 100 m of it** — while the same stop window
     * held a fix of 11 m accuracy, 157 m away, on top of the car.
     */
    val witnessedRestFix: GpsPoint? get() = stopWindowFixes.minByOrNull { it.accuracy }

    /** The most GPS-accurate fix collected at the moment of stopping, or [fallback]. */
    fun bestFix(fallback: GpsPoint): GpsPoint = witnessedRestFix ?: fallback

    /**
     * How long the CAR has been at rest, measured from the stop the anchor belongs to rather than
     * from the phone's current stop clock: the anchor is what witnessed the rest, so a pedestrian
     * who keeps restarting their own stop clock cannot restart the car's. [DET-CAR-REST-CLOCK-001]
     */
    fun restMsAt(now: Long): Long = capturedAtStop?.let { now - it } ?: 0L

    // ── The stop, and the anchor bound to it ──────────────────────────────────

    /**
     * One STOPPED fix.
     *
     * @param stopStartedAt When the current stop began (the caller keeps the stop clock, because
     *   opening a stop is a decision about the whole fix stream, not about the anchor).
     * @param stopWindowFixes The fixes collected inside the initial-stop window, including this one.
     * @param newAnchor The anchor after this fix — the same instance when nothing re-captured it,
     *   which is what [rebind] tests by IDENTITY.
     * @param stopGapMs The hole the current stop opened through.
     * @param frozen Whether the freeze verdict fires on this fix.
     * @param walkFixesSinceDriving Presented by the walk-in odometer at this instant.
     * @param stepEventsSinceDriving Presented by `EgressEvidence`.
     * @param sensorAlive Presented by `EgressEvidence`.
     */
    @Suppress("LongParameterList")
    fun onStoppedFix(
        stopStartedAt: Long,
        stopWindowFixes: List<GpsPoint>,
        newAnchor: GpsPoint?,
        stopGapMs: Long,
        frozen: Boolean,
        stepEventsSinceDriving: Int,
        sensorAlive: Boolean,
        stopEvidenceSince: Long = stopStartedAt,
    ): AnchorTrust = rebind(
        newAnchor = newAnchor,
        stopStartedAt = stopStartedAt,
        stopGapMs = stopGapMs,
        stepEventsSinceDriving = stepEventsSinceDriving,
        sensorAlive = sensorAlive,
    ).copy(
        stopStartedAt = stopStartedAt,
        stopWindowFixes = stopWindowFixes,
        stopEnteredAfterGapMs = stopGapMs,
        stopEvidenceSince = stopEvidenceSince,
        frozenByRest = frozenByRest || frozen,
        // Reset the reposition counter on every stopped fix. [PARKING-001]
        repositionStreak = 0,
        // …and the sustained-departure run: a stop breaks a departure by definition.
        // [DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001]
        sustainedDepartureStreak = 0,
        // …and the real-drive run with it: a stop breaks any run of driving fixes by definition.
        // [DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001]
        realDriveStreak = 0,
    )

    /**
     * **The five copied conditions, once.** The anchor binds to a stop, and its five witnesses are
     * sealed at that same instant — or none of them are.
     *
     * The test is IDENTITY on the anchor instance: a same-stop accuracy refinement produces a new
     * anchor bound to the SAME stop, and that must keep the original capture (the taints belong to
     * the stop, not to the sharpness of the fix). Only a bind to a DIFFERENT stop re-seals.
     */
    private fun rebind(
        newAnchor: GpsPoint?,
        stopStartedAt: Long,
        stopGapMs: Long,
        stepEventsSinceDriving: Int,
        sensorAlive: Boolean,
    ): AnchorTrust {
        val stopOfRecord = if (newAnchor !== anchor) stopStartedAt else capturedAtStop
        if (stopOfRecord == capturedAtStop) return copy(anchor = newAnchor)
        return copy(
            anchor = newAnchor,
            capturedAtStop = stopOfRecord,
            capture = AnchorCapture(
                walkFixes = walkIn.fixesSinceDriving,
                stepEvents = stepEventsSinceDriving,
                sawSteps = sensorAlive,
                walkInSpanMeters = walkInSpanTo(newAnchor),
                gapMs = stopGapMs,
            ),
        )
    }

    /** How far the walk run's first fix sits from the anchor it led to — 0 when either is missing. */
    private fun walkInSpanTo(newAnchor: GpsPoint?): Double {
        val origin = walkIn.runOriginFix ?: return 0.0
        val target = newAnchor ?: return 0.0
        return haversineMeters(origin.latitude, origin.longitude, target.latitude, target.longitude)
    }

    // ── The stop's own track refutes it ───────────────────────────────────────

    /**
     * [DET-REFUTED-STILLNESS-CANNOT-MATURE-AN-ANCHOR-001] The stop's track just proved the car was
     * still MOVING through the fixes this anchor was captured from — so the anchor is not a rest,
     * and it goes the same way a resolved car movement takes it (anchor, stop-of-record, sealed
     * measurements, egress birth). The caller only invokes this for an UNPINNED anchor captured at
     * the refuted stop itself: a pinned anchor is a rest this session PROVED (only re-measured real
     * driving may move it), and an anchor from an earlier stop is not this stop's claim to revoke.
     *
     * Field 2026-08-28 (Redmi, house FP): the anchor stuck to the stop-opening fix 3.5 km back —
     * its 19.75 m accuracy beat every later fix — through four refutations, then matured by TIME.
     * Disowning at the refutation restarts the best-accuracy contest among fixes the track has not
     * yet contradicted.
     */
    fun disownedByRefutation(): AnchorTrust = copy(
        anchor = null,
        capturedAtStop = null,
        capture = capture.clearedWithAnchor(),
        egressBirth = null,
        // [DET-A-DISOWNED-ANCHOR-TAKES-ITS-WALK-WITH-IT-001] The two the sentence above PROMISED
        // and this copy did not deliver. A resolved car movement clears five things ([onMovingFix]:
        // anchor, stop-of-record, sealed capture, `frozenByRest`, and the kinematic count, which
        // the caller passes as 0); this cleared three, so a disowned anchor left behind a rest it
        // no longer certifies and a walk measured against a position that has been revoked.
        //
        // Both survivors are read by the anchor RE-CAPTURED afterwards, which is a different place:
        // `hasKinematicEgressSignal` is `frozenByRest && anchor != null && count >= min`, so the
        // kinematic confirm path could fire on the new anchor using fixes earned walking away from
        // the old one. Measured, not argued — see `AnchorTrustTest`.
        frozenByRest = false,
        kinematicEgressFixes = 0,
    )

    // ── The stop ends ─────────────────────────────────────────────────────────

    /**
     * One MOVING fix: the stop is over.
     *
     * @param anchorCleared The movement resolved as CAR (or a reposition maneuver): the anchor goes,
     *   and its capture is cleared exactly as far as it is cleared today — see
     *   [AnchorCapture.clearedWithAnchor].
     * @param carMovement A resolved CAR movement — driving OR a reposition maneuver. Zeroes the
     *   walk-in odometer, which measures "since the last car movement".
     * @param fix The fix being processed; marks the walk run's origin when a new run starts.
     */
    fun onMovingFix(
        anchorCleared: Boolean,
        carMovement: Boolean,
        fix: GpsPoint,
        repositionStreak: Int,
        realDriveStreak: Int,
        sustainedDepartureStreak: Int,
        kinematicEgressFixes: Int,
    ): AnchorTrust = copy(
        anchor = if (anchorCleared) null else anchor,
        capturedAtStop = if (anchorCleared) null else capturedAtStop,
        capture = if (anchorCleared) capture.clearedWithAnchor() else capture,
        frozenByRest = if (anchorCleared) false else frozenByRest,
        // The stop is over; its own gap dies with it. The anchor's SEALED taint clears only with
        // the anchor itself, which is the line above. [DET-GAP-ANCHOR-001]
        stopStartedAt = null,
        stopWindowFixes = emptyList(),
        stopEnteredAfterGapMs = 0L,
        stopEvidenceSince = null,
        walkIn = WalkIn(
            fixesSinceDriving = if (carMovement) 0 else walkIn.fixesSinceDriving + 1,
            runOriginFix = when {
                carMovement -> null
                walkIn.runOriginFix == null -> fix
                else -> walkIn.runOriginFix
            },
        ),
        kinematicEgressFixes = kinematicEgressFixes,
        repositionStreak = repositionStreak,
        realDriveStreak = realDriveStreak,
        sustainedDepartureStreak = sustainedDepartureStreak,
    )

    // ── The egress walk's birth ───────────────────────────────────────────────

    /**
     * [DET-ANCHOR-EGRESS-001] Record or sharpen where the egress walk began — **one transition for
     * both flavours**, where there used to be two near-identical blocks 180 lines apart.
     *
     * The stopped flavour ran on a STOPPED fix and the moving one on a MOVING fix, and they were
     * copies of each other except for one clause. Copies drift: a bound added to one of them is a
     * bound the other keeps not having, and neither block's reader can tell whether a difference is
     * a decision or a divergence. Here the difference is a NAMED PARAMETER, so it has to be passed
     * on purpose.
     *
     * ## The asymmetry, preserved and named: [acceptsKinematicWitness]
     *
     * A birth needs a WITNESS that the egress walk started. On a moving fix either a counted step or
     * a kinematic (GPS-measured) walk fix will do; on a stopped fix **only a counted step** does.
     *
     * That was filed as "bug #6, preserved not fixed", pending *"a directed replay or field data"*.
     *
     * ⛔ **[DET-A-DISOWNED-ANCHOR-TAKES-ITS-WALK-WITH-IT-001] Settled by measurement, and the answer
     * is that it is NOT debt — it is the rule.** A `kinematicEgressFixes` count is only ever earned
     * on a MOVING fix, and that same fix opens the birth, where this flag is already `true`. So on a
     * stopped fix a non-zero count can only be a count earned EARLIER, against a position this
     * anchor may no longer be — and accepting it would record a birth at the stopped fix itself,
     * i.e. exactly at the anchor, which `judgeEgressBirth` then reads as `BORN_AT_ANCHOR`. That
     * manufactures the "no doubt" answer `DET-NOTHING-TO-JUDGE-IS-NOT-NO-DOUBT-001` had just removed,
     * and dresses it as a measurement.
     *
     * The probe that settled it (now `AnchorTrustTest`) also found the real defect behind the
     * question: [disownedByRefutation] was leaving `kinematicEgressFixes` and `frozenByRest` behind,
     * so the stale count was reachable at all. That is fixed; this flag stays.
     *
     * ⚠️ **And the mute-counter user is not what this flag was withholding.** Their walk opens a
     * birth perfectly well through the moving flavour — as long as the fixes report ≥
     * `stoppedSpeedThresholdMps` (1 m/s) with credible accuracy. What has no witness is a walk whose
     * fixes report BELOW that: those land on the stopped side, where nothing counts them, and no
     * flag here can see them. Fixing that means measuring the walk by DISPLACEMENT rather than by
     * the declared speed field — the mirror of `DET-STOP-MUST-BE-STILL-IN-SPACE-001` — and it is its
     * own ticket: `docs/backlog/det-a-walk-reporting-zero-is-still-a-walk-001.md`.
     *
     * @param anchorCleared The anchor went away, so the birth means nothing any more.
     * @param stepCount Presented by `EgressEvidence`.
     * @param kinematicEgressFixes The GPS-measured egress walk so far.
     * @param acceptsKinematicWitness Whether a kinematic fix alone may open a birth. `true` on a
     *   moving fix, `false` on a stopped one — see above.
     */
    @Suppress("LongParameterList")
    fun withEgressBirth(
        fix: GpsPoint,
        anchorCleared: Boolean,
        stepCount: Int,
        kinematicEgressFixes: Int,
        acceptsKinematicWitness: Boolean,
        birthWindowMs: Long,
        refineMaxExtraSteps: Int,
    ): AnchorTrust {
        if (anchorCleared) return copy(egressBirth = null)
        val current = egressBirth

        // Deliberately NOT gated on the anchor being PINNED: when pinning arrives late relative to
        // the walk, "first fix after pinned" is already metres into it, so the 0→witness transition
        // is the earliest anchored witness of the walk start.
        val witnessed = stepCount > 0 || (acceptsKinematicWitness && kinematicEgressFixes > 0)
        val record = current == null && anchor != null && witnessed

        // Within the birth window a better-accuracy fix may sharpen the recorded birth — ONLY while
        // the step count proves the user is still standing at it. That bound is what keeps a slow
        // walk from dragging the birth along (BUG-REPARK-WALK replay).
        val refine = !record && current != null &&
            (fix.timestamp - current.originFix.timestamp) <= birthWindowMs &&
            stepCount <= current.stepCountAtBirth + refineMaxExtraSteps &&
            fix.accuracy < current.originFix.accuracy

        return copy(
            egressBirth = when {
                record -> EgressBirth(fix, stepCount)
                refine -> current.copy(originFix = fix)
                else -> current
            },
        )
    }
}
