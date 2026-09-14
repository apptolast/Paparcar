@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.geocoder

import com.rndeveloper.paparcar.data.datasource.local.room.GeocoderCacheDao
import com.rndeveloper.paparcar.data.datasource.local.room.GeocoderCacheEntity
import com.rndeveloper.paparcar.domain.geocoder.LocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlin.math.roundToInt
import kotlin.time.Clock

class RoomLocalAddressAndPlaceDataSource(
    private val dao: GeocoderCacheDao,
) : LocalAddressAndPlaceDataSource {

    override suspend fun get(lat: Double, lon: Double): AddressAndPlace? {
        val entity = dao.getByKey(cacheKey(lat, lon)) ?: return null
        if (Clock.System.now().toEpochMilliseconds() - entity.cachedAt > CACHE_TTL_MS) return null
        if (!entity.poiChecked) return null
        return entity.toAddressAndPlace()
    }

    override suspend fun getNearest(lat: Double, lon: Double): AddressAndPlace? {
        val minCachedAt = Clock.System.now().toEpochMilliseconds() - CACHE_TTL_MS
        val nearest = pickNearestCell(dao.getStreetCells(minCachedAt), lat, lon) ?: return null
        // ⛔ `poiChecked = false` whatever the neighbour cell says. The seal is a statement about the
        // point that MEASURED it, and this one can sit up to MAX_NEAREST_DISTANCE_METERS away — the
        // same reason the borrowed answer lends its street and never its place. Carrying the
        // neighbour's `true` up would tell a persisting consumer "the POI question is settled here"
        // about a question nobody asked at this point.
        // [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001] [GEO-CACHE-ANSWERS-NEARBY-001]
        return nearest.toAddressAndPlace().copy(approximate = true, poiChecked = false)
    }

    override suspend fun put(lat: Double, lon: Double, info: AddressAndPlace, poiChecked: Boolean) {
        dao.upsert(
            GeocoderCacheEntity(
                locationKey = cacheKey(lat, lon),
                addressStreet = info.address.street,
                addressCity = info.address.city,
                addressRegion = info.address.region,
                addressCountry = info.address.country,
                addressCountryCode = info.address.countryCode,
                addressProvince = info.address.province,
                addressPostalCode = info.address.postalCode,
                placeInfoName = info.placeInfo?.name,
                placeInfoCategory = info.placeInfo?.category?.name,
                cachedAt = Clock.System.now().toEpochMilliseconds(),
                poiChecked = poiChecked,
            )
        )
    }

    override suspend fun evictExpired() {
        val expiryMs = Clock.System.now().toEpochMilliseconds() - CACHE_TTL_MS
        dao.evictExpired(expiryMs)
    }

    private fun GeocoderCacheEntity.toAddressAndPlace(): AddressAndPlace {
        val address = AddressInfo(
            street = addressStreet,
            city = addressCity,
            region = addressRegion,
            country = addressCountry,
            countryCode = addressCountryCode,
            province = addressProvince,
            postalCode = addressPostalCode,
        )
        val placeInfoName = placeInfoName
        val placeInfoCategory = placeInfoCategory
        val placeInfo = if (placeInfoName != null && placeInfoCategory != null) {
            val category = runCatching { PlaceCategory.valueOf(placeInfoCategory) }
                .getOrDefault(PlaceCategory.OTHER)
            PlaceInfo(name = placeInfoName, category = category)
        } else null
        // A cell carries its own seal upward: `get()` only ever serves sealed rows, so a cache hit
        // is a POI question already answered and a consumer that persists must not re-ask.
        // [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
        return AddressAndPlace(address = address, placeInfo = placeInfo, poiChecked = poiChecked)
    }

    companion object {
        private const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1_000
        private const val CACHE_PRECISION = 10_000

        /** How far a cached street may honestly stand in for the asked point ("Near X"). */
        private const val MAX_NEAREST_DISTANCE_METERS = 250.0

        fun cacheKey(lat: Double, lon: Double): String {
            val latKey = (lat * CACHE_PRECISION).roundToInt()
            val lonKey = (lon * CACHE_PRECISION).roundToInt()
            return "${latKey}_${lonKey}"
        }

        /**
         * Nearest candidate cell within [MAX_NEAREST_DISTANCE_METERS] of (lat, lon), or null.
         * Pure — the cell centre is recovered from the "${latKey}_${lonKey}" key, so a row whose
         * key doesn't parse (should not exist) is simply skipped. [GEO-CACHE-ANSWERS-NEARBY-001]
         */
        fun pickNearestCell(
            candidates: List<GeocoderCacheEntity>,
            lat: Double,
            lon: Double,
        ): GeocoderCacheEntity? = candidates
            .mapNotNull { entity ->
                val (cellLat, cellLon) = parseKey(entity.locationKey) ?: return@mapNotNull null
                entity to haversineMeters(lat, lon, cellLat, cellLon)
            }
            .filter { (_, meters) -> meters <= MAX_NEAREST_DISTANCE_METERS }
            .minByOrNull { (_, meters) -> meters }
            ?.first

        private fun parseKey(key: String): Pair<Double, Double>? {
            val separator = key.indexOf('_')
            if (separator <= 0) return null
            val latKey = key.substring(0, separator).toIntOrNull() ?: return null
            val lonKey = key.substring(separator + 1).toIntOrNull() ?: return null
            return latKey.toDouble() / CACHE_PRECISION to lonKey.toDouble() / CACHE_PRECISION
        }
    }
}
