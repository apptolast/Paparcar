@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.vehicle

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwapActiveVehicleFencesUseCaseTest {

    private val loc = GpsPoint(latitude = 40.0, longitude = -3.7, accuracy = 8f, timestamp = 0L, speed = 0f)

    private fun session(id: String, vehicleId: String) = UserParking(
        id = id, userId = "u", vehicleId = vehicleId, location = loc,
        geofenceId = id, isActive = true, sizeCategory = VehicleSize.MEDIUM_SUV,
    )

    private fun vehicle(id: String, bt: String? = null) =
        Vehicle(id = id, userId = "u", sizeCategory = VehicleSize.MEDIUM_SUV, bluetoothDeviceId = bt)

    private fun useCase(parking: FakeUserParkingRepository, vehicles: FakeVehicleRepository, geofence: FakeGeofenceManager) =
        SwapActiveVehicleFencesUseCase(
            userParkingRepository = parking,
            vehicleRepository = vehicles,
            geofenceService = geofence,
            config = ParkingDetectionConfig(),
        )

    @Test
    fun `swap removes the outgoing fence and registers the incoming one`() = runTest {
        val parking = FakeUserParkingRepository(initialSessions = listOf(session("sess-A", "veh-A"), session("sess-B", "veh-B")))
        val vehicles = FakeVehicleRepository(defaultVehicle = vehicle("veh-A"), extraVehicles = listOf(vehicle("veh-B")))
        val geofence = FakeGeofenceManager()

        useCase(parking, vehicles, geofence)(outgoingVehicleId = "veh-A", incomingVehicleId = "veh-B")

        assertEquals(listOf("sess-A"), geofence.removedIds)
        assertEquals(listOf("sess-B"), geofence.createdIds)
    }

    @Test
    fun `swap keeps a Bluetooth-paired outgoing vehicle's fence`() = runTest {
        // BT is identity — the paired car owns a fence regardless of the active flag, so it is never
        // swapped out. [VEH-ACTIVE-FENCE-001]
        val parking = FakeUserParkingRepository(initialSessions = listOf(session("sess-A", "veh-A"), session("sess-B", "veh-B")))
        val vehicles = FakeVehicleRepository(
            defaultVehicle = vehicle("veh-A", bt = "AA:BB:CC:DD:EE:FF"),
            extraVehicles = listOf(vehicle("veh-B")),
        )
        val geofence = FakeGeofenceManager()

        useCase(parking, vehicles, geofence)(outgoingVehicleId = "veh-A", incomingVehicleId = "veh-B")

        assertTrue(geofence.removedIds.isEmpty(), "BT-paired outgoing keeps its fence")
        assertEquals(listOf("sess-B"), geofence.createdIds)
    }

    @Test
    fun `swap registers nothing when the incoming vehicle is not parked`() = runTest {
        val parking = FakeUserParkingRepository(initialSessions = listOf(session("sess-A", "veh-A")))
        val vehicles = FakeVehicleRepository(defaultVehicle = vehicle("veh-A"), extraVehicles = listOf(vehicle("veh-B")))
        val geofence = FakeGeofenceManager()

        useCase(parking, vehicles, geofence)(outgoingVehicleId = "veh-A", incomingVehicleId = "veh-B")

        assertEquals(listOf("sess-A"), geofence.removedIds)
        assertTrue(geofence.createdIds.isEmpty(), "an unparked incoming vehicle has no fence to register")
    }
}
