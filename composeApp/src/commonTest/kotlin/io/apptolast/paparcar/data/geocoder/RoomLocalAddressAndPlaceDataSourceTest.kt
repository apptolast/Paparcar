package io.apptolast.paparcar.data.geocoder

import io.apptolast.paparcar.data.datasource.local.room.GeocoderCacheEntity
import io.apptolast.paparcar.data.geocoder.RoomLocalAddressAndPlaceDataSource.Companion.cacheKey
import io.apptolast.paparcar.data.geocoder.RoomLocalAddressAndPlaceDataSource.Companion.pickNearestCell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [GEO-CACHE-ANSWERS-NEARBY-001] — the offline near-miss pick over the cached cells. */
class RoomLocalAddressAndPlaceDataSourceTest {

    private fun cell(lat: Double, lon: Double, street: String, key: String = cacheKey(lat, lon)) =
        GeocoderCacheEntity(
            locationKey = key,
            addressStreet = street,
            addressCity = "Madrid",
            addressRegion = null,
            addressCountry = "ES",
            addressCountryCode = "ES",
            placeInfoName = null,
            placeInfoCategory = null,
            cachedAt = 0L,
            poiChecked = true,
        )

    @Test
    fun should_pickTheClosestCell_when_severalAreInRange() {
        // ~33 m north vs ~111 m north of the asked point.
        val near = cell(40.4171, -3.7038, "Calle Cercana")
        val far = cell(40.4178, -3.7038, "Calle Lejana")

        val picked = pickNearestCell(listOf(far, near), lat = 40.4168, lon = -3.7038)

        assertEquals("Calle Cercana", picked?.addressStreet)
    }

    @Test
    fun should_answerNothing_when_everyCellIsBeyondTheRadius() {
        // ~333 m north — an honest "near X" cannot stretch that far.
        val beyond = cell(40.4198, -3.7038, "Calle Remota")

        assertNull(pickNearestCell(listOf(beyond), lat = 40.4168, lon = -3.7038))
    }

    @Test
    fun should_skipCells_when_theirKeyDoesNotParse() {
        val broken = cell(40.4168, -3.7038, "Calle Rota", key = "garbage")
        val valid = cell(40.4171, -3.7038, "Calle Cercana")

        val picked = pickNearestCell(listOf(broken, valid), lat = 40.4168, lon = -3.7038)

        assertEquals("Calle Cercana", picked?.addressStreet)
    }

    @Test
    fun should_answerNothing_when_thereAreNoCells() {
        assertNull(pickNearestCell(emptyList(), lat = 40.4168, lon = -3.7038))
    }
}
