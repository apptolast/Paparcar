package io.apptolast.paparcar.domain.detection.state

/**
 * [09 §5] **Did the person get out of the car?** — the third sub-state: everything the FEET and the
 * activity recogniser have to say.
 *
 * It owns the step machine (the counter, its freshness line, the raw event odometer, the
 * live-sensor latch, the pedal-cadence counters) and the AR stamps that qualify what kind of ride
 * this was.
 *
 * ## The boundary, and the one place it is not one-way
 *
 * The design rule is *AnchorTrust owns the anchor; the steps are PRESENTED to it, never copied into
 * it* [07 §2.4]. `DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001` made the traffic run the other way
 * too: the cadence classifier has to know whether the anchor is PINNED, because the same signature
 * — feet moving next to a fast fix — means opposite things on either side of it.
 *
 * That does not break the boundary, it just makes it symmetric: [onStepEvent] receives the anchor's
 * state **as an argument**, exactly as the anchor receives the steps. What it does force is that
 * the REDUCTION ORDER be declared, which is why P2.6 exists — a slip in the order changes the
 * cadence verdict and nothing would say so.
 *
 * ## What this step fixes
 *
 * The eleven values were flat neighbours of thirty unrelated ones, and their reset rules were three
 * different conditions interleaved line by line inside one 40-field `copy`:
 *
 *  - measured driving zeroes the counter, its freshness line and the exit hint;
 *  - driving **or a reposition burst** zeroes the raw event odometer;
 *  - the anchor going away zeroes the stepless-departure run.
 *
 * Read as `if (effectiveDriving) 0 else …` three lines apart, those look like one rule applied
 * consistently. They are not, and the differences are load-bearing: a reposition maneuver is a
 * resolved CAR movement for the walk-in odometer but not for the counter, and the stepless run
 * belongs to the anchor rather than to the drive. [onFix] states all three in one place.
 *
 * @property stepCount Pedestrian steps counted under the triple gate. Reset by measured driving
 *   ONLY — a candidate discard moves [stepsAtLastDiscard] instead, because the count is read by
 *   consumers that need the whole truth. [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001]
 * @property stepsAtLastDiscard Where [stepCount] stood when a candidate was last discarded: the
 *   freshness line. Those steps happened and are still testimony; what expired is their power to
 *   CONFIRM.
 * @property stepEventsSinceDriving Every step event, counted or gated, since the last resolved CAR
 *   movement — the raw walk odometer, deliberately NOT [stepCount] (whose gate ignores steps while
 *   moving with no anchor, which is exactly the walk-in stretch). [DET-CONFIRM-FRESHNESS-001]
 * @property sensorAlive Any step event arrived this session, so the sensor is ALIVE and its SILENCE
 *   during measured movement is evidence of the CAR. A mute sensor's silence is only noise. Never
 *   reset within the session.
 * @property pinnedSteplessMovingFixes Moving fixes provably outside a PINNED anchor's envelope
 *   while a live counter counted nothing. Any step event resets it.
 * @property vehicleExitHint The AR `IN_VEHICLE → EXIT` transition arrived. A HINT, never a proof —
 *   the name says so now: it nominates, and only measured movement confirms.
 * @property bicycleRideAtMs True transition time of the last AR `ON_BICYCLE` ENTER. A VETO input
 *   only: cycling can never arm or confirm anything, only contradict the kinematics — which a
 *   bicycle satisfies as comfortably as a car. [DET-BIKE-NOT-A-CAR-001]
 * @property vehicleRideAtMs True transition time of the last AR `IN_VEHICLE` ENTER. Cycling to the
 *   station and then driving is a trip BY CAR: the later boarding supersedes the ride, which is why
 *   both are timestamps and not booleans — AR delivers transitions out of wall-clock order and only
 *   the true times are comparable.
 * @property fastMotionStepEvents Step events concurrent with a fresh, credible fix above the
 *   pedestrian ceiling — the PEDALLING signature. Never reset mid-session: cadence is evidence
 *   about the session's movement, and a car's phantom bursts stay under threshold.
 * @property fastMotionStepFixes Distinct fixes credited with at least one cadence step — one fix's
 *   burst can be one pothole.
 * @property fastMotionCreditedFixAtMs Dedup marker: the fix instant already credited above.
 */
