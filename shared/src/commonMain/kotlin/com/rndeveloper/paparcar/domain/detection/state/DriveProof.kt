package com.rndeveloper.paparcar.domain.detection.state

import com.rndeveloper.paparcar.domain.detection.physics.DriveProofBounds
import com.rndeveloper.paparcar.domain.detection.physics.corroboratesDrive
import com.rndeveloper.paparcar.domain.detection.physics.creditSpeedBand
import com.rndeveloper.paparcar.domain.detection.physics.pruneRecentFixes
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.util.haversineMeters

/** HOW a session proved it drove. The distinction is citable in diagnostics; until now it existed
 *  only as a line of logcat. [DET-DRIVE-PROOF-001][DET-SHORT-HOP-PROOF-001] */
enum class DriveProofSource {
    /** Speed corroborated across a bounded look-back window: real ground covered, not one Doppler claim. */
    TRACK_WINDOW,

    /** Measured DISPLACEMENT from the pin the car left, sustained across consecutive credible fixes. */
    SHORT_HOP,
}

/**
 * [09 §5] **Did this session watch the car drive?** — the fourth sub-state.
 *
 * It owns the two independent proofs, the peak they promote, the look-back ring and the two band
 * clocks. One rule governs the whole thing: **a statistic about driving is worth nothing until the
 * track has PROVEN a drive.** A lone mirage — 45 m/s at claimed accuracy 5 m from a phone sitting
 * indoors — used to set the session peak, satisfy every "did we see driving?" question and pin the
 * living room (field 2026-07-27).
 *
 * ## Three lifetimes in one sub-state, and that is the finding
 *
 * The plan expected the boundary here to be "rings versus clocks" [10 P2.4]. It is not. What
 * actually separates these values is **how long each one lives**, and there are three:
 *
 *  - **[proven] and the band clocks LATCH.** Nothing in the session ever clears them. The proof is a
 *    fact about the trip: once the car provably drove, no later fix un-drives it.
 *  - **[shortHopRun] is a RUN.** Any fix that fails the geometry breaks it back to zero, so a lone
 *    cache teleport never accumulates into a proof.
 *  - **[recentFixes] expires by TIME.** It is a window, not a memory.
 *
 * Read as eleven flat fields updated in one `copy`, those three read as one accumulator. They are
 * not, and the difference is exactly what makes a proof a proof: a latch that could be reset by a
 * slow fix, or a run that latched, would each break a different field case.
 *
 * ## Why `hasEverReachedDrivingSpeed` is NOT here
 *
 * It is the lifecycle AUTHORIZATION — *may this session confirm at all?* — and lives in
 * `SessionTelemetry`. Fusing nomination with confirmation is the exact bug `DET-G-05` closed, and
 * the governing doctrine says the event nominates while only measured movement confirms
 * [07 §3.3]. [DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001] And the two now differ in one
 * more way that keeps this file's latch rule TRUE as written: the authorization's fix-born crossing
 * is provisional for one fix (`SessionTelemetry.provisionalCrossing` — its own track may take it
 * back), while [proven] here stays unconditional, because it already demands a corroborated window
 * or a streak before it latches. No later fix un-drives a PROVEN drive; a lone claimed one was
 * never proven.
 *
 * @property proven How the drive was proven, or null. Latched for the session, with the source that
 *   FIRST proved it — a later track window does not overwrite a short hop's provenance.
 * @property provenMaxSpeedMps The session peak every confirm path reads as "did this session measure
 *   driving?", ZERO until [proven] latches. Named for what it is: the peak AFTER promotion.
 * @property peakMps The pre-proof accumulator [provenMaxSpeedMps] is promoted from the moment the
 *   track proves a drive, so a proven session reports the same vmax it always did.
 * @property credibleFixCount How many fixes this session saw at real driving speed with credible
 *   accuracy. [peakMps] is ONE sample and a receiver converging out of a cold start emits exactly
 *   one (field 2026-08-16 23:52: 42 km/h at acc 11.5 m on the third fix, user on foot all session);
 *   a COUNT distinguishes that spike from a drive the look-back merely failed to corroborate.
 *   [DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001]
 * @property recentFixes The bounded look-back ring `corroboratesDrive` judges the current fix against.
 * @property shortHopRun Consecutive credible fixes sitting unambiguously away from the pin the car
 *   left. [DET-SHORT-HOP-PROOF-001]
 * @property drivingBandMs Cumulative ms in the credible driving band, credited only across gaps that
 *   fit the window the drive-proof shape already trusts. Read through [provenDrivingBandMs].
 * @property motorBandMs The same clock one band higher: time held above a speed muscle cannot
 *   produce. Deliberately NOT drive-proof-gated — its job is to REFUTE a human-powered claim, never
 *   to buy a silent pin, and the asymmetry runs the safe way: doubting the veto costs a prompt,
 *   believing it cost a car (field 2026-08-20, 361 s above 40 km/h and the session still died judged
 *   a bicycle). [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001]
 * @property motorDisplacementRateMps The fastest ground rate this session has SUSTAINED from an
 *   anchor, measured as distance over elapsed time rather than as samples in a band.
 *   [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001] It exists because [motorBandMs] is a clock, and a
 *   clock cannot tick across a hole: field 2026-08-26 (Redmi, Valdés→Góndola) measured 94,3 km/h at
 *   15,5 m of accuracy and still banked ~1 s of the 30 s the clock needs, because OEM batching left
 *   its three in-band fixes 163 s and 200 s apart and `creditSpeedBand` credits nothing beyond
 *   `driveProofWindowMaxMs` (60 s). A rate over a baseline does not care how the middle was sampled —
 *   the same reason `sustainedDepartureFromAnchor` exists at all. NOT a peak: its measurement floor
 *   is `sustainedDepartureFloorMeters` plus both accuracy envelopes, and rates above
 *   `sustainedDepartureMaxRateMps` are discarded as cache teleports, so the lone-sample mirage
 *   [DET-ASSERTION-OUTRANKS-INFERENCE-001] cannot enter here. Like [motorBandMs] and for the same
 *   reason, deliberately NOT drive-proof-gated: its job is to REFUTE a human-powered claim, never to
 *   buy a silent pin.
 * @property lastFixSeenAtMs Wall clock when the last fix was processed — the freshness reference a
 *   concurrent step event is judged against (step events carry no GPS timestamp of their own).
 * @property lastFixCredible Whether that fix's accuracy was credible.
 * @property maxExcursionMeters [DET-DRIVING-EVIDENCE-VALUE-OBJECT-001] The furthest the position
 *   provably got from the session origin, counted only over fixes whose accuracy was credible. A
 *   LATCH, like [proven]: how far the car got is a fact about the trip, and coming back does not
 *   un-travel it. This is the displacement half of `DrivingEvidence.Measured`, and it is an
 *   EXCURSION rather than a net origin→final displacement on purpose — a there-and-back trip that
 *   parks on its own street nets ≈0 and would become a false negative, while the mirage this has to
 *   refute (field 2026-08-29: 71,6 m out, 64,8 m back in 3,5 s) never got past 72 m.
 */
