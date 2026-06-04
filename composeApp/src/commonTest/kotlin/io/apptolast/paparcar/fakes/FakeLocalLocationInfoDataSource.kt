package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.geocoder.LocalLocationInfoDataSource
import io.apptolast.paparcar.domain.model.LocationInfo

class FakeLocalLocationInfoDataSource : LocalLocationInfoDataSource {
    private val cache = mutableMapOf<Pair<Double, Double>, LocationInfo>()

    override suspend fun get(lat: Double, lon: Double): LocationInfo? = cache[Pair(lat, lon)]

    override suspend fun put(lat: Double, lon: Double, info: LocationInfo, poiChecked: Boolean) {
        if (poiChecked) cache[Pair(lat, lon)] = info
    }

    override suspend fun evictExpired() = Unit
}
