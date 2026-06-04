@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.data.geocoder

import io.apptolast.paparcar.data.datasource.local.room.GeocoderCacheDao
import io.apptolast.paparcar.data.datasource.local.room.GeocoderCacheEntity
import io.apptolast.paparcar.domain.geocoder.LocalLocationInfoDataSource
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.LocationInfo
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import kotlin.math.roundToInt
import kotlin.time.Clock

class RoomLocalLocationInfoDataSource(
    private val dao: GeocoderCacheDao,
) : LocalLocationInfoDataSource {

    override suspend fun get(lat: Double, lon: Double): LocationInfo? {
        val entity = dao.getByKey(cacheKey(lat, lon)) ?: return null
        if (Clock.System.now().toEpochMilliseconds() - entity.cachedAt > CACHE_TTL_MS) return null
        if (!entity.poiChecked) return null
        return entity.toLocationInfo()
    }

    override suspend fun put(lat: Double, lon: Double, info: LocationInfo, poiChecked: Boolean) {
        dao.upsert(
            GeocoderCacheEntity(
                locationKey = cacheKey(lat, lon),
                addressStreet = info.address.street,
                addressCity = info.address.city,
                addressRegion = info.address.region,
                addressCountry = info.address.country,
                addressCountryCode = info.address.countryCode,
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

    private fun GeocoderCacheEntity.toLocationInfo(): LocationInfo {
        val address = AddressInfo(
            street = addressStreet,
            city = addressCity,
            region = addressRegion,
            country = addressCountry,
            countryCode = addressCountryCode,
        )
        val placeInfoName = placeInfoName
        val placeInfoCategory = placeInfoCategory
        val placeInfo = if (placeInfoName != null && placeInfoCategory != null) {
            val category = runCatching { PlaceCategory.valueOf(placeInfoCategory) }
                .getOrDefault(PlaceCategory.OTHER)
            PlaceInfo(name = placeInfoName, category = category)
        } else null
        return LocationInfo(address = address, placeInfo = placeInfo)
    }

    private companion object {
        const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1_000
        const val CACHE_PRECISION = 10_000

        fun cacheKey(lat: Double, lon: Double): String {
            val latKey = (lat * CACHE_PRECISION).roundToInt()
            val lonKey = (lon * CACHE_PRECISION).roundToInt()
            return "${latKey}_${lonKey}"
        }
    }
}
