package io.apptolast.paparcar.domain.usecase.spot

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.VehicleSize
import com.apptolast.customlogin.domain.model.UserSession
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeAddressAndPlaceRepository
import io.apptolast.paparcar.fakes.FakeReportSpotScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportSpotReleasedUseCaseTest {

    private val addressAndPlaceRepo = FakeAddressAndPlaceRepository()
    private val scheduler = FakeReportSpotScheduler()
    private val auth = FakeAuthRepository(
        initialSession = UserSession(
            userId = "uid-1",
            email = "test@paparcar.io",
            displayName = "Carlos M.",
            photoUrl = null,
        )
    )
    private val useCase = ReportSpotReleasedUseCase(
        reportSpotScheduler = scheduler,
        getAddressAndPlace = GetAddressAndPlaceUseCase(addressAndPlaceRepo),
        authRepository = auth,
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
        addressAndPlaceRepo.addressResult = Result.success(address)

        useCase(40.416775, -3.703790, "spot-1")

        assertEquals(address, scheduler.lastAddress)
    }

    @Test
    fun `should_passEmptyAddress_when_geocodingFails`() = runTest {
        addressAndPlaceRepo.addressResult = Result.failure(RuntimeException("Geocoder unavailable"))

        useCase(40.416775, -3.703790, "spot-1")

        assertEquals(1, scheduler.scheduleCallCount)
        // GetAddressAndPlaceUseCase falls back to AddressInfo(null,null,null,null) on geocode failure
        assertEquals(AddressInfo(null, null, null, null), scheduler.lastAddress)
    }

    @Test
    fun `should_passPlaceInfo_when_placeFound`() = runTest {
        val place = PlaceInfo(name = "El Corte Inglés", category = PlaceCategory.MALL)
        addressAndPlaceRepo.placeInfo = place

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
        useCase(40.416775, -3.703790, "spot-1", sizeCategory = VehicleSize.MICRO_SMALL)

        assertEquals(VehicleSize.MICRO_SMALL, scheduler.lastSizeCategory)
    }

    @Test
    fun `should_useAutoDetected_as_defaultSpotType`() = runTest {
        useCase(40.416775, -3.703790, "spot-1")

        assertEquals(SpotType.AUTO_DETECTED, scheduler.lastSpotType)
    }

    // ── reportedBy = UID [AUDIT-RULES-001 C4] ─────────────────────────────────

    @Test
    fun `should_passUid_as_reportedBy_when_session_exists`() = runTest {
        // The spot's identity must be the reporter's UID (not their display name) so the
        // Firestore rules can authorise owner-only edit/delete.
        useCase(40.416775, -3.703790, "spot-1")

        assertEquals("uid-1", scheduler.lastReportedBy)
    }

    @Test
    fun `should_passNull_as_reportedBy_when_no_session`() = runTest {
        auth.emitState(com.apptolast.customlogin.domain.model.AuthState.Unauthenticated)
        val ucNoAuth = ReportSpotReleasedUseCase(
            reportSpotScheduler = scheduler,
            getAddressAndPlace = GetAddressAndPlaceUseCase(addressAndPlaceRepo),
            authRepository = auth,
        )

        ucNoAuth(40.416775, -3.703790, "spot-2")

        assertNull(scheduler.lastReportedBy)
    }
}
