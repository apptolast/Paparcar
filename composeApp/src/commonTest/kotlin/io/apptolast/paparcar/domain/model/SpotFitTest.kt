package io.apptolast.paparcar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SpotFitTest {

    private fun spot(size: VehicleSize?, body: CarbodyType? = null): Spot =
        Spot(
            id = "s1",
            location = GpsPoint(0.0, 0.0, 0f, 0L, 0f),
            reportedBy = "u",
            sizeCategory = size,
            carbodyType = body,
        )

    private fun vehicle(size: VehicleSize, body: CarbodyType? = null): Vehicle =
        Vehicle(
            id = "v1",
            userId = "u",
            sizeCategory = size,
            carbodyType = body,
        )

    @Test
    fun should_returnUnknown_when_vehicleIsNull() {
        assertEquals(SpotFit.UNKNOWN, computeSpotFit(spot(VehicleSize.MEDIUM_SUV), vehicle = null))
    }

    @Test
    fun should_returnUnknown_when_spotHasNoSize() {
        val v = vehicle(VehicleSize.MEDIUM_SUV, CarbodyType.HATCHBACK_MEDIUM)
        assertEquals(SpotFit.UNKNOWN, computeSpotFit(spot(size = null), v))
    }

    @Test
    fun should_returnOptimal_when_sameCarbody() {
        val v = vehicle(VehicleSize.LARGE_SEDAN, CarbodyType.SUV_LARGE)
        val s = spot(VehicleSize.LARGE_SEDAN, CarbodyType.SUV_LARGE)
        assertEquals(SpotFit.OPTIMAL, computeSpotFit(s, v))
    }

    @Test
    fun should_returnFits_when_sameSizeButDifferentBody() {
        val v = vehicle(VehicleSize.MEDIUM_SUV, CarbodyType.HATCHBACK_MEDIUM)
        val s = spot(VehicleSize.MEDIUM_SUV, CarbodyType.SUV_MEDIUM)
        assertEquals(SpotFit.FITS, computeSpotFit(s, v))
    }

    @Test
    fun should_returnFits_when_userSmallerThanSpot() {
        val v = vehicle(VehicleSize.MICRO_SMALL, CarbodyType.HATCHBACK_SMALL)
        val s = spot(VehicleSize.LARGE_SEDAN, CarbodyType.SEDAN)
        assertEquals(SpotFit.FITS, computeSpotFit(s, v))
    }

    @Test
    fun should_returnDoesNotFit_when_userLargerThanSpot() {
        val v = vehicle(VehicleSize.VAN_HIGH, CarbodyType.VAN_COMMERCIAL)
        val s = spot(VehicleSize.MICRO_SMALL, CarbodyType.HATCHBACK_SMALL)
        assertEquals(SpotFit.DOES_NOT_FIT, computeSpotFit(s, v))
    }

    @Test
    fun should_returnDoesNotFit_when_sameSizeButUserWiderThanSpotBody() {
        // Same length envelope (MEDIUM_SUV), but user is a wide SUV_MEDIUM (2.40 m)
        // and the spot was freed by a narrower HATCHBACK_MEDIUM (2.20 m).
        val v = vehicle(VehicleSize.MEDIUM_SUV, CarbodyType.SUV_MEDIUM)
        val s = spot(VehicleSize.MEDIUM_SUV, CarbodyType.HATCHBACK_MEDIUM)
        assertEquals(SpotFit.DOES_NOT_FIT, computeSpotFit(s, v))
    }
}
