package com.rndeveloper.paparcar.domain.geocoder

import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one question this file exists to answer: **does a POI lookup that never happened look
 * different from one that found nothing?** [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
 *
 * Until `AddressAndPlace.poiChecked` existed it did not: both ended the flow with the same single
 * address emission and the same `placeInfo == null`, so Android's worker returned `success` and
 * iOS's drainer dropped the op — and `success` is terminal on both. A session parked 14 m from a
 * supermarket kept its street for good (field 14-09-2026, El Puerto de Santa María, with three of
 * four Overpass calls answering 504).
 *
 * ⛔ The two tests that carry the whole ticket are [should_retry_when_theLookupNeverAnswered] and
 * [should_complete_when_theLookupAnsweredThereIsNoPlace]. They are deliberately identical except
 * for the one emission that separates "I could not ask" from "I asked": if that emission ever
 * stops being made, they disagree and one of them fails.
 */
class ParkingEnrichmentTest {

    private val repository = FakeUserParkingRepository(initialSession = session())

    // ── The pair that IS the invariant ────────────────────────────────────────

    @Test
    fun should_retry_when_theLookupNeverAnswered() = runTest {
        // Overpass down: phase 1 emits the street, phase 2 emits NOTHING.
        val done = enrichParkingSession(
            sessionId = SESSION_ID,
            answers = flowOf(phaseOneAddress()),
            repository = repository,
        )

        assertFalse(done, "a lookup that never reached Overpass was reported as finished")
    }

    @Test
    fun should_complete_when_theLookupAnsweredThereIsNoPlace() = runTest {
        val done = enrichParkingSession(
            sessionId = SESSION_ID,
            answers = flowOf(phaseOneAddress(), phaseTwoAnswer(place = null)),
            repository = repository,
        )

        assertTrue(done, "a measured 'no place within 40 m' is an answer, not a reason to re-ask")
    }

    // ── Everything the retry must NOT cost ────────────────────────────────────

    @Test
    fun should_keepTheStreetItAlreadyHas_when_theLookupNeverAnswered() = runTest {
        enrichParkingSession(SESSION_ID, flowOf(phaseOneAddress()), repository)

        // Asking again must not mean the user stares at a blank peek meanwhile: phase 1's street is
        // stamped on the way through, and only the POI half is outstanding.
        val stored = repository.getSessionById(SESSION_ID)
        assertEquals(STREET, stored?.address)
        assertNull(stored?.placeInfo)
    }

    @Test
    fun should_stampTheName_when_aPlaceIsFound() = runTest {
        val done = enrichParkingSession(
            sessionId = SESSION_ID,
            answers = flowOf(phaseOneAddress(), phaseTwoAnswer(place = SUPERMARKET)),
            repository = repository,
        )

        assertTrue(done)
        assertEquals(SUPERMARKET, repository.getSessionById(SESSION_ID)?.placeInfo)
    }

    @Test
    fun should_writeOnce_when_theAnswerIsNoPlace() = runTest {
        enrichParkingSession(
            sessionId = SESSION_ID,
            answers = flowOf(phaseOneAddress(), phaseTwoAnswer(place = null)),
            repository = repository,
        )

        // The second emission carries nothing the first did not. Writing it would re-dirty the row
        // and enqueue a second identical remote push — on MOST parks, since most have no POI.
        assertEquals(1, repository.addressAndPlaceWrites.size, "the no-place answer was written twice")
    }

    @Test
    fun should_writeTwice_when_aPlaceArrivesAfterTheStreet() = runTest {
        enrichParkingSession(
            sessionId = SESSION_ID,
            answers = flowOf(phaseOneAddress(), phaseTwoAnswer(place = SUPERMARKET)),
            repository = repository,
        )

        // The skip above must not swallow the upgrade: street first, then the name on top.
        assertEquals(2, repository.addressAndPlaceWrites.size)
        assertNull(repository.addressAndPlaceWrites.first().second)
        assertEquals(SUPERMARKET, repository.addressAndPlaceWrites.last().second)
    }

    // ── A borrowed answer settles nothing ─────────────────────────────────────

    @Test
    fun should_retryAndWriteNothing_when_onlyABorrowedNeighbourAnswered() = runTest {
        // Offline: the address is a neighbouring cell's street, up to 250 m away, and its cell's
        // seal says nothing about THIS point. [GEO-CACHE-ANSWERS-NEARBY-001]
        val borrowed = AddressAndPlace(
            address = AddressInfo(street = "Calle Vecina", city = "Madrid", region = null, country = "ES"),
            placeInfo = null,
            approximate = true,
            poiChecked = true,
        )

        val done = enrichParkingSession(SESSION_ID, flowOf(borrowed), repository)

        assertFalse(done, "a borrowed neighbour was accepted as this point's answer")
        assertEquals(emptyList(), repository.addressAndPlaceWrites)
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun phaseOneAddress() = AddressAndPlace(
        address = STREET,
        placeInfo = null,
        poiChecked = false,
    )

    private fun phaseTwoAnswer(place: PlaceInfo?) = AddressAndPlace(
        address = STREET,
        placeInfo = place,
        poiChecked = true,
    )

    private companion object {
        const val SESSION_ID = "session-1"

        val STREET = AddressInfo(
            street = "Diseminado 2.4 50",
            city = "El Puerto de Santa María",
            region = "Andalucía",
            country = "España",
            countryCode = "es",
            province = "Cádiz",
        )

        val SUPERMARKET = PlaceInfo(name = "Supersol", category = PlaceCategory.SUPERMARKET)

        fun session() = UserParking(
            id = SESSION_ID,
            userId = "user-1",
            vehicleId = "vehicle-1",
            location = GpsPoint(
                latitude = 36.60266,
                longitude = -6.25985,
                accuracy = 8f,
                timestamp = 1L,
                speed = 0f,
            ),
        )
    }
}
