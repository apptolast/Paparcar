package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.fakes.FakeAddressAndPlaceRepository
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeReportSpotScheduler
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportManualSpotUseCaseTest {

    private val scheduler = FakeReportSpotScheduler()
    private val reportSpotReleased = ReportSpotReleasedUseCase(
        reportSpotScheduler = scheduler,
        getAddressAndPlace = GetAddressAndPlaceUseCase(FakeAddressAndPlaceRepository()),
        authRepository = FakeAuthRepository(FakeAuthRepository.authenticatedSession()),
    )

    private fun vehicle(id: String, isActive: Boolean, carbody: CarbodyType?) = Vehicle(
        id = id,
        userId = "user-1",
        brand = "Toyota",
        model = "Corolla",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        carbodyType = carbody,
        isActive = isActive,
    )

    @Test
    fun should_fall_back_to_active_vehicle_carbody_when_reporting() = runTest {
        val vehicleRepo = FakeVehicleRepository(
            defaultVehicle = vehicle("veh-A", isActive = false, carbody = CarbodyType.PICKUP),
            extraVehicles = listOf(vehicle("veh-B", isActive = true, carbody = CarbodyType.SEDAN)),
        )
        val useCase = ReportManualSpotUseCase(reportSpotReleased, vehicleRepo)

        val result = useCase(lat = 40.0, lon = -3.0, sizeCategory = VehicleSize.MICRO_SMALL, prefetched = null)

        assertTrue(result.isSuccess)
        assertEquals(1, scheduler.scheduleCallCount)
        // The ACTIVE vehicle's carbody wins, not the default one.
        assertEquals(CarbodyType.SEDAN, scheduler.lastCarbodyType)
        // Size is taken verbatim from the picker.
        assertEquals(VehicleSize.MICRO_SMALL, scheduler.lastSizeCategory)
        assertEquals(SpotType.MANUAL_REPORT, scheduler.lastSpotType)
        assertEquals(1f, scheduler.lastConfidence)
        assertTrue(scheduler.lastSpotId!!.startsWith("manual_"))
    }

    @Test
    fun should_report_null_carbody_when_no_active_vehicle() = runTest {
        val vehicleRepo = FakeVehicleRepository() // no vehicles registered
        val useCase = ReportManualSpotUseCase(reportSpotReleased, vehicleRepo)

        val result = useCase(lat = 40.0, lon = -3.0, sizeCategory = null, prefetched = null)

        assertTrue(result.isSuccess)
        assertNull(scheduler.lastCarbodyType)
        // Null size means the user explicitly picked "Indefinido" — never inferred.
        assertNull(scheduler.lastSizeCategory)
    }

    @Test
    fun should_fail_when_the_scheduler_throws() = runTest {
        scheduler.shouldThrow = true
        val useCase = ReportManualSpotUseCase(reportSpotReleased, FakeVehicleRepository())

        val result = useCase(lat = 40.0, lon = -3.0, sizeCategory = null, prefetched = null)

        assertTrue(result.isFailure)
    }
}
