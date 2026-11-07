@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.rndeveloper.paparcar.bluetooth

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateBtParkUseCase
import com.rndeveloper.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import com.rndeveloper.paparcar.fakes.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BluetoothParkingDetectorTest {
    private class Fixture(val scope: TestScope) {
        val config = ParkingDetectionConfig()
        val source = FakeLocationDataSource()
        val repository = FakeUserParkingRepository()
        var bluetoothSaved = 0
        val notifications = object : FakeAppNotificationManager() {
            override fun showBluetoothParkingSaved(
                parkingId: String, vehicleName: String?, latitude: Double, longitude: Double,
            ) { bluetoothSaved++ }
        }
        private val vehicle = Vehicle(
            id = "bt-car", userId = "user-123", brand = "Seat", model = "Leon",
            sizeCategory = VehicleSize.MEDIUM_SUV,
        )
        val detector = BluetoothParkingDetector(
            observeLocation = ObserveAdaptiveLocationUseCase(source),
            confirmParking = ConfirmParkingUseCase(
                userParkingRepository = repository,
                vehicleRepository = FakeVehicleRepository(defaultVehicle = vehicle),
                zoneRepository = FakeZoneRepository(),
                geofenceService = FakeGeofenceManager(),
                enrichmentScheduler = FakeParkingEnrichmentScheduler(),
                authRepository = FakeAuthRepository(FakeAuthRepository.authenticatedSession()),
                config = config,
                departureEventBus = FakeDepartureEventBus(),
            ),
            notificationPort = notifications,
            config = config,
            evaluateBtPark = EvaluateBtParkUseCase(config),
            nowMs = { BASE + scope.testScheduler.currentTime },
        )
        fun point(meters: Double, speed: Float = 0f) = GpsPoint(
            40.4 + meters / 111_320.0, -3.7, 8f,
            BASE + scope.testScheduler.currentTime, speed,
        )
        fun start(short: Boolean = false) = scope.async {
            detector.detectParking(
                "car-mac", vehicle.id, BASE - if (short) 12_000L else 600_000L,
                point(0.0).copy(timestamp = BASE - if (short) 12_000L else 600_000L),
            )
        }
        suspend fun settle() {
            scope.runCurrent()
            source.emitHighAccuracy(point(400.0))
            scope.runCurrent()
            scope.advanceTimeBy(config.btDisconnectDebounceMs)
            source.emitHighAccuracy(point(400.0))
            scope.runCurrent()
        }
    }

    @Test
    fun drivingDisconnectReturnsTheFixForFollowingWithoutPlantingAPin() = runTest {
        val f = Fixture(this)
        val work = f.start()
        runCurrent()
        val driving = f.point(400.0, 10f)
        f.source.emitHighAccuracy(driving)
        runCurrent()

        assertEquals(driving, assertIs<BtParkingOutcome.ContinueDriving>(work.await()).fix)
        assertEquals(0, f.repository.saveNewParkingSessionCallCount)
        assertEquals(0, f.notifications.markParkingNudgeCallCount)
    }

    @Test
    fun normalArrivalSavesAutomaticallyAtTheOriginalStop() = runTest {
        val f = Fixture(this)
        val work = f.start()
        f.settle()
        advanceTimeBy(20_000L)
        f.source.emitHighAccuracy(f.point(440.0, 1.3f))
        runCurrent()

        assertEquals(BtParkingOutcome.Finished, work.await())
        assertEquals(1, f.bluetoothSaved)
        assertEquals(0, f.notifications.parkingConfirmationCallCount)
        assertEquals(0, f.notifications.markParkingNudgeCallCount)
        val saved = f.repository.observeActiveSessions().first().single()
        assertEquals("bt-car", saved.vehicleId)
        assertEquals(40.4 + 400.0 / 111_320.0, saved.location.latitude, 0.000001)
    }

    @Test
    fun homeArrivalWithoutWalkingIsSavedWithinTheShortWatch() = runTest {
        val f = Fixture(this)
        val work = f.start()
        f.settle()
        advanceTimeBy(f.config.btWalkAwayTimeoutMs)
        runCurrent()

        assertTrue(work.isCompleted)
        assertEquals(BtParkingOutcome.Finished, work.await())
        assertEquals(1, f.bluetoothSaved)
        assertEquals(0, f.notifications.markParkingNudgeCallCount)
        assertTrue(testScheduler.currentTime <= 150_000L)
    }

    @Test
    fun resumedDrivingAfterAStopAlsoHandsOffTheTrip() = runTest {
        val f = Fixture(this)
        val work = f.start()
        f.settle()
        advanceTimeBy(10_000L)
        val driving = f.point(500.0, 10f)
        f.source.emitHighAccuracy(driving)
        runCurrent()

        assertEquals(driving, assertIs<BtParkingOutcome.ContinueDriving>(work.await()).fix)
        assertEquals(0, f.repository.saveNewParkingSessionCallCount)
    }

    @Test
    fun briefUnmeasuredConnectionProducesNoQuestion() = runTest {
        val f = Fixture(this)
        val work = f.start(short = true)
        runCurrent()
        assertEquals(0, f.notifications.markParkingNudgeCallCount)
        advanceUntilIdle()

        assertEquals(BtParkingOutcome.Finished, work.await())
        assertEquals(0, f.notifications.markParkingNudgeCallCount)
        assertEquals(0, f.repository.saveNewParkingSessionCallCount)
    }

    @Test
    fun aRealRideWithoutAnArrivalFixAsksOnceAfterSampling() = runTest {
        val f = Fixture(this)
        val work = f.start()
        runCurrent()
        assertEquals(0, f.notifications.markParkingNudgeCallCount)
        advanceUntilIdle()

        assertEquals(BtParkingOutcome.Finished, work.await())
        assertEquals(1, f.notifications.markParkingNudgeCallCount)
        assertEquals("bt-car", f.notifications.lastMarkParkingNudgeVehicleId)
        assertEquals(0, f.repository.saveNewParkingSessionCallCount)
    }

    @Test
    fun aBriefLinkCanHandOffIfThePhoneIsStillDriving() = runTest {
        val f = Fixture(this)
        val work = f.start(short = true)
        runCurrent()
        f.source.emitHighAccuracy(f.point(100.0, 10f))
        runCurrent()

        assertIs<BtParkingOutcome.ContinueDriving>(work.await())
        assertEquals(0, f.notifications.markParkingNudgeCallCount)
        assertEquals(0, f.repository.saveNewParkingSessionCallCount)
    }

    @Test
    fun reconnectCancellationBeforeTheGraceEndsLeavesNoPinOrQuestion() = runTest {
        val f = Fixture(this)
        val work = f.start()
        runCurrent()
        f.source.emitHighAccuracy(f.point(400.0))
        runCurrent()
        advanceTimeBy(5_000L)
        // The service cancels this exact job when the car reconnects.
        work.cancel()
        advanceUntilIdle()

        assertTrue(work.isCancelled)
        assertEquals(0, f.bluetoothSaved)
        assertEquals(0, f.notifications.markParkingNudgeCallCount)
        assertNull(f.repository.observeActiveSessions().first().firstOrNull())
    }

    private companion object { const val BASE = 1_000_000L }
}
