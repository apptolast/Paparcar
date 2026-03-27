package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.UserParkingEntity
import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParkingSessionMapperTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val baseEntity = UserParkingEntity(
        id = "session-1",
        userId = "user-42",
        latitude = 40.4168,
        longitude = -3.7038,
        accuracy = 5f,
        timestamp = 1_700_000_000L,
        isActive = true,
    )

    private val baseParking = UserParking(
        id = "session-1",
        userId = "user-42",
        location = GpsPoint(
            latitude = 40.4168,
            longitude = -3.7038,
            accuracy = 5f,
            timestamp = 1_700_000_000L,
            speed = 0f,
        ),
        isActive = true,
    )

    // ── UserParkingEntity → Domain ────────────────────────────────────────────

    @Test
    fun `entity toDomain maps basic fields correctly`() {
        val domain = baseEntity.toDomain()

        assertEquals("session-1", domain.id)
        assertEquals("user-42", domain.userId)
        assertEquals(40.4168, domain.location.latitude)
        assertEquals(-3.7038, domain.location.longitude)
        assertEquals(true, domain.isActive)
    }

    @Test
    fun `entity toDomain produces null address when all address fields are null`() {
        val domain = baseEntity.toDomain()
        assertNull(domain.address)
    }

    @Test
    fun `entity toDomain produces AddressInfo when at least one field is present`() {
        val entity = baseEntity.copy(addressCity = "Madrid", addressCountry = "ES")
        val domain = entity.toDomain()

        assertNotNull(domain.address)
        assertEquals("Madrid", domain.address?.city)
        assertEquals("ES", domain.address?.country)
        assertNull(domain.address?.street)
    }

    @Test
    fun `entity toDomain produces null placeInfo when fields are null`() {
        assertNull(baseEntity.toDomain().placeInfo)
    }

    @Test
    fun `entity toDomain produces PlaceInfo when name and valid category present`() {
        val entity = baseEntity.copy(placeInfoName = "Repsol", placeInfoCategory = "FUEL")
        val domain = entity.toDomain()

        assertNotNull(domain.placeInfo)
        assertEquals("Repsol", domain.placeInfo?.name)
        assertEquals(PlaceCategory.FUEL, domain.placeInfo?.category)
    }

    @Test
    fun `entity toDomain produces null placeInfo for unknown category`() {
        val entity = baseEntity.copy(placeInfoName = "Test", placeInfoCategory = "UNKNOWN_CAT")
        assertNull(entity.toDomain().placeInfo)
    }

    // ── Domain → Entity ───────────────────────────────────────────────────────

    @Test
    fun `parking toEntity maps userId correctly`() {
        val entity = baseParking.toEntity()
        assertEquals("user-42", entity.userId)
    }

    @Test
    fun `parking toEntity maps address fields`() {
        val parking = baseParking.copy(
            address = AddressInfo(street = "Gran Vía", city = "Madrid", region = null, country = "ES"),
        )
        val entity = parking.toEntity()

        assertEquals("Gran Vía", entity.addressStreet)
        assertEquals("Madrid", entity.addressCity)
        assertNull(entity.addressRegion)
        assertEquals("ES", entity.addressCountry)
    }

    // ── Domain → Spot ─────────────────────────────────────────────────────────

    @Test
    fun `toSpot uses userId as reportedBy`() {
        val spot = baseParking.toSpot()
        assertEquals("user-42", spot.reportedBy)
    }

    @Test
    fun `toSpot preserves location`() {
        val spot = baseParking.toSpot()
        assertEquals(40.4168, spot.location.latitude)
        assertEquals(-3.7038, spot.location.longitude)
    }

    // ── Domain → ParkingHistoryDto ────────────────────────────────────────────

    @Test
    fun `toParkingHistoryDto includes userId`() {
        val dto = baseParking.toParkingHistoryDto()
        assertEquals("user-42", dto.userId)
    }

    @Test
    fun `toParkingHistoryDto maps address to dto`() {
        val parking = baseParking.copy(
            address = AddressInfo(street = null, city = "Barcelona", region = null, country = "ES"),
        )
        val dto = parking.toParkingHistoryDto()

        assertNotNull(dto.address)
        assertEquals("Barcelona", dto.address?.city)
    }

    // ── ParkingHistoryDto → Entity ────────────────────────────────────────────

    @Test
    fun `dto toEntity maps userId`() {
        val dto = ParkingHistoryDto(
            id = "s1",
            userId = "user-42",
            latitude = 40.0,
            longitude = -3.0,
        )
        assertEquals("user-42", dto.toEntity().userId)
    }

    @Test
    fun `dto toEntity maps nested address`() {
        val dto = ParkingHistoryDto(
            id = "s1",
            latitude = 40.0,
            longitude = -3.0,
            address = AddressDto(street = "Calle Mayor", city = "Madrid", region = null, country = "ES"),
        )
        val entity = dto.toEntity()

        assertEquals("Calle Mayor", entity.addressStreet)
        assertEquals("Madrid", entity.addressCity)
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    @Test
    fun `AddressInfo toAddressDto maps all fields`() {
        val info = AddressInfo(street = "s", city = "c", region = "r", country = "es")
        val dto = info.toAddressDto()

        assertEquals("s", dto.street)
        assertEquals("c", dto.city)
        assertEquals("r", dto.region)
        assertEquals("es", dto.country)
    }

    @Test
    fun `PlaceInfo toPlaceInfoDto maps name and category name`() {
        val info = PlaceInfo(name = "Shell", category = PlaceCategory.FUEL)
        val dto = info.toPlaceInfoDto()

        assertEquals("Shell", dto.name)
        assertEquals("FUEL", dto.category)
    }
}
