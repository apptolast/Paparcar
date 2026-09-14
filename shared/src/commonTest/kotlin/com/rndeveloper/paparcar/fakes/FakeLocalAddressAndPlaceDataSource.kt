package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.geocoder.LocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.domain.model.AddressAndPlace

class FakeLocalAddressAndPlaceDataSource : LocalAddressAndPlaceDataSource {
    private val cache = mutableMapOf<Pair<Double, Double>, AddressAndPlace>()

    /** Every write, in order — lets tests assert cache purity (what got cached and sealed). */
    val puts = mutableListOf<Pair<AddressAndPlace, Boolean>>()

    /** What [getNearest] answers — the fake's stand-in for a nearby cached cell. */
    var nearestResult: AddressAndPlace? = null

    /**
     * A pre-sealed cell that answers EVERY coordinate — the fake's stand-in for "this point's cell
     * was already measured by somebody else". [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
     */
    var cached: AddressAndPlace? = null

    override suspend fun get(lat: Double, lon: Double): AddressAndPlace? =
        cached ?: cache[Pair(lat, lon)]

    override suspend fun getNearest(lat: Double, lon: Double): AddressAndPlace? = nearestResult

    override suspend fun put(lat: Double, lon: Double, info: AddressAndPlace, poiChecked: Boolean) {
        puts += info to poiChecked
        if (poiChecked) cache[Pair(lat, lon)] = info
    }

    override suspend fun evictExpired() = Unit
}
