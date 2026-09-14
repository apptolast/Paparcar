package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.repository.AddressAndPlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeAddressAndPlaceRepository(
    initialInfo: AddressAndPlace = AddressAndPlace(
        address = AddressInfo(null, null, null, null),
        placeInfo = null,
    ),
) : AddressAndPlaceRepository {

    /** Mutate per-test to control what address/place the repository returns. */
    var addressResult: Result<AddressInfo> = Result.success(initialInfo.address)
    var placeInfo: com.rndeveloper.paparcar.domain.model.PlaceInfo? = initialInfo.placeInfo
    /** Delay before the first emission — lets tests race a slow geocode against re-asks. */
    var delayMs: Long = 0
    /** Emit every answer flagged approximate — the offline borrowed-neighbour case. */
    var approximate: Boolean = false
    /** Every request, in order — lets tests assert cancellation/dedup behaviour. */
    val calls = mutableListOf<Pair<Double, Double>>()

    /** The `persisting` flag of every request, in order. [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001] */
    val persistingCalls = mutableListOf<Boolean>()

    /**
     * False = Overpass never answered, so phase 2 emits NOTHING — the case that is NOT the same as
     * "no place here" and that the real repository distinguishes with `poiChecked`. Default true
     * because most tests want a lookup that completed. [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
     */
    var poiAnswered: Boolean = true

    override fun getAddressAndPlace(
        lat: Double,
        lon: Double,
        persisting: Boolean,
    ): Flow<AddressAndPlace> = flow {
        calls += lat to lon
        persistingCalls += persisting
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        val address = addressResult.getOrElse { AddressInfo(null, null, null, null) }
        emit(AddressAndPlace(address = address, placeInfo = null, approximate = approximate))
        // Mirrors the real contract: the second emission is made on ANSWERED, not on "found".
        if (poiAnswered) {
            emit(
                AddressAndPlace(
                    address = address,
                    placeInfo = placeInfo,
                    approximate = approximate,
                    poiChecked = true,
                ),
            )
        }
    }
}