data class DriveProof(
    val proven: DriveProofSource? = null,
    val provenMaxSpeedMps: Float = 0f,
    val peakMps: Float = 0f,
    val credibleFixCount: Int = 0,
    val maxExcursionMeters: Double = 0.0,
    val recentFixes: List<GpsPoint> = emptyList(),
    val shortHopRun: Int = 0,
    val drivingBandMs: Long = 0L,
    val lastBandFixTimestampMs: Long = 0L,
    val motorBandMs: Long = 0L,
    val lastMotorBandFixTimestampMs: Long = 0L,
    val motorDisplacementRateMps: Float = 0f,
    val lastFixSeenAtMs: Long = 0L,
    val lastFixCredible: Boolean = false,
) {

    /** Has the track proven a drive at all? */
    val isProven: Boolean get() = proven != null

    /**
     * [DET-MOTOR-PROOF-001] The sustained-drive statistic the evaluator's `sessionSawDriving` reads,
     * under the same promotion rule as [provenMaxSpeedMps]: ZERO until the track proved a drive, so
     * an uncorroborated band run buys nothing.
     */
    val provenDrivingBandMs: Long get() = if (isProven) drivingBandMs else 0L

    /**
     * One processed fix: both proofs, the promotion, the ring and the two clocks.
     *
     * Everything the caller must present is a value this sub-state does not own — the departure pin
     * and its fence, the session clock, and the config bars.
     *
     * @param departureAnchor The pin the car left. Null for manual / AR-armed trips with no origin
     *   pin, which therefore can never prove a short hop.
     * @param elapsedSinceArmMs Wall clock since the session armed.
     * @param credibleSpeedFix This fix's accuracy is good enough to believe its speed.
     * @param bounds The look-back window's shape — presented so the ring and the corroboration read
     *   the SAME numbers; a second copy is how a window gets widened in one and forgotten in the
     *   other.
     * @param sustainedDepartureRateMps The ground rate the stop reduction ALREADY measured from the
     *   anchor for this same fix, or null when there was no such departure. Presented rather than
     *   recomputed here for the reason this whole sub-state is presented values:
     *   `sustainedDepartureFromAnchor` reads the anchor as it stood BEFORE this fix, and a second
     *   call site would be a copy that agrees by luck.
     *   [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001]
     */
    @Suppress("LongParameterList")
    fun onFix(
        fix: GpsPoint,
        nowMs: Long,
        credibleSpeedFix: Boolean,
        departureAnchor: GpsPoint?,
        departureFenceRadiusMeters: Float,
        elapsedSinceArmMs: Long,
        bounds: DriveProofBounds,
        config: ParkingDetectionConfig,
        sustainedDepartureRateMps: Double? = null,
        distanceFromOriginMeters: Double = 0.0,
    ): DriveProof {
        val newPeak = if (fix.speed > peakMps && credibleSpeedFix) fix.speed else peakMps
        val fixInBand = credibleSpeedFix && fix.speed >= config.minimumTripSpeedMps
        val fixInMotorBand = credibleSpeedFix && fix.speed >= config.motorProofSpeedMps

        val newShortHopRun =
            if (shortHopQualifies(fix, departureAnchor, departureFenceRadiusMeters, elapsedSinceArmMs, config)) {
                shortHopRun + 1
            } else {
                0
            }

        // First proof wins and keeps its provenance: a later track window does not relabel a hop.
        val newProven = proven ?: when {
            newShortHopRun >= config.shortHopProofFixes -> DriveProofSource.SHORT_HOP
            fixInBand && corroboratesDrive(recentFixes, fix, bounds) -> DriveProofSource.TRACK_WINDOW
            else -> null
        }

        return copy(
            proven = newProven,
            // The retroactive promotion: the banked peak turns on the instant the proof arrives.
            provenMaxSpeedMps = if (newProven != null) newPeak else 0f,
            peakMps = newPeak,
            credibleFixCount = credibleFixCount + if (fixInBand) 1 else 0,
            // [DET-DRIVING-EVIDENCE-VALUE-OBJECT-001] Only a credible fix may move the excursion:
            // a 250 m-accuracy fix reported 200 m away proves nothing about where the car got to.
            maxExcursionMeters = if (credibleSpeedFix) {
                maxOf(maxExcursionMeters, distanceFromOriginMeters)
            } else {
                maxExcursionMeters
            },
            recentFixes = pruneRecentFixes(recentFixes, fix, bounds),
            shortHopRun = newShortHopRun,
            drivingBandMs = creditSpeedBand(
                accumulatedMs = drivingBandMs,
                lastInBandFixMs = lastBandFixTimestampMs,
                fixTimestampMs = fix.timestamp,
                fixInBand = fixInBand,
                windowMaxMs = config.driveProofWindowMaxMs,
            ),
            lastBandFixTimestampMs = if (fixInBand) fix.timestamp else lastBandFixTimestampMs,
            motorBandMs = creditSpeedBand(
                accumulatedMs = motorBandMs,
                lastInBandFixMs = lastMotorBandFixTimestampMs,
                fixTimestampMs = fix.timestamp,
                fixInBand = fixInMotorBand,
                windowMaxMs = config.driveProofWindowMaxMs,
            ),
            lastMotorBandFixTimestampMs = if (fixInMotorBand) fix.timestamp else lastMotorBandFixTimestampMs,
            // A high-water mark, like [peakMps] — but of a RATE OVER A BASELINE, not of a sample. It
            // latches for the session because what it records is a fact about the trip: ground the
            // vehicle provably covered does not become uncovered.
            motorDisplacementRateMps = maxOf(
                motorDisplacementRateMps,
                sustainedDepartureRateMps?.toFloat() ?: 0f,
            ),
            lastFixSeenAtMs = nowMs,
            lastFixCredible = credibleSpeedFix,
        )
    }

    /**
     * [DET-SHORT-HOP-PROOF-001] The SHORT-HOP profile of the drive verifier: does this fix sit
     * credibly and unambiguously away from the pin the car left?
     *
     * `corroboratesDrive` proves a drive from SPEED, and that shape is unreachable on a short, slow
     * urban hop: field 2026-08-14 22:56 armed on a verified exit, recorded 303 fixes, peaked at
     * 30 km/h, ended 900 m from the pin with 104 egress steps — and still logged `drive 3/303`, so
     * the statistic stayed 0, the unattended timeout read "no measured driving" and the park was
     * LOST. This profile proves the same fact from DISPLACEMENT.
     *
     * It is anchored to the PIN THE CAR LEFT, never to the session's own first fix:
     *
     *  - the pin is a position the car provably occupied, so distance from it is the physical
     *    question "did this vehicle actually go somewhere?";
     *  - it makes the Doppler-mirage class impossible by construction (field 2026-07-27) — a phone
     *    indoors next to its own pin measures ~0 m of displacement however many phantom 45 m/s
     *    bursts the chipset reports. Anchoring to the first fix would have handed the mirage its own
     *    burst as the origin and called the return home a drive.
     *
     * [DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001] And a fix that is merely FAR measures nothing either.
     * Every other clause is geometry against the pin, which answers "where has the car ended up?" —
     * never "did THIS session watch it get there?". On an arm that bounds when the car left the two
     * coincide; on a sentry-wake they do not, because the sensor can fire hours after the user
     * walked off, so the clock starts at zero with a kilometre of WALKING already banked. Field
     * 2026-08-16 23:52: 990 m of pavement covered on foot, three stationary fixes at that distance
     * handed the session a drive proof, and 220 walking steps planted a pin at the beach. So the run
     * must MEASURE driving fix by fix — a pedestrian cannot sustain that, a real hop produces
     * nothing else.
     *
     * Asymmetric failure: every bound errs toward "not proven".
     */
    @Suppress("LongParameterList")
    internal fun shortHopQualifies(
        fix: GpsPoint,
        departureAnchor: GpsPoint?,
        fenceRadiusMeters: Float,
        elapsedSinceArmMs: Long,
        config: ParkingDetectionConfig,
    ): Boolean {
        val anchor = departureAnchor ?: return false
        // A degraded fix measures nothing — the same credibility gate every drive decision uses.
        if (fix.accuracy > config.minGpsAccuracyForDriving) return false
        if (!config.isCredibleDrivingSpeed(fix.speed * KMH_PER_MPS, fix.accuracy)) return false
        val distance = haversineMeters(anchor.latitude, anchor.longitude, fix.latitude, fix.longitude)
        // Floor first: well beyond any observed GPS pathology, the fence, and both envelopes.
        if (distance < config.shortHopProofFloorMeters) return false
        if (distance <= anchor.accuracy + fix.accuracy + fenceRadiusMeters) return false
        // …and beyond what legs explain over the elapsed time — the same physics as the ride proof.
        return config.isBeyondPedestrianReach(
            distanceMeters = distance,
            elapsedMs = elapsedSinceArmMs,
            fenceRadiusMeters = fenceRadiusMeters,
            accuracyMeters = anchor.accuracy + fix.accuracy,
        )
    }

    private companion object {
        const val KMH_PER_MPS = 3.6f
    }
}