data class EgressEvidence(
    val stepCount: Int = 0,
    val stepsAtLastDiscard: Int = 0,
    val stepEventsSinceDriving: Int = 0,
    val sensorAlive: Boolean = false,
    val pinnedSteplessMovingFixes: Int = 0,
    val vehicleExitHint: Boolean = false,
    val bicycleRideAtMs: Long? = null,
    val vehicleRideAtMs: Long? = null,
    val fastMotionStepEvents: Int = 0,
    val fastMotionStepFixes: Int = 0,
    val fastMotionCreditedFixAtMs: Long = 0L,
) {

    /**
     * [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] Steps that may still CONFIRM: those counted since
     * the last candidate discard. Every other reader wants [stepCount], the full count — the
     * difference between "this cannot confirm now" and "this never happened".
     */
    val freshStepCount: Int get() = (stepCount - stepsAtLastDiscard).coerceAtLeast(0)

    // ── The feet ──────────────────────────────────────────────────────────────

    /**
     * One step event from the hardware.
     *
     * Every event — counted or gated — proves the sensor is alive, feeds the raw odometer and
     * interrupts any stepless-departure run: a person is moving their feet, so a pinned anchor's
     * movement may still be them.
     *
     * Whether it also increments [stepCount] is the TRIPLE GATE, and the third clause is the one
     * with a field incident behind it [DET-STEP-SPEED-GATE-001]: with an anchor set, egress-walk
     * steps count only at pedestrian speed, because a car crawling in stop-and-go traffic keeps the
     * anchor set while moving at driving speed, and its vibration used to accumulate phantom steps
     * that faked steps+egress and held the anchor mid-route (field 2026-07-12, Avenida de los
     * Mástiles).
     *
     * @param driveAuthorized Presented by `SessionTelemetry`: before any drive, every step counts.
     * @param stopped Presented by the anchor's stop clock.
     * @param anchorPresent An anchor exists, so these steps are the egress walk.
     * @param anchorPinned Presented by the anchor: step-locked or frozen at a matured rest. Vetoes
     *   the cadence reading — see [cadenceQualifies].
     * @param lastFixSpeedMps Speed of the most recent fix, presented by `SessionTelemetry`.
     * @param lastFixCredible Whether that fix's accuracy was credible.
     * @param lastFixSeenAtMs Wall clock when it was processed; `0` before the first fix.
     */
    @Suppress("LongParameterList")
    fun onStepEvent(
        stepAtMs: Long,
        driveAuthorized: Boolean,
        stopped: Boolean,
        anchorPresent: Boolean,
        anchorPinned: Boolean,
        lastFixSpeedMps: Float,
        lastFixCredible: Boolean,
        lastFixSeenAtMs: Long,
        pedestrianCeilingMps: Float,
        motorProofSpeedMps: Float,
        cadenceFixFreshnessMs: Long,
    ): EgressEvidence {
        val shouldCount = !driveAuthorized ||
            stopped ||
            (anchorPresent && lastFixSpeedMps < pedestrianCeilingMps)

        val cadenceStep = cadenceQualifies(
            anchorPinned = anchorPinned,
            lastFixSpeedMps = lastFixSpeedMps,
            lastFixCredible = lastFixCredible,
            lastFixSeenAtMs = lastFixSeenAtMs,
            stepAtMs = stepAtMs,
            pedestrianCeilingMps = pedestrianCeilingMps,
            motorProofSpeedMps = motorProofSpeedMps,
            cadenceFixFreshnessMs = cadenceFixFreshnessMs,
        )

        return copy(
            sensorAlive = true,
            pinnedSteplessMovingFixes = 0,
            stepEventsSinceDriving = stepEventsSinceDriving + 1,
            stepCount = if (shouldCount) stepCount + 1 else stepCount,
            fastMotionStepEvents = fastMotionStepEvents + if (cadenceStep) 1 else 0,
            fastMotionStepFixes = fastMotionStepFixes +
                if (cadenceStep && lastFixSeenAtMs != fastMotionCreditedFixAtMs) 1 else 0,
            fastMotionCreditedFixAtMs = if (cadenceStep) lastFixSeenAtMs else fastMotionCreditedFixAtMs,
        )
    }

    /**
     * [DET-MOTOR-PROOF-001] Is this step a PEDAL stroke? Feet moving in rhythm while a fresh,
     * credible fix reads above the pedestrian ceiling: nobody WALKS at that speed, and a car's
     * counter stays silent while rolling.
     *
     * Three bounds, each from an incident:
     *
     *  - **a ceiling** [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] — the rule had a floor and none, so a
     *    phantom step next to a 36 m/s motorway fix was read as pedalling at 131 km/h. Above
     *    `motorProofSpeedMps` the concurrency proves the OPPOSITE of pedalling;
     *  - **freshness** — a step judged against a stale fix is judged against nothing;
     *  - **a WHEN** [DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001] — once the anchor is pinned the
     *    session has already witnessed where the car came to rest, so feet next to a fast fix are
     *    the user walking away on a noisy stream, which is the EXPECTED shape of an egress. Field
     *    2026-08-22 (Redmi, Góndola→Camelias): a 75 km/h car trip latched the cadence 36 s AFTER
     *    the anchor froze, on steps the log itself labelled `egress walk, anchor set`.
     *
     * Deliberately independent of the counting gate: that gate has egress-walk semantics, this one
     * asks a different question of the same event.
     */
    @Suppress("LongParameterList")
    private fun cadenceQualifies(
        anchorPinned: Boolean,
        lastFixSpeedMps: Float,
        lastFixCredible: Boolean,
        lastFixSeenAtMs: Long,
        stepAtMs: Long,
        pedestrianCeilingMps: Float,
        motorProofSpeedMps: Float,
        cadenceFixFreshnessMs: Long,
    ): Boolean = !anchorPinned &&
        lastFixCredible &&
        lastFixSpeedMps >= pedestrianCeilingMps &&
        lastFixSpeedMps < motorProofSpeedMps &&
        lastFixSeenAtMs > 0L &&
        stepAtMs - lastFixSeenAtMs <= cadenceFixFreshnessMs

    /**
     * [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] A candidate's observation window expired.
     *
     * A VERDICT MAY NOT DESTROY A MEASUREMENT. This used to zero [stepCount] — "the window expired,
     * so those steps were phantom jiggle" — which is the right thing to say to the NEXT candidate
     * and the wrong thing to say to every other reader: the anchor lock, the walk-reach ceilings
     * and, above all, the 15-minute unattended verdict that reads the same counter to justify
     * saving a zone. So the count stands and the freshness line moves.
     */
    fun candidateDiscarded(): EgressEvidence = copy(stepsAtLastDiscard = stepCount)

    // ── What the fix stream does to all of it ─────────────────────────────────

    /**
     * One processed fix, with the three reset rules that used to sit interleaved in a 40-field
     * `copy` — stated together precisely because they are NOT the same rule.
     *
     * @param effectiveDriving The person/car discriminator resolved as CAR. Zeroes the counter, its
     *   freshness line and the exit hint: a drive ends the previous egress outright, so jam jiggle
     *   cannot accumulate across stops.
     * @param repositionBurst A parking maneuver. A resolved CAR movement for the RAW odometer (it
     *   measures "since the last car movement") but **not** for the counter — the user has not
     *   driven away, they shuffled the car.
     * @param anchorCleared The anchor is gone or its run resolved as CAR. Only this clears the
     *   stepless-departure run, which belongs to the anchor rather than to the drive.
     * @param steplessMovingFixes The run's new value, computed by the caller against the anchor's
     *   envelope — presented, not owned.
     */
    fun onFix(
        effectiveDriving: Boolean,
        repositionBurst: Boolean,
        anchorCleared: Boolean,
        steplessMovingFixes: Int,
    ): EgressEvidence = copy(
        stepCount = if (effectiveDriving) 0 else stepCount,
        stepsAtLastDiscard = if (effectiveDriving) 0 else stepsAtLastDiscard,
        vehicleExitHint = if (effectiveDriving) false else vehicleExitHint,
        stepEventsSinceDriving = if (effectiveDriving || repositionBurst) 0 else stepEventsSinceDriving,
        pinnedSteplessMovingFixes = if (anchorCleared) 0 else steplessMovingFixes,
    )

    // ── What the activity recogniser says ─────────────────────────────────────

    /**
     * The `IN_VEHICLE → EXIT` transition arrived.
     *
     * [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] An EXIT is also evidence of the BOARDING it must have
     * followed, so it supersedes an earlier cycling stamp exactly like an ENTER does. Forward only:
     * AR delivers transitions out of order, and an EXIT stamped older than a boarding already known
     * would AGE the evidence.
     */
    fun onVehicleExit(atMs: Long): EgressEvidence = copy(
        vehicleExitHint = true,
        vehicleRideAtMs = maxOf(vehicleRideAtMs ?: Long.MIN_VALUE, atMs),
    )

    /** [DET-BIKE-NOT-A-CAR-001] An AR `ON_BICYCLE` ENTER at its TRUE transition time. */
    fun onBicycleRide(atMs: Long): EgressEvidence = copy(bicycleRideAtMs = atMs)

    /** [DET-BIKE-NOT-A-CAR-001] An AR `IN_VEHICLE` ENTER at its TRUE transition time. */
    fun onVehicleRide(atMs: Long): EgressEvidence = copy(vehicleRideAtMs = atMs)
}
