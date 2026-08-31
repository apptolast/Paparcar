package com.rndeveloper.paparcar.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.apptolast.customlogin.domain.AuthRepository
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.UserProfileDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.VehicleDto
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.detection.worker.ClearActiveParkingSessionWorker
import com.rndeveloper.paparcar.detection.worker.SaveNewParkingSessionWorker
import com.rndeveloper.paparcar.detection.worker.UpdateParkingSessionAddressAndPlaceWorker
import com.rndeveloper.paparcar.data.mapper.toParkingHistoryDto
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.RouteInferenceResolution
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveNewParkingSessionWorkerTest {

    private val fakeDataSource = FakeUserProfileDataSource()
    private val fakeAuth = FakeAuthRepository(
        initialSession = FakeAuthRepository.authenticatedSession(userId = "user-1"),
    )

    @Before
    fun setUp() {
        startKoin {
            modules(module {
                single<RemoteUserProfileDataSource> { fakeDataSource }
                single<AuthRepository> { fakeAuth }
            })
        }
    }

    @After
    fun tearDown() = stopKoin()

    // ── SaveNewParkingSessionWorker ─────────────────────────────────────────────────────

    @Test
    fun `SaveNewParkingSessionWorker success — saves new session and marks previous as inactive`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val session = userParking("new-session")
        val request = SaveNewParkingSessionWorker.buildRequest(session,previousSessionId = "old-session")
        val worker = TestListenableWorkerBuilder<SaveNewParkingSessionWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeDataSource.saveParkingSessionCallCount)
        assertEquals("new-session", fakeDataSource.lastSavedSession?.id)
        assertEquals(1, fakeDataSource.updateActiveFlagCallCount)
        assertEquals("old-session" to false, fakeDataSource.lastActiveFlagUpdate)
    }

    /**
     * [SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001] **The witness the field list never had.**
     *
     * The payload used to be a hand-written list of 15 keys re-assembled field by field, documented
     * as "every field of the new session" and asked to be kept "in lockstep with ParkingHistoryDto".
     * It fell out of lockstep three times — MAPPER-001, MAPPER-002 and `zoneRadiusMeters` — and each
     * time the fix added the one missing field and a test for that one field. The per-field tests
     * below are those fixes. None of them could fail for a field nobody had thought of, which is
     * exactly the population that keeps getting dropped.
     *
     * This one compares the WHOLE dto that reached the data source against the whole dto the mapper
     * produces. A field added to [ParkingHistoryDto] tomorrow is covered on the day it is added,
     * without anyone remembering this file exists.
     *
     * ⚠️ Falsification: drop any single field from the payload (e.g. re-introduce a per-field
     * `workDataOf` that omits `zoneRadiusMeters`) and this must go red. If it stays green, it is
     * asserting less than it claims.
     */
    @Test
    fun `SaveNewParkingSessionWorker carries EVERY dto field to the data source`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Every field set to a value distinguishable from its default, so a dropped field shows up
        // as a mismatch rather than coinciding with the default it would have been written as.
        val session = userParking("whole-session").copy(
            vehicleId = "vehicle-99",
            spotId = "spot-7",
            geofenceId = "geof-7",
            isActive = false,
            detectionReliability = 0.90f,
            spotType = SpotType.MANUAL_REPORT,
            sizeCategory = VehicleSize.VAN_HIGH,
            carbodyType = CarbodyType.PICKUP,
            armEvidence = "verified_enter",
            detectionPath = "steps+egress",
            zoneRadiusMeters = 250f,
            routePolyline = "ghk~Ex}ce@Ov@Bb@",
            routeSnapped = true,
            routeInferredSpans = "3:9",
            routeInferredResolution = RouteInferenceResolution.CONFIRMED,
            routeDistanceMeters = 3542.79f,
            endedAtMs = 1788111681770L,
            publishedSpot = true,
        )
        val request = SaveNewParkingSessionWorker.buildRequest(session, previousSessionId = null)
        val worker = TestListenableWorkerBuilder<SaveNewParkingSessionWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        worker.doWork()

        val saved = fakeDataSource.lastSavedSession
        // `updatedAt` is stamped at enqueue time (wall clock) and `userId` at execution time from
        // the auth session, so neither can come from the mapper — every OTHER field must.
        val expected = session.toParkingHistoryDto(updatedAt = saved?.updatedAt ?: 0L)
        assertEquals(expected, saved)
    }

    /**
     * The `updatedAt` the old payload had no key for: it was written as 0, and 0 loses every
     * Last-Write-Wins comparison in `UserParkingReconcile`, so a remote doc could never win over a
     * stale local row. [SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001][SYNC-RECONCILE-USERPARKING-001]
     */
    @Test
    fun `SaveNewParkingSessionWorker stamps a real updatedAt so the remote can win the LWW`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val request = SaveNewParkingSessionWorker.buildRequest(userParking("stamp"), previousSessionId = null)
        val worker = TestListenableWorkerBuilder<SaveNewParkingSessionWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        worker.doWork()

        assertTrue(
            (fakeDataSource.lastSavedSession?.updatedAt ?: 0L) > 0L,
            "updatedAt must be a real timestamp, not the 0 that always loses the LWW",
        )
    }

    @Test
    fun `SaveNewParkingSessionWorker propagates vehicleId through the Data payload`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val session = userParking("new-session").copy(vehicleId = "vehicle-99")
        val request = SaveNewParkingSessionWorker.buildRequest(session,previousSessionId = null)
        val worker = TestListenableWorkerBuilder<SaveNewParkingSessionWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        worker.doWork()

        assertEquals("vehicle-99", fakeDataSource.lastSavedSession?.vehicleId)
    }

    @Test
    fun `SaveNewParkingSessionWorker propagates detectionReliability through the Data payload`() = runTest {
        // Regression: previously the DTO reconstructed inside doWork() dropped this field, so every
        // Firestore doc ended up with detectionReliability=null even when the coordinator passed 0.90. [MAPPER-003]
        val context: Context = ApplicationProvider.getApplicationContext()
        val session = userParking("rel-session").copy(detectionReliability = 0.90f)
        val request = SaveNewParkingSessionWorker.buildRequest(session,previousSessionId = null)
        val worker = TestListenableWorkerBuilder<SaveNewParkingSessionWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        worker.doWork()

        assertEquals(0.90f, fakeDataSource.lastSavedSession?.detectionReliability)
    }

    @Test
    fun `SaveNewParkingSessionWorker preserves null detectionReliability for manual reports`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val session = userParking("manual-session").copy(detectionReliability = null)
        val request = SaveNewParkingSessionWorker.buildRequest(session,previousSessionId = null)
        val worker = TestListenableWorkerBuilder<SaveNewParkingSessionWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        worker.doWork()

        assertEquals(null, fakeDataSource.lastSavedSession?.detectionReliability)
    }

    @Test
    fun `SaveNewParkingSessionWorker success — no previous session id skips active-flag update`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val session = userParking("solo-session")
        val request = SaveNewParkingSessionWorker.buildRequest(session,previousSessionId = null)
        val worker = TestListenableWorkerBuilder<SaveNewParkingSessionWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeDataSource.saveParkingSessionCallCount)
        assertEquals(0, fakeDataSource.updateActiveFlagCallCount)
    }

    @Test
    fun `SaveNewParkingSessionWorker retries on Firestore failure`() = runTest {
        fakeDataSource.saveParkingSessionThrows = RuntimeException("network error")
        val context: Context = ApplicationProvider.getApplicationContext()
        val session = userParking("retry-session")
        val request = SaveNewParkingSessionWorker.buildRequest(session,null)
        val worker = TestListenableWorkerBuilder<SaveNewParkingSessionWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `SaveNewParkingSessionWorker fails permanently after max attempts`() = runTest {
        fakeDataSource.saveParkingSessionThrows = RuntimeException("persistent failure")
        val context: Context = ApplicationProvider.getApplicationContext()
        val session = userParking("fail-session")
        val request = SaveNewParkingSessionWorker.buildRequest(session,null)
        val worker = TestListenableWorkerBuilder<SaveNewParkingSessionWorker>(context)
            .setInputData(request.workSpec.input)
            .setRunAttemptCount(5)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `SaveNewParkingSessionWorker fails immediately when userId missing`() = runTest {
        fakeAuth.emitState(com.apptolast.customlogin.domain.model.AuthState.Unauthenticated)
        val context: Context = ApplicationProvider.getApplicationContext()
        val worker = TestListenableWorkerBuilder<SaveNewParkingSessionWorker>(context).build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    // ── ClearActiveParkingSessionWorker ─────────────────────────────────────────────────

    @Test
    fun `ClearActiveParkingSessionWorker success — marks session inactive via update()`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val request = ClearActiveParkingSessionWorker.buildRequest("session-abc")
        val worker = TestListenableWorkerBuilder<ClearActiveParkingSessionWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeDataSource.updateActiveFlagCallCount)
        assertEquals("session-abc" to false, fakeDataSource.lastActiveFlagUpdate)
    }

    @Test
    fun `ClearActiveParkingSessionWorker retries on failure`() = runTest {
        fakeDataSource.updateActiveFlagThrows = RuntimeException("Firestore timeout")
        val context: Context = ApplicationProvider.getApplicationContext()
        val request = ClearActiveParkingSessionWorker.buildRequest("session-abc")
        val worker = TestListenableWorkerBuilder<ClearActiveParkingSessionWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    // ── UpdateParkingSessionAddressAndPlaceWorker ──────────────────────────────────────────────

    @Test
    fun `UpdateParkingSessionAddressAndPlaceWorker success — calls updateParkingSessionAddressAndPlace`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val request = UpdateParkingSessionAddressAndPlaceWorker.buildRequest(
            sessionId = "session-xyz",
            address = com.rndeveloper.paparcar.domain.model.AddressInfo("Calle Mayor", "Madrid", "Madrid", "Spain"),
            placeInfo = null,
        )
        val worker = TestListenableWorkerBuilder<UpdateParkingSessionAddressAndPlaceWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeDataSource.updateLocationCallCount)
        assertEquals("session-xyz", fakeDataSource.lastLocationUpdate?.sessionId)
        assertEquals("Calle Mayor", fakeDataSource.lastLocationUpdate?.address?.street)
    }

    @Test
    fun `UpdateParkingSessionAddressAndPlaceWorker retries on failure`() = runTest {
        fakeDataSource.updateLocationThrows = RuntimeException("offline")
        val context: Context = ApplicationProvider.getApplicationContext()
        val request = UpdateParkingSessionAddressAndPlaceWorker.buildRequest("session-xyz", null, null)
        val worker = TestListenableWorkerBuilder<UpdateParkingSessionAddressAndPlaceWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun userParking(id: String) = UserParking(
        id = id,
        userId = "user-1",
        location = GpsPoint(40.416775, -3.703790, 8f, 1000L, 0f),
        isActive = true,
    )
}

