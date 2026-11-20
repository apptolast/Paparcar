package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.detection.PendingSpotRelease
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] May a boarding tell the neighbourhood a plaza is about
 * to free up?
 */
class EvaluateSpotReleaseNominationUseCaseTest {

    @Test
    fun should_preservePointPublicationPolicy_when_pinHasApproximateProvenance() {
        val point = session().copy(detectionPath = "closed_approximate_pin")
        assertIs<SpotReleaseNomination.Publish>(evaluate(session = point))
        assertIs<SpotReleaseNomination.Skip>(evaluate(session = point.copy(zoneRadiusMeters = 60f)))
    }

    private val useCase = EvaluateSpotReleaseNominationUseCase()
    private val carLocation = GpsPoint(40.0, -3.0, accuracy = 5f, timestamp = 1_000L, speed = 0f)

    private fun session(
        id: String = "session-1",
        zoneRadiusMeters: Float? = null,
        provisionalDepartureAtMs: Long? = null,
        isActive: Boolean = true,
    ) = UserParking(
        id = id,
        userId = "u",
        vehicleId = "veh-A",
        location = carLocation,
        isActive = isActive,
        zoneRadiusMeters = zoneRadiusMeters,
        provisionalDepartureAtMs = provisionalDepartureAtMs,
    )

    private fun evaluate(
        session: UserParking? = session(),
        pending: PendingSpotRelease? = null,
        privateZones: List<Zone> = emptyList(),
        boardingIsCredible: Boolean = true,
        nowMs: Long = 10_000L,
    ) = useCase(session, pending, privateZones, boardingIsCredible, nowMs)

    @Test
    fun should_publish_when_aCredibleBoardingHappensOnALivePlaza() {
        val verdict = evaluate()

        val publish = assertIs<SpotReleaseNomination.Publish>(verdict)
        assertEquals("session-1", publish.session.id)
    }

    @Test
    fun should_sayNothing_when_theLaneDoesNotVouchForTheBoarding() {
        // The flapping Bluetooth dongle of issue #131: ~20 connect/disconnect cycles a day. Without
        // this, every one of them would plant a plaza nobody is leaving.
        val verdict = evaluate(boardingIsCredible = false)

        assertEquals(
            EvaluateSpotReleaseNominationUseCase.REASON_NOT_A_BOARDING,
            assertIs<SpotReleaseNomination.Skip>(verdict).reason,
        )
    }

    @Test
    fun should_sayNothing_when_theBoardedCarHoldsNoPlaza() {
        assertEquals(
            EvaluateSpotReleaseNominationUseCase.REASON_NO_PLAZA,
            assertIs<SpotReleaseNomination.Skip>(evaluate(session = null)).reason,
        )
        assertEquals(
            EvaluateSpotReleaseNominationUseCase.REASON_NO_PLAZA,
            assertIs<SpotReleaseNomination.Skip>(evaluate(session = session(isActive = false))).reason,
        )
    }

    @Test
    fun should_sayNothing_when_aDeducedDepartureAlreadyPublishedThisPlaza() {
        val verdict = evaluate(session = session(provisionalDepartureAtMs = 5_000L))

        assertEquals(
            EvaluateSpotReleaseNominationUseCase.REASON_ALREADY_PUBLISHED,
            assertIs<SpotReleaseNomination.Skip>(verdict).reason,
        )
    }

    @Test
    fun should_sayNothing_when_aLiveNominationForThisPlazaIsAlreadyOut() {
        val verdict = evaluate(
            pending = PendingSpotRelease("session-1", "veh-A", nominatedAtMs = 9_000L),
        )

        assertEquals(
            EvaluateSpotReleaseNominationUseCase.REASON_ALREADY_NOMINATED,
            assertIs<SpotReleaseNomination.Skip>(verdict).reason,
        )
    }

    @Test
    fun should_stillSayNothing_when_theLinkDropsAndComesBackLongAfterTheFirstNomination() {
        // ⛔ The one that matters for the Bluetooth lane, and the one this used to get WRONG: the
        // gate was bounded by the releasing TTL, so a second CONNECT four minutes later published
        // the plaza again. A parked car whose link drops and returns — a dongle losing the phone, a
        // head unit waking up — is not somebody boarding twice. Same car, same space, never moved.
        //
        // The upstream anti-oscillation gate cannot catch this: it only sees a reconnect while the
        // hunt is still open (~30 s). A drop that returns ten minutes later walks straight past it.
        val nominatedAt = 1_000L
        val verdict = evaluate(
            pending = PendingSpotRelease("session-1", "veh-A", nominatedAtMs = nominatedAt),
            nowMs = nominatedAt + 10 * 60 * 1_000L,
        )

        assertEquals(
            EvaluateSpotReleaseNominationUseCase.REASON_ALREADY_NOMINATED,
            assertIs<SpotReleaseNomination.Skip>(verdict).reason,
            "a plaza is released once; after that it is promoted or withdrawn, never re-announced",
        )
    }

    @Test
    fun should_publish_when_anotherPlazaIsTheOneAlreadyNominated() {
        val verdict = evaluate(
            pending = PendingSpotRelease("some-other-session", "veh-B", nominatedAtMs = 9_000L),
        )

        assertIs<SpotReleaseNomination.Publish>(verdict)
    }

    @Test
    fun should_sayNothing_when_theCarIsParkedInsideAPrivateZone() {
        // Asked through the same gate the real publication uses, of the zones as they are NOW.
        // [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001]
        val garage = Zone(
            id = "z", userId = "u", name = "Casa",
            lat = carLocation.latitude, lon = carLocation.longitude,
            iconKey = "home", createdAt = 0L, radiusMeters = 60f, isPrivate = true,
        )

        val verdict = evaluate(privateZones = listOf(garage))

        assertEquals("private_zone", assertIs<SpotReleaseNomination.Skip>(verdict).reason)
    }

    @Test
    fun should_sayNothing_when_theParkItselfIsOnlyKnownToARadius() {
        // A releasing AREA stacks two doubts — "somewhere around here" AND "not free yet" — which
        // is more than a stranger can act on. Zone parks still publish at the real departure.
        val verdict = evaluate(session = session(zoneRadiusMeters = 45f))

        assertEquals(
            EvaluateSpotReleaseNominationUseCase.REASON_APPROXIMATE_PARK,
            assertIs<SpotReleaseNomination.Skip>(verdict).reason,
        )
    }
}
