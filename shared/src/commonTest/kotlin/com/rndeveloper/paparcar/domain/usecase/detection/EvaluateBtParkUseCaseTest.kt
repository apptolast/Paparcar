package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * First test suite of the Bluetooth path (audit A3): the pure decision core extracted from the
 * Android detector. The two phantom-spot holes it locks shut [DET-AUDIT-002 T2]:
 * a BT drop while DRIVING must never pin a park, and the walk-away distance must be covered
 * on FOOT — the car's own displacement satisfied it before.
 */
class EvaluateBtParkUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val useCase = EvaluateBtParkUseCase(config)

    private fun fix(
        meters: Double = 0.0,
        speedMps: Float = 0f,
        accuracy: Float = 10f,
        timestamp: Long = 0L,
    ) = GpsPoint(
        latitude = BASE_LAT + meters / METERS_PER_DEGREE_LAT,
        longitude = BASE_LON,
        accuracy = accuracy,
        timestamp = timestamp,
        speed = speedMps,
    )

    /** One sampled fix and the wall-clock instant it arrived — what the hunt folds over. */
    private data class Sample(val fix: GpsPoint, val atMs: Long)

    private fun stoppedAt(meters: Double, atMs: Long) = Sample(fix(meters = meters, speedMps = 0.3f), atMs)
    private fun drivingAt(atMs: Long) = Sample(fix(meters = 300.0, speedMps = 9f), atMs)
    private fun degradedAt(atMs: Long) = Sample(fix(accuracy = 80f), atMs)
    private fun walkingAt(atMs: Long) = Sample(fix(meters = 20.0, speedMps = 1.4f), atMs)

    /** Replay a whole disconnect window through the fold, in arrival order. The disconnect is t=0. */
    private fun hunt(vararg samples: Sample) = samples.fold(BtCandidateHunt(sinceMs = 0L)) { state, sample ->
        useCase.foldCandidateFix(state, sample.fix, sample.atMs)
    }

    private fun candidate(atMs: Long, meters: Double = 0.0) = BtCandidate(fix(meters = meters), atMs)

    private fun metersFromBase(point: GpsPoint) = (point.latitude - BASE_LAT) * METERS_PER_DEGREE_LAT

    // ── Candidate fix: pin-grade AND stationary, or nothing ──────────────────

    @Test
    fun should_acceptCandidate_when_stationaryAndPinGrade() {
        assertEquals(BtParkVerdict.CandidateAccepted, useCase.evaluateCandidateFix(fix(speedMps = 0.3f, accuracy = 8f)))
    }

    @Test
    fun should_abortCandidate_when_credibleDrivingFix() {
        // The audit-A2 hole: BT drop at 30 km/h used to pin a "park" on the road.
        assertEquals(BtParkVerdict.DrivingAbort, useCase.evaluateCandidateFix(fix(speedMps = 8f, accuracy = 10f)))
    }

    @Test
    fun should_keepSampling_when_fixAccuracyDegraded() {
        // Degraded fixes decide NOTHING — neither candidate nor abort (noise either way).
        assertEquals(BtParkVerdict.KeepWaiting, useCase.evaluateCandidateFix(fix(speedMps = 0f, accuracy = 80f)))
    }

    @Test
    fun should_keepSampling_when_apparentDrivingSpeedWithDegradedAccuracy() {
        // A 120 m-accuracy Doppler spike must not abort a real park (fail-negative both ways).
        assertEquals(BtParkVerdict.KeepWaiting, useCase.evaluateCandidateFix(fix(speedMps = 8f, accuracy = 120f)))
    }

    @Test
    fun should_keepSampling_when_movingAtWalkingPace() {
        // Settling into the spot / user already stepping out: not stopped, not driving.
        assertEquals(BtParkVerdict.KeepWaiting, useCase.evaluateCandidateFix(fix(speedMps = 1.4f, accuracy = 10f)))
    }

    // ── Candidate hunt: the EARLIEST fix wins [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001] ──────

    @Test
    fun should_pinTheEarliestStationaryFix_when_laterOnesAlsoQualify() {
        // The whole ticket. Disconnect at t=0, the user still at the car at t=3 s, then walking:
        // fixes keep arriving and keep qualifying, and every one of them is further from the car.
        val hunt = hunt(
            stoppedAt(meters = 0.0, atMs = 3_000L),    // at the car, engine just off
            stoppedAt(meters = 40.0, atMs = 33_000L),  // 40 m up the street, waiting at a crossing
            stoppedAt(meters = 95.0, atMs = 61_000L),  // at the door
        )
        val pinned = assertNotNull(hunt.candidate)
        assertEquals(0.0, metersFromBase(pinned.fix), 0.5)
        assertEquals(3_000L, pinned.atMs)
    }

    @Test
    fun should_abortHunt_when_drivingArrivesAfterAStationaryCandidate() {
        // BT dropped at a red light: the fix at t=2 s is a genuine standstill, and the car moving
        // off at t=15 s is what says it was not a park. Before the hunt started at the disconnect,
        // this whole window went unwatched — the first sample was taken at t=30 s.
        val hunt = hunt(stoppedAt(meters = 0.0, atMs = 2_000L), drivingAt(15_000L))
        assertTrue(hunt.aborted)
    }

    @Test
    fun should_keepTheAbort_when_theCarStopsAgainLater() {
        // Abort is terminal: the next red light must not resurrect the hunt.
        val hunt = hunt(drivingAt(4_000L), stoppedAt(meters = 0.0, atMs = 20_000L))
        assertTrue(hunt.aborted)
        assertNull(hunt.candidate)
    }

    @Test
    fun should_holdNoCandidate_when_everyFixIsUnusable() {
        // Degraded and walking-pace fixes decide nothing — the detector reports a GPS timeout.
        val hunt = hunt(degradedAt(5_000L), walkingAt(20_000L))
        assertNull(hunt.candidate)
        assertFalse(hunt.aborted)
    }

    @Test
    fun should_ignoreTheCachedFix_when_itWasStampedBeforeTheDisconnect() {
        // Subscribing AT the disconnect means the fused provider can hand us a cached fix first (its
        // default max age is twice the request interval ≈ 10 s). Ten seconds before the ignition
        // went off the car was still rolling, and 4 m/s = 14 km/h clears minimumDepartureSpeedKmh —
        // so without the stamp guard the very first sample would abort ordinary BT parks.
        val disconnectAtMs = 1_000_000L
        var state = BtCandidateHunt(sinceMs = disconnectAtMs)

        state = useCase.foldCandidateFix(
            state,
            fix(meters = 120.0, speedMps = 4f, timestamp = disconnectAtMs - 9_000L),
            atMs = disconnectAtMs + 200L,
        )
        assertFalse(state.aborted)
        assertNull(state.candidate)

        state = useCase.foldCandidateFix(
            state,
            fix(meters = 0.0, speedMps = 0.2f, timestamp = disconnectAtMs + 4_000L),
            atMs = disconnectAtMs + 4_000L,
        )
        assertEquals(0.0, metersFromBase(assertNotNull(state.candidate).fix), 0.5)
    }

    @Test
    fun should_notPinTheCachedFix_when_itWasStampedBeforeTheDisconnect() {
        // The mirror hazard of the same cached sample: stationary (the last light before home), so
        // it would win the hunt outright and pin the car a block back.
        val disconnectAtMs = 1_000_000L
        val state = useCase.foldCandidateFix(
            BtCandidateHunt(sinceMs = disconnectAtMs),
            fix(meters = 150.0, speedMps = 0.1f, timestamp = disconnectAtMs - 8_000L),
            atMs = disconnectAtMs + 100L,
        )
        assertNull(state.candidate)
    }

    // ── Walk-away: the displacement must be WALKED ────────────────────────────

    @Test
    fun should_confirm_when_walkedDistanceAtPedestrianRate() {
        // 35 m in 25 s = 1.4 m/s — a person on foot.
        val verdict = useCase.evaluateWalkAway(candidate(atMs = 0L), fix(meters = 35.0, speedMps = 1.3f), nowMs = 25_000L)
        assertEquals(BtParkVerdict.WalkAwayConfirmed, verdict)
    }

    @Test
    fun should_confirm_when_theWalkStartedBeforeTheWatchDid() {
        // [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001] The coupling that makes the early hunt safe.
        // Candidate sampled at t=2 s; the watch only opens when the debounce closes at t=30 s, and
        // its first fix is already 35 m away. Measured from the candidate that is 1.25 m/s — a
        // walk. Measured from the start of the watch it would be 35 m in a blink, i.e. a teleport,
        // and the lane would abort a perfectly real park.
        val walkFix = fix(meters = 35.0, speedMps = 0.9f)
        assertEquals(
            BtParkVerdict.WalkAwayConfirmed,
            useCase.evaluateWalkAway(candidate(atMs = 2_000L), walkFix, nowMs = 30_000L),
        )
        // The same displacement clocked from the watch instead of from the candidate — i.e. what
        // this lane would do if the two halves of the seal were allowed to drift apart.
        assertEquals(
            BtParkVerdict.DrivingAbort,
            useCase.evaluateWalkAway(candidate(atMs = 30_000L), walkFix, nowMs = 30_000L),
        )
    }

    @Test
    fun should_abortWalkAway_when_currentFixIsCredibleDriving() {
        // The car (with the phone in it) drove on after a BT drop at a light.
        val verdict = useCase.evaluateWalkAway(candidate(atMs = 0L), fix(meters = 40.0, speedMps = 9f), nowMs = 6_000L)
        assertEquals(BtParkVerdict.DrivingAbort, verdict)
    }

    @Test
    fun should_abortWalkAway_when_displacementOutrunsPedestrianRate() {
        // 200 m in 10 s with speed=0 fixes (sparse cadence hid the drive): 20 m/s is wheels.
        val verdict = useCase.evaluateWalkAway(candidate(atMs = 0L), fix(meters = 200.0, speedMps = 0f), nowMs = 10_000L)
        assertEquals(BtParkVerdict.DrivingAbort, verdict)
    }

    @Test
    fun should_abortWalkAway_when_thresholdCoveredInstantly() {
        // No elapsed span with the distance already covered = teleport/GPS jump — never confirm.
        val verdict = useCase.evaluateWalkAway(candidate(atMs = 9_000L), fix(meters = 35.0, speedMps = 0f), nowMs = 9_000L)
        assertEquals(BtParkVerdict.DrivingAbort, verdict)
    }

    @Test
    fun should_keepWaiting_when_underTheWalkThreshold() {
        val verdict = useCase.evaluateWalkAway(candidate(atMs = 0L), fix(meters = 15.0, speedMps = 1.2f), nowMs = 12_000L)
        assertEquals(BtParkVerdict.KeepWaiting, verdict)
    }

    @Test
    fun should_keepWaiting_when_currentWalkFixDegraded() {
        // A 100 m-accuracy fix can fake 30 m of displacement by noise alone.
        val verdict = useCase.evaluateWalkAway(
            candidate(atMs = 0L),
            fix(meters = 35.0, speedMps = 1.2f, accuracy = 100f),
            nowMs = 25_000L,
        )
        assertEquals(BtParkVerdict.KeepWaiting, verdict)
    }

    // ── Engagement sizing [DET-BT-DISCONNECT-WITHOUT-RIDE-001] ────────────────────────────────

    @Test
    fun should_reportProximityOnly_when_engagementWasTheFieldCase() {
        // Field 2026-08-21, Oppo + Kamiq: ACL CONNECT 14:08:40.2 → DISCONNECT 14:08:53.9. The car
        // was brought home by a family member and switched off beside the phone; the lane placed a
        // 0.85 pin with a geofence off this.
        val verdict = useCase.evaluateEngagement(connectedAtMs = 0L, disconnectedAtMs = 13_700L)
        assertEquals(BtEngagement.ProximityOnly(13_700L), verdict)
    }

    @Test
    fun should_reportRide_when_engagementReachesTheMinimum() {
        val verdict = useCase.evaluateEngagement(
            connectedAtMs = 0L,
            disconnectedAtMs = config.btMinRideDurationMs,
        )
        assertEquals(BtEngagement.Ride(config.btMinRideDurationMs), verdict)
    }

    @Test
    fun should_reportProximityOnly_when_engagementIsOneMsShortOfTheMinimum() {
        val verdict = useCase.evaluateEngagement(
            connectedAtMs = 0L,
            disconnectedAtMs = config.btMinRideDurationMs - 1,
        )
        assertEquals(BtEngagement.ProximityOnly(config.btMinRideDurationMs - 1), verdict)
    }

    @Test
    fun should_reportUnknown_when_noConnectOnRecord() {
        // Doubt, not permission: an unsized engagement asks instead of placing.
        assertEquals(BtEngagement.Unknown, useCase.evaluateEngagement(connectedAtMs = null, disconnectedAtMs = 1_000L))
    }

    @Test
    fun should_reportUnknown_when_connectStampSitsInTheFuture() {
        // Clock change between the two edges — a negative duration sizes nothing.
        assertEquals(BtEngagement.Unknown, useCase.evaluateEngagement(connectedAtMs = 5_000L, disconnectedAtMs = 1_000L))
    }

    @Test
    fun should_reportUnknown_when_engagementExceedsTheRideCeiling() {
        // An OEM force-stop can swallow the CONNECT broadcast; the surviving stale stamp would
        // otherwise compute a multi-day "ride" and wave the old behaviour straight back in.
        val verdict = useCase.evaluateEngagement(
            connectedAtMs = 0L,
            disconnectedAtMs = config.btMaxRideDurationMs + 1,
        )
        assertEquals(BtEngagement.Unknown, verdict)
    }

    // ── Ride proof: the car has to GO somewhere [DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001] ──────
    //
    // The hole the two gates above could not see: both judge the END of a ride, neither asks
    // whether there WAS one. Ninety seconds of radio in a parked car satisfied every one of them.

    /** The engagement's origin: a cached fix stamped at CONNECT, [ageMs] old by then. */
    private fun origin(meters: Double = 0.0, ageMs: Long = 0L, accuracy: Float = 10f) =
        fix(meters = meters, accuracy = accuracy, timestamp = CONNECTED_AT_MS - ageMs)

    @Test
    fun should_proveRide_when_carEndedFarFromWhereItConnected() {
        val proof = useCase.evaluateRideDisplacement(
            origin = origin(),
            connectedAtMs = CONNECTED_AT_MS,
            candidate = candidate(atMs = CONNECTED_AT_MS + 600_000L, meters = 400.0),
        )
        assertIs<BtRideProof.Moved>(proof, "400 m with a fresh origin is a ride")
        // The threshold actually applied, spelled out: base + both accuracies + zero walk allowance.
        assertEquals(170.0, proof.requiredMeters, 0.5)
    }

    @Test
    fun should_refuseRide_when_theCarNeverLeftItsSpace() {
        // THE case: 10 minutes in the car with the radio on, engine off, walk into the house.
        // Ride-shaped in time, and the car is exactly where it was.
        val proof = useCase.evaluateRideDisplacement(
            origin = origin(),
            connectedAtMs = CONNECTED_AT_MS,
            candidate = candidate(atMs = CONNECTED_AT_MS + 600_000L, meters = 6.0),
        )
        assertIs<BtRideProof.DidNotMove>(proof, "6 m is not a trip")
    }

    @Test
    fun should_refuseRide_when_theWalkBeforeBoardingCouldExplainTheWholeDisplacement() {
        // A 250 m gap clears the bare threshold — but the origin was already 60 s old at the
        // connect, and 60 s of walking is 150 m the user could have covered on FOOT toward the car.
        // Charging that walk is what stops a stale origin from manufacturing a ride nobody drove.
        val proof = useCase.evaluateRideDisplacement(
            origin = origin(ageMs = 60_000L),
            connectedAtMs = CONNECTED_AT_MS,
            candidate = candidate(atMs = CONNECTED_AT_MS + 600_000L, meters = 250.0),
        )
        assertIs<BtRideProof.DidNotMove>(proof, "250 m against a 60 s-old origin is not proof")
        assertEquals(320.0, proof.requiredMeters, 0.5) // 150 base + 20 accuracies + 150 walk
    }

    @Test
    fun should_reportUnmeasurable_when_noFixWasCachedAtConnect() {
        val proof = useCase.evaluateRideDisplacement(
            origin = null,
            connectedAtMs = CONNECTED_AT_MS,
            candidate = candidate(atMs = CONNECTED_AT_MS + 600_000L, meters = 4_000.0),
        )
        // Even a 4 km gap decides nothing without an origin: it is unmeasured, not measured-large.
        assertEquals(BtRideProof.Unmeasurable(BtRideOriginGap.NO_FIX), proof)
    }

    @Test
    fun should_reportUnmeasurable_when_theOriginFixIsOlderThanTheCeiling() {
        val proof = useCase.evaluateRideDisplacement(
            origin = origin(ageMs = config.btConnectFixMaxAgeMs + 1),
            connectedAtMs = CONNECTED_AT_MS,
            candidate = candidate(atMs = CONNECTED_AT_MS + 600_000L, meters = 4_000.0),
        )
        assertEquals(BtRideProof.Unmeasurable(BtRideOriginGap.STALE_FIX), proof)
    }

    @Test
    fun should_reportUnmeasurable_when_theOriginIsStampedAfterTheConnect() {
        // Clock change between the cached fix and the connect — a negative age is not a distance.
        val proof = useCase.evaluateRideDisplacement(
            origin = origin(ageMs = -5_000L),
            connectedAtMs = CONNECTED_AT_MS,
            candidate = candidate(atMs = CONNECTED_AT_MS + 600_000L, meters = 400.0),
        )
        assertEquals(BtRideProof.Unmeasurable(BtRideOriginGap.INCOHERENT_CLOCK), proof)
    }

    @Test
    fun should_refuseRide_when_bothFixesAreCoarseEnoughToExplainTheGap() {
        // A distance smaller than the error bars of its own endpoints is not a distance.
        val proof = useCase.evaluateRideDisplacement(
            origin = origin(accuracy = 60f),
            connectedAtMs = CONNECTED_AT_MS,
            candidate = BtCandidate(fix(meters = 190.0, accuracy = 60f), CONNECTED_AT_MS + 600_000L),
        )
        assertIs<BtRideProof.DidNotMove>(proof, "190 m between two ±60 m fixes proves nothing")
    }

    // ── The recorded ride testifies [DET-BT-A-PARK-IS-CONFIRMED-NOT-ASSUMED-001] ────────────────
    //
    // Since the trip recorder exists, the engagement's own fixes outrank the cached connect fix:
    // measured DURING the ride by our stream, no cached gap for a walk to hide in.

    /** A recorder fix [meters] from base, stamped [afterConnectMs] into the engagement. */
    private fun ridePoint(meters: Double, afterConnectMs: Long, accuracy: Float = 10f) =
        fix(meters = meters, accuracy = accuracy, timestamp = CONNECTED_AT_MS + afterConnectMs)

    @Test
    fun should_proveRideFromTheRecordedRoute_when_noFixWasCachedAtConnect() {
        // The exact case the connect-fix witness reported Unmeasurable(NO_FIX) on: no cached
        // origin — but the recorder measured the whole drive.
        val proof = useCase.evaluateRideDisplacement(
            origin = null,
            connectedAtMs = CONNECTED_AT_MS,
            candidate = candidate(atMs = CONNECTED_AT_MS + 600_000L, meters = 400.0),
            recordedRide = listOf(ridePoint(0.0, afterConnectMs = 5_000L), ridePoint(350.0, afterConnectMs = 590_000L)),
        )
        assertIs<BtRideProof.Moved>(proof, "the ride's own fixes size the ride — no cached origin needed")
        assertEquals(BtRideEvidence.RECORDED_ROUTE, proof.evidence)
    }

    @Test
    fun should_proveRideFromTheRecordedRoute_when_theCachedOriginIsStale() {
        val proof = useCase.evaluateRideDisplacement(
            origin = origin(ageMs = config.btConnectFixMaxAgeMs + 1),
            connectedAtMs = CONNECTED_AT_MS,
            candidate = candidate(atMs = CONNECTED_AT_MS + 600_000L, meters = 400.0),
            recordedRide = listOf(ridePoint(0.0, afterConnectMs = 5_000L), ridePoint(350.0, afterConnectMs = 590_000L)),
        )
        assertIs<BtRideProof.Moved>(proof, "a stale cached fix must not out-vote the measured ride")
        assertEquals(BtRideEvidence.RECORDED_ROUTE, proof.evidence)
    }

    @Test
    fun should_letTheRecordedRouteRefuseTheRide_when_itsEndpointsStayedPut() {
        // The route is the BETTER witness in both directions: its DidNotMove is terminal even when
        // a far-away stale-ish cached origin would have "proven" a ride nobody drove.
        val proof = useCase.evaluateRideDisplacement(
            origin = origin(meters = 4_000.0),
            connectedAtMs = CONNECTED_AT_MS,
            candidate = candidate(atMs = CONNECTED_AT_MS + 600_000L, meters = 10.0),
            recordedRide = listOf(ridePoint(0.0, afterConnectMs = 5_000L), ridePoint(12.0, afterConnectMs = 590_000L)),
        )
        assertIs<BtRideProof.DidNotMove>(proof, "the engagement's own measurements outrank the cached fix")
        assertEquals(BtRideEvidence.RECORDED_ROUTE, proof.evidence)
    }

    @Test
    fun should_ignoreRecordedPointsFromBeforeTheConnect_when_sizingTheRide() {
        // A leftover of the PREVIOUS trip (cleared late, gap not yet hit) must never size this one:
        // with the pre-connect point filtered out, only ONE usable point remains → fall back to the
        // connect-fix witness, which here says the car never left.
        val proof = useCase.evaluateRideDisplacement(
            origin = origin(),
            connectedAtMs = CONNECTED_AT_MS,
            candidate = candidate(atMs = CONNECTED_AT_MS + 600_000L, meters = 6.0),
            recordedRide = listOf(
                fix(meters = 900.0, timestamp = CONNECTED_AT_MS - 60_000L), // previous trip's tail
                ridePoint(6.0, afterConnectMs = 5_000L),
            ),
        )
        assertIs<BtRideProof.DidNotMove>(proof)
        assertEquals(BtRideEvidence.CONNECT_FIX, proof.evidence, "one usable point is not a ride — fallback decides")
    }

    @Test
    fun should_sizeTheRideToItsLastRecordedPoint_when_theHuntNeverProducedACandidate() {
        // The GPS-timeout rescue: no candidate, no cached origin — the route alone proves the car
        // went somewhere, so the caller can ASK instead of losing the park in silence.
        val proof = useCase.evaluateRideDisplacement(
            origin = null,
            connectedAtMs = CONNECTED_AT_MS,
            candidate = null,
            recordedRide = listOf(ridePoint(0.0, afterConnectMs = 5_000L), ridePoint(500.0, afterConnectMs = 590_000L)),
        )
        assertIs<BtRideProof.Moved>(proof, "candidate-less sizing ends at the last recorded point")
        assertEquals(BtRideEvidence.RECORDED_ROUTE, proof.evidence)
    }

    @Test
    fun should_reportUnmeasurable_when_neitherCandidateNorUsableRouteExists() {
        // Timeout with an empty store: nothing measured anything — doubt, never permission.
        val proof = useCase.evaluateRideDisplacement(
            origin = origin(),
            connectedAtMs = CONNECTED_AT_MS,
            candidate = null,
            recordedRide = emptyList(),
        )
        assertEquals(BtRideProof.Unmeasurable(BtRideOriginGap.NO_FIX), proof)
    }

    private companion object {
        const val BASE_LAT = 36.6024
        const val BASE_LON = -6.2766
        const val METERS_PER_DEGREE_LAT = 111_320.0

        /** Wall-clock of the CONNECT the ride-proof tests measure from. Non-zero on purpose: a
         *  zero epoch would let an unstamped fix pass the age checks by accident. */
        const val CONNECTED_AT_MS = 1_756_000_000_000L
    }
}