// ─── In-package fake (test-only) ──────────────────────────────────────────────

private class FakeUserProfileDataSource : RemoteUserProfileDataSource {

    data class LocationUpdate(val sessionId: String, val address: AddressDto?, val placeInfo: PlaceInfoDto?)

    var saveParkingSessionCallCount = 0
    var lastSavedSession: ParkingHistoryDto? = null
    var saveParkingSessionThrows: Throwable? = null

    var updateActiveFlagCallCount = 0
    var lastActiveFlagUpdate: Pair<String, Boolean>? = null
    var updateActiveFlagThrows: Throwable? = null

    var updateLocationCallCount = 0
    var lastLocationUpdate: LocationUpdate? = null
    var updateLocationThrows: Throwable? = null

    override suspend fun getProfile(userId: String): UserProfileDto? = null
    override suspend fun createOrUpdateProfile(profile: UserProfileDto) = Unit
    override suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?) = Unit
    override suspend fun getParkingHistory(userId: String): List<ParkingHistoryDto> = emptyList()
    override suspend fun deleteUserData(userId: String) = Unit
    override suspend fun getVehicles(userId: String): List<VehicleDto> = emptyList()
    override suspend fun saveVehicle(userId: String, vehicle: VehicleDto) = Unit
    override suspend fun deleteVehicle(userId: String, vehicleId: String) = Unit
    override suspend fun updateVehicleActiveFlag(userId: String, vehicleId: String, isActive: Boolean) = Unit
    override suspend fun updateVehicleBluetoothDevice(userId: String, vehicleId: String, deviceAddress: String?) = Unit

    override suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto) {
        saveParkingSessionThrows?.let { throw it }
        saveParkingSessionCallCount++
        lastSavedSession = session
    }

    override suspend fun clearParkingSessionActiveFlag(userId: String, sessionId: String) {
        updateActiveFlagThrows?.let { throw it }
        updateActiveFlagCallCount++
        lastActiveFlagUpdate = sessionId to false
    }

    override suspend fun updateParkingSessionAddressAndPlace(
        userId: String,
        sessionId: String,
        address: AddressDto?,
        placeInfo: PlaceInfoDto?,
    ) {
        updateLocationThrows?.let { throw it }
        updateLocationCallCount++
        lastLocationUpdate = LocationUpdate(sessionId, address, placeInfo)
    }
}
