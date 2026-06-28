package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShouldArmFromVehicleEnterUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val useCase = ShouldArmFromVehicleEnterUseCase(config)

    private fun point(lat: Double, lon: Double, accuracy: Float = 5f) =
        GpsPoint(latitude = lat, longitude = lon, accuracy = accuracy, timestamp = 0L, speed = 0f)

    private fun session(lat: Double, lon: Double, size: VehicleSize? = null, accuracy: Float = 5f) =
        UserParking(id = "p", location = point(lat, lon, accuracy), sizeCategory = size)

    @Test
    fun should_not_arm_when_no_active_session() {
        val decision = useCase(currentFix = point(40.0, -3.0), parkedSession = null)
        assertIs<VehicleEnterArmDecision.NoActiveSession>(decision)
    }

    @Test
    fun should_not_arm_when_no_fix_available() {
        val decision = useCase(currentFix = null, parkedSession = session(40.0, -3.0))
        assertIs<VehicleEnterArmDecision.NoFix>(decision)
    }

    @Test
    fun should_arm_when_within_proximity_of_parked_car() {
        // ~22 m north of the parked car — well within the default ~87 m effective radius.
        val decision = useCase(currentFix = point(40.0002, -3.0), parkedSession = session(40.0, -3.0))
        assertIs<VehicleEnterArmDecision.Arm>(decision)
        assertTrue(decision.distanceMeters <= config.geofenceRadiusFor(null, 5f))
    }

    @Test
    fun should_not_arm_when_boarded_far_from_parked_car() {
        // ~330 m north — beyond any geofence radius = a bus / taxi / friend's car, not my car.
        val decision = useCase(currentFix = point(40.003, -3.0), parkedSession = session(40.0, -3.0))
        assertIs<VehicleEnterArmDecision.TooFar>(decision)
    }

    @Test
    fun proximity_radius_tracks_the_per_vehicle_geofence_radius() {
        // Same ~100 m fix: inside a VAN's larger geofence (~127 m), outside a MOTORCYCLE's (~67 m).
        val fix = point(40.0009, -3.0)
        assertIs<VehicleEnterArmDecision.Arm>(
            useCase(currentFix = fix, parkedSession = session(40.0, -3.0, VehicleSize.VAN_HIGH)),
        )
        assertIs<VehicleEnterArmDecision.TooFar>(
            useCase(currentFix = fix, parkedSession = session(40.0, -3.0, VehicleSize.MOTORCYCLE)),
        )
    }
}
