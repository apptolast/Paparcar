package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.places.PlacesDataSource

class FakePlacesDataSource : PlacesDataSource {

    var placeResult: Result<PlaceInfo?> = Result.success(null)

    /** Every coordinate this lane was actually asked about, in order — the only way to prove a
     *  cache hit did NOT reach the network, and that a stored answer DID.
     *  [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001] */
    val asked = mutableListOf<Pair<Double, Double>>()

    override suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?> {
        asked += lat to lon
        return placeResult
    }
}
