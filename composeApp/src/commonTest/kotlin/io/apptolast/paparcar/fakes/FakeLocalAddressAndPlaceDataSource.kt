package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.geocoder.LocalAddressAndPlaceDataSource
import io.apptolast.paparcar.domain.model.AddressAndPlace

class FakeLocalAddressAndPlaceDataSource : LocalAddressAndPlaceDataSource {
    private val cache = mutableMapOf<Pair<Double, Double>, AddressAndPlace>()

    /** Every write, in order — lets tests assert cache purity (what got cached and sealed). */
    val puts = mutableListOf<Pair<AddressAndPlace, Boolean>>()

    override suspend fun get(lat: Double, lon: Double): AddressAndPlace? = cache[Pair(lat, lon)]

    override suspend fun put(lat: Double, lon: Double, info: AddressAndPlace, poiChecked: Boolean) {
        puts += info to poiChecked
        if (poiChecked) cache[Pair(lat, lon)] = info
    }

    override suspend fun evictExpired() = Unit
}
