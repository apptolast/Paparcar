@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.notification

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotifyParkingConfirmationUseCaseTest {

    private val notificationManager = FakeAppNotificationManager()

    private fun useCase(vehicle: Vehicle? = null) = NotifyParkingConfirmationUseCase(
        notificationPort = notificationManager,
        vehicleRepository = FakeVehicleRepository(defaultVehicle = vehicle),
    )

    private fun vehicle(brand: String? = null, model: String? = null) = Vehicle(
        id = "v1",
        userId = "user-1",
        brand = brand,
        model = model,
        sizeCategory = VehicleSize.MEDIUM,
        bluetoothDeviceId = null,
        isDefault = true,
    )

    // ── Confidence routing ────────────────────────────────────────────────────

    @Test
    fun `should call showParkingConfirmation with score 0 for Low confidence`() = runTest {
        useCase()(ParkingConfidence.Low)
        assertEquals(1, notificationManager.parkingConfirmationCallCount)
    }

    @Test
    fun `should call showParkingConfirmation with actual score for Medium confidence`() = runTest {
        useCase()(ParkingConfidence.Medium(score = 0.60f))
        assertEquals(1, notificationManager.parkingConfirmationCallCount)
    }

    @Test
    fun `should call showParkingConfirmation with actual score for High confidence`() = runTest {
        useCase()(ParkingConfidence.High(score = 0.85f))
        assertEquals(1, notificationManager.parkingConfirmationCallCount)
    }

    @Test
    fun `should not call showParkingConfirmation for NotYet confidence`() = runTest {
        useCase()(ParkingConfidence.NotYet)
        assertEquals(0, notificationManager.parkingConfirmationCallCount)
    }

    // ── Vehicle display name ──────────────────────────────────────────────────

    @Test
    fun `should pass brand and model as display name when both present`() = runTest {
        var capturedName: String? = "sentinel"
        val mgr = object : FakeAppNotificationManager() {
            override fun showParkingConfirmation(score: Float, vehicleName: String?) {
                super.showParkingConfirmation(score, vehicleName)
                capturedName = vehicleName
            }
        }
        val uc = NotifyParkingConfirmationUseCase(
            notificationPort = mgr,
            vehicleRepository = FakeVehicleRepository(vehicle(brand = "Toyota", model = "Corolla")),
        )
        uc(ParkingConfidence.Low)
        assertEquals("Toyota Corolla", capturedName)
    }

    @Test
    fun `should pass brand only when model is null`() = runTest {
        var capturedName: String? = "sentinel"
        val mgr = object : FakeAppNotificationManager() {
            override fun showParkingConfirmation(score: Float, vehicleName: String?) {
                capturedName = vehicleName
            }
        }
        val uc = NotifyParkingConfirmationUseCase(
            notificationPort = mgr,
            vehicleRepository = FakeVehicleRepository(vehicle(brand = "Ford", model = null)),
        )
        uc(ParkingConfidence.Low)
        assertEquals("Ford", capturedName)
    }

    @Test
    fun `should pass null when both brand and model are null`() = runTest {
        var capturedName: String? = "sentinel"
        val mgr = object : FakeAppNotificationManager() {
            override fun showParkingConfirmation(score: Float, vehicleName: String?) {
                capturedName = vehicleName
            }
        }
        val uc = NotifyParkingConfirmationUseCase(
            notificationPort = mgr,
            vehicleRepository = FakeVehicleRepository(vehicle(brand = null, model = null)),
        )
        uc(ParkingConfidence.Low)
        assertEquals(null, capturedName)
    }

    @Test
    fun `should pass null when no default vehicle exists`() = runTest {
        var capturedName: String? = "sentinel"
        val mgr = object : FakeAppNotificationManager() {
            override fun showParkingConfirmation(score: Float, vehicleName: String?) {
                capturedName = vehicleName
            }
        }
        val uc = NotifyParkingConfirmationUseCase(
            notificationPort = mgr,
            vehicleRepository = FakeVehicleRepository(defaultVehicle = null),
        )
        uc(ParkingConfidence.Low)
        assertEquals(null, capturedName)
    }
}
