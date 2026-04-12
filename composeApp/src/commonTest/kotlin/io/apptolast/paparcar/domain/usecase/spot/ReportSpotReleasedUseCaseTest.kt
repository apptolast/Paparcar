package io.apptolast.paparcar.domain.usecase.spot

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import io.apptolast.paparcar.fakes.FakeGeocoderDataSource
import io.apptolast.paparcar.fakes.FakePlacesDataSource
import io.apptolast.paparcar.fakes.FakeReportSpotScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportSpotReleasedUseCaseTest {

    private val geocoder = FakeGeocoderDataSource()
    private val places = FakePlacesDataSource()
    private val scheduler = FakeReportSpotScheduler()
    private val useCase = ReportSpotReleasedUseCase(
        reportSpotScheduler = scheduler,
        getLocationInfo = GetLocationInfoUseCase(geocoder, places),
    )

    // ── Schedule always called ─────────────────────────────────────────────────

    @Test
    fun `should_callScheduleOnce_when_invoked`() = runTest {
        useCase(40.416775, -3.703790, "spot-1")

        assertEquals(1, scheduler.scheduleCallCount)
    }

    @Test
    fun `should_passCoordinates_to_scheduler`() = runTest {
        useCase(40.416775, -3.703790, "spot-1")

        assertEquals(40.416775, scheduler.lastLat)
        assertEquals(-3.703790, scheduler.lastLon)
    }

    @Test
    fun `should_passSpotId_to_scheduler`() = runTest {
        useCase(40.416775, -3.703790, "spot-42")

        assertEquals("spot-42", scheduler.lastSpotId)
    }

    // ── Geocoding ─────────────────────────────────────────────────────────────

    @Test
    fun `should_passAddress_when_geocodingSucceeds`() = runTest {
        val address = AddressInfo(street = "Calle Mayor", city = "Madrid", region = null, country = "ES")
        geocoder.addressResult = Result.success(address)

        useCase(40.416775, -3.703790, "spot-1")

        assertEquals(address, scheduler.lastAddress)
    }

    @Test
    fun `should_passNullAddress_when_geocodingFails`() = runTest {
        geocoder.addressResult = Result.failure(RuntimeException("Geocoder unavailable"))

        useCase(40.416775, -3.703790, "spot-1")

        assertEquals(1, scheduler.scheduleCallCount)
        assertNull(scheduler.lastAddress)
    }

    @Test
    fun `should_passPlaceInfo_when_placeFound`() = runTest {
        val place = PlaceInfo(name = "El Corte Inglés", category = PlaceCategory.MALL)
        places.placeResult = Result.success(place)

        useCase(40.416775, -3.703790, "spot-1")

        assertEquals(place, scheduler.lastPlaceInfo)
    }

    // ── Phase 4 params ────────────────────────────────────────────────────────

    @Test
    fun `should_passSpotType_and_confidence`() = runTest {
        useCase(40.416775, -3.703790, "spot-1", SpotType.MANUAL_REPORT, confidence = 0.8f)

        assertEquals(SpotType.MANUAL_REPORT, scheduler.lastSpotType)
        assertEquals(0.8f, scheduler.lastConfidence)
    }

    @Test
    fun `should_passSizeCategory`() = runTest {
        useCase(40.416775, -3.703790, "spot-1", sizeCategory = VehicleSize.SMALL)

        assertEquals(VehicleSize.SMALL, scheduler.lastSizeCategory)
    }

    @Test
    fun `should_useAutoDetected_as_defaultSpotType`() = runTest {
        useCase(40.416775, -3.703790, "spot-1")

        assertEquals(SpotType.AUTO_DETECTED, scheduler.lastSpotType)
    }
}
