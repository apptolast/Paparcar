package io.apptolast.paparcar.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.data.datasource.remote.dto.UserProfileDto
import io.apptolast.paparcar.detection.worker.ClearActiveSyncWorker
import io.apptolast.paparcar.detection.worker.LocationUpdateSyncWorker
import io.apptolast.paparcar.detection.worker.ParkingSyncWorker
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParkingSyncWorkerTest {

    private val fakeDataSource = FakeUserProfileDataSource()

    @Before
    fun setUp() {
        startKoin {
            modules(module {
                single<RemoteUserProfileDataSource> { fakeDataSource }
            })
        }
    }

    @After
    fun tearDown() = stopKoin()

    // ── ParkingSyncWorker ─────────────────────────────────────────────────────

    @Test
    fun `ParkingSyncWorker success — saves new session and marks previous as inactive`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val session = userParking("new-session")
        val request = ParkingSyncWorker.buildRequest("user-1", session, previousSessionId = "old-session")
        val worker = TestListenableWorkerBuilder<ParkingSyncWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeDataSource.saveParkingSessionCallCount)
        assertEquals("new-session", fakeDataSource.lastSavedSession?.id)
        assertEquals(1, fakeDataSource.updateActiveFlagCallCount)
        assertEquals("old-session" to false, fakeDataSource.lastActiveFlagUpdate)
    }

    @Test
    fun `ParkingSyncWorker success — no previous session id skips active-flag update`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val session = userParking("solo-session")
        val request = ParkingSyncWorker.buildRequest("user-1", session, previousSessionId = null)
        val worker = TestListenableWorkerBuilder<ParkingSyncWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeDataSource.saveParkingSessionCallCount)
        assertEquals(0, fakeDataSource.updateActiveFlagCallCount)
    }

    @Test
    fun `ParkingSyncWorker retries on Firestore failure`() = runTest {
        fakeDataSource.saveParkingSessionThrows = RuntimeException("network error")
        val context: Context = ApplicationProvider.getApplicationContext()
        val session = userParking("retry-session")
        val request = ParkingSyncWorker.buildRequest("user-1", session, null)
        val worker = TestListenableWorkerBuilder<ParkingSyncWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `ParkingSyncWorker fails permanently after max attempts`() = runTest {
        fakeDataSource.saveParkingSessionThrows = RuntimeException("persistent failure")
        val context: Context = ApplicationProvider.getApplicationContext()
        val session = userParking("fail-session")
        val request = ParkingSyncWorker.buildRequest("user-1", session, null)
        val worker = TestListenableWorkerBuilder<ParkingSyncWorker>(context)
            .setInputData(request.workSpec.input)
            .setRunAttemptCount(5)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `ParkingSyncWorker fails immediately when userId missing`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val worker = TestListenableWorkerBuilder<ParkingSyncWorker>(context).build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    // ── ClearActiveSyncWorker ─────────────────────────────────────────────────

    @Test
    fun `ClearActiveSyncWorker success — marks session inactive via update()`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val request = ClearActiveSyncWorker.buildRequest("user-1", "session-abc")
        val worker = TestListenableWorkerBuilder<ClearActiveSyncWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeDataSource.updateActiveFlagCallCount)
        assertEquals("session-abc" to false, fakeDataSource.lastActiveFlagUpdate)
    }

    @Test
    fun `ClearActiveSyncWorker retries on failure`() = runTest {
        fakeDataSource.updateActiveFlagThrows = RuntimeException("Firestore timeout")
        val context: Context = ApplicationProvider.getApplicationContext()
        val request = ClearActiveSyncWorker.buildRequest("user-1", "session-abc")
        val worker = TestListenableWorkerBuilder<ClearActiveSyncWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    // ── LocationUpdateSyncWorker ──────────────────────────────────────────────

    @Test
    fun `LocationUpdateSyncWorker success — calls updateParkingSessionLocation`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val request = LocationUpdateSyncWorker.buildRequest(
            userId = "user-1",
            sessionId = "session-xyz",
            address = io.apptolast.paparcar.domain.model.AddressInfo("Calle Mayor", "Madrid", "Madrid", "Spain"),
            placeInfo = null,
        )
        val worker = TestListenableWorkerBuilder<LocationUpdateSyncWorker>(context)
            .setInputData(request.workSpec.input)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, fakeDataSource.updateLocationCallCount)
        assertEquals("session-xyz", fakeDataSource.lastLocationUpdate?.sessionId)
        assertEquals("Calle Mayor", fakeDataSource.lastLocationUpdate?.address?.street)
    }

    @Test
    fun `LocationUpdateSyncWorker retries on failure`() = runTest {
        fakeDataSource.updateLocationThrows = RuntimeException("offline")
        val context: Context = ApplicationProvider.getApplicationContext()
        val request = LocationUpdateSyncWorker.buildRequest("user-1", "session-xyz", null, null)
        val worker = TestListenableWorkerBuilder<LocationUpdateSyncWorker>(context)
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

    override suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto) {
        saveParkingSessionThrows?.let { throw it }
        saveParkingSessionCallCount++
        lastSavedSession = session
    }

    override suspend fun updateParkingSessionActiveFlag(userId: String, sessionId: String, isActive: Boolean) {
        updateActiveFlagThrows?.let { throw it }
        updateActiveFlagCallCount++
        lastActiveFlagUpdate = sessionId to isActive
    }

    override suspend fun updateParkingSessionLocation(
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
