package com.rndeveloper.paparcar.domain.usecase.notification

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.fakes.FakeAddressAndPlaceRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] What a question may call the place it is asking
 * about.
 *
 * Every case is the SAME asymmetry the detection doctrine runs on: naming no street costs the user
 * nothing, and naming the WRONG one costs them the answer — they read a road they are not on and
 * reply "not yet" to a park that really happened, or say yes to a place they never saw.
 */
class ResolveAskedStreetUseCaseTest {

    private val at = GpsPoint(36.6119, -6.2805, accuracy = 8f, timestamp = 0L, speed = 0f)

    private val repo = FakeAddressAndPlaceRepository()
    private val resolve = ResolveAskedStreetUseCase(GetAddressAndPlaceUseCase(repo))

    private fun geocoderSays(street: String?) {
        repo.addressResult = Result.success(
            AddressInfo(street, "El Puerto de Santa María", "Cádiz", "España"),
        )
    }

    @Test
    fun should_name_the_street_when_the_geocoder_answers_for_this_exact_spot() = runTest {
        geocoderSays("Calle Padornelo 3")

        assertEquals(
            "Calle Padornelo 3",
            resolve(at),
            "the house number is part of it — that is what makes the question answerable",
        )
    }

    @Test
    fun should_name_no_street_when_the_answer_is_borrowed_from_a_neighbouring_cell() = runTest {
        // GEO-CACHE-ANSWERS-NEARBY-001: offline, the repository lends the nearest cached street.
        // Useful as "near X" in a list; in a question that plants a pin it is a road the user is not
        // on, printed with a house number.
        geocoderSays("Calle Larga 14")
        repo.approximate = true

        assertNull(resolve(at), "a borrowed street is the most confident-looking way this could lie")
    }

    @Test
    fun should_name_no_street_when_the_geocoder_does_not_answer_in_time() = runTest {
        geocoderSays("Calle Padornelo 3")
        repo.delayMs = 30_000L

        assertNull(resolve(at), "the ask may not wait on a geocoder; a slow answer is no answer")
    }

    @Test
    fun should_name_no_street_when_the_geocoder_has_none() = runTest {
        geocoderSays(null)
        assertNull(resolve(at))

        geocoderSays("   ")
        assertNull(resolve(at), "blank is not a street")
    }

    @Test
    fun should_ignore_the_poi_name_and_keep_the_street() = runTest {
        // "did you park at Mercadona?" reads better, but the POI is the repository's slow Phase 2:
        // it would only ever appear when a previous visit cached it, so the SAME stop would be
        // worded differently on different days.
        geocoderSays("Calle Padornelo 3")
        repo.placeInfo = PlaceInfo(name = "Mercadona", category = PlaceCategory.SUPERMARKET)

        assertEquals("Calle Padornelo 3", resolve(at))
    }

    @Test
    fun should_not_geocode_at_all_when_there_is_no_place_to_ask_about() = runTest {
        geocoderSays("Calle Padornelo 3")

        assertNull(resolve(null))
        assertEquals(emptyList(), repo.calls, "no point, no lookup — not even a wasted one")
    }
}
