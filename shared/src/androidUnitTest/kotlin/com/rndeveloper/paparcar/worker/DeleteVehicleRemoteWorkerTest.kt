package com.rndeveloper.paparcar.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.UserProfileDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.VehicleDto
import com.rndeveloper.paparcar.detection.worker.DeleteVehicleRemoteWorker
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
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

/**
 * [SYNC-A-REMOTE-DELETE-HAS-NO-OUTBOX-BEHIND-IT-001] The worker behind the delete's outbox: both
 * remote deletes, in order, with retry — the guarantees the `syncScope` never gave.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeleteVehicleRemoteWorkerTest {

    private val fakeDataSource = DeleteRecordingDataSource()
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

    private fun buildWorker(vehicleId: String = "vehicle-doomed"): DeleteVehicleRemoteWorker {
        val context: Context = ApplicationProvider.getApplicationContext()
        val request = DeleteVehicleRemoteWorker.buildRequest(vehicleId)
        return TestListenableWorkerBuilder<DeleteVehicleRemoteWorker>(context)
            .setInputData(request.workSpec.input)
            .build()
    }

    @Test
    fun `deletes the sessions FIRST and the vehicle second`() = runTest {
        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // The order is load-bearing: surviving session docs are what the inbound reconcile
        // resurrects as history of a car that no longer exists.
        assertEquals(
            listOf("sessions:vehicle-doomed", "vehicle:vehicle-doomed"),
            fakeDataSource.calls,
        )
    }

    @Test
    fun `retries when the sessions delete fails — and does not delete the vehicle out of order`() = runTest {
        fakeDataSource.sessionsDeleteThrows = IllegalStateException("offline")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(
            emptyList(),
            fakeDataSource.calls.filter { it.startsWith("vehicle:") },
            "a failed sessions delete must not let the vehicle delete jump the order — the retry " +
                "reruns the whole job from the top",
        )
    }

    @Test
    fun `retries when the vehicle delete fails after the sessions landed`() = runTest {
        fakeDataSource.vehicleDeleteThrows = IllegalStateException("offline")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        // The rerun re-deletes the sessions too: both remote deletes are idempotent, so paying
        // that write again is cheaper than tracking partial progress.
        assertEquals(listOf("sessions:vehicle-doomed"), fakeDataSource.calls)
    }

    @Test
    fun `fails without touching the remote when the user is no longer signed in`() = runTest {
        fakeAuth.signOut()

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(emptyList(), fakeDataSource.calls, "a signed-out user's remote data is not ours to touch")
    }
}

/** Records the two delete calls in arrival order; everything else is inert. */
private class DeleteRecordingDataSource : RemoteUserProfileDataSource {
    val calls = mutableListOf<String>()
    var sessionsDeleteThrows: Throwable? = null
    var vehicleDeleteThrows: Throwable? = null

    override suspend fun deleteParkingSessionsForVehicle(userId: String, vehicleId: String) {
        sessionsDeleteThrows?.let { throw it }
        calls += "sessions:$vehicleId"
    }

    override suspend fun deleteVehicle(userId: String, vehicleId: String) {
        vehicleDeleteThrows?.let { throw it }
        calls += "vehicle:$vehicleId"
    }

    override suspend fun getProfile(userId: String): UserProfileDto? = null
    override suspend fun createOrUpdateProfile(profile: UserProfileDto) = Unit
    override suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?) = Unit
    override suspend fun getParkingHistory(userId: String): List<ParkingHistoryDto> = emptyList()
    override suspend fun deleteUserData(userId: String) = Unit
    override suspend fun getVehicles(userId: String): List<VehicleDto> = emptyList()
    override suspend fun saveVehicle(userId: String, vehicle: VehicleDto) = Unit
    override suspend fun updateVehicleActiveFlag(userId: String, vehicleId: String, isActive: Boolean) = Unit
    override suspend fun updateVehicleBluetoothDevice(userId: String, vehicleId: String, deviceAddress: String?) = Unit
    override suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto) = Unit
    override suspend fun clearParkingSessionActiveFlag(userId: String, sessionId: String) = Unit
    override suspend fun updateParkingSessionAddressAndPlace(
        userId: String,
        sessionId: String,
        address: AddressDto?,
        placeInfo: PlaceInfoDto?,
    ) = Unit
}
