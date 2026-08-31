package com.rndeveloper.paparcar.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rndeveloper.paparcar.data.datasource.local.room.AppDatabase
import com.rndeveloper.paparcar.data.datasource.local.room.UserParkingEntity
import com.rndeveloper.paparcar.data.datasource.local.room.VehicleEntity
import com.rndeveloper.paparcar.data.datasource.local.room.buildAppDatabase
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.UserProfileDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.VehicleDto
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Witness for [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001].
 *
 * Deleting a vehicle used to remove one row and leave every parking of that car behind. They were
 * not visible anywhere — the history pages read `WHERE vehicleId = :vehicleId`, and no page asks for
 * a car that no longer exists — but they were still there, still syncing.
 *
 * That failure is invisible to any test that reads through the UI or the repository's own reads:
 * both would agree the history is gone. The only way to see it is to ask the TABLE, so this test
 * drives the real Room database rather than a fake, and asserts on rows.
 *
 * The block is measured the same way: a car holding an active parking must survive the delete
 * intact, because closing a parking can publish a spot to the community and that is never a side
 * effect of deleting a car.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VehicleDeleteCascadeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: AppDatabase
    private lateinit var remote: RecordingRemote
    private lateinit var repository: VehicleRepositoryImpl

    @Before
    fun setUp() {
        db = buildAppDatabase(context, "veh-delete-cascade.db")
        remote = RecordingRemote()
        repository = VehicleRepositoryImpl(
            dao = db.vehicleDao(),
            profileDao = db.userProfileDao(),
            userParkingDao = db.parkingSessionDao(),
            userProfileDataSource = remote,
            authRepository = FakeAuthRepository(
                initialSession = FakeAuthRepository.authenticatedSession(USER),
            ),
            syncScope = CoroutineScope(StandardTestDispatcher()),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun should_deleteEveryParkingOfTheVehicle_when_theVehicleIsDeleted() = runTest {
        seedVehicle(KEPT)
        seedVehicle(DOOMED)
        db.parkingSessionDao().insert(parking("p1", DOOMED, isActive = false))
        db.parkingSessionDao().insert(parking("p2", DOOMED, isActive = false))
        // A withdrawn parking never appears in the history, which is exactly why it would be left
        // behind by a fix that only swept what the user can see.
        db.parkingSessionDao().insert(parking("p3", DOOMED, isActive = false, retractedAtMs = 1L))
        db.parkingSessionDao().insert(parking("p4", KEPT, isActive = false))

        assertTrue(repository.deleteVehicle(DOOMED).isSuccess)

        assertEquals(
            emptyList(),
            rowsOf(DOOMED),
            "a deleted car cannot leave parkings behind — invisible is not gone",
        )
        assertEquals(
            listOf("p4"),
            rowsOf(KEPT),
            "the other car's history is not collateral damage",
        )
    }

    @Test
    fun should_refuseAndChangeNothing_when_theVehicleIsParkedRightNow() = runTest {
        seedVehicle(DOOMED)
        db.parkingSessionDao().insert(parking("past", DOOMED, isActive = false))
        db.parkingSessionDao().insert(parking("live", DOOMED, isActive = true))

        val result = repository.deleteVehicle(DOOMED)

        assertIs<PaparcarError.Vehicle.DeleteBlockedByActiveParking>(
            result.exceptionOrNull(),
            "a parked car is refused on purpose, not reported as a failure",
        )
        assertEquals(
            listOf("live", "past"),
            rowsOf(DOOMED).sorted(),
            "a refused delete leaves the history untouched",
        )
        assertTrue(
            db.vehicleDao().getById(DOOMED, USER) != null,
            "…and leaves the vehicle itself in place",
        )
    }

    @Test
    fun should_countOnlyWhatTheHistoryShows_when_reportingTheFootprint() = runTest {
        seedVehicle(DOOMED)
        db.parkingSessionDao().insert(parking("p1", DOOMED, isActive = false))
        db.parkingSessionDao().insert(parking("p2", DOOMED, isActive = false, retractedAtMs = 1L))
        db.parkingSessionDao().insert(parking("p3", DOOMED, isActive = true))

        val footprint = repository.getParkingFootprint(DOOMED)

        // The warning quotes what the user would see disappear: the withdrawn one was never shown
        // and the active one is what blocks the delete in the first place.
        assertEquals(1, footprint.endedParkings)
        assertTrue(footprint.hasActiveParking)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private suspend fun rowsOf(vehicleId: String): List<String> =
        db.parkingSessionDao().getByUser(USER).filter { it.vehicleId == vehicleId }.map { it.id }

    private suspend fun seedVehicle(id: String) {
        db.vehicleDao().insert(
            VehicleEntity(id = id, userId = USER, sizeCategory = "MEDIUM_SUV"),
        )
    }

    private fun parking(
        id: String,
        vehicleId: String,
        isActive: Boolean,
        retractedAtMs: Long? = null,
    ) = UserParkingEntity(
        id = id,
        userId = USER,
        vehicleId = vehicleId,
        latitude = 40.0,
        longitude = -3.0,
        accuracy = 5f,
        timestamp = 1_000L,
        isActive = isActive,
        retractedAtMs = retractedAtMs,
    )

    private companion object {
        const val USER = "user-1"
        const val KEPT = "vehicle-kept"
        const val DOOMED = "vehicle-doomed"
    }
}

/** Records the remote calls so the test can tell "we asked Firestore to forget it" from silence. */
private class RecordingRemote : RemoteUserProfileDataSource {
    val deletedHistoryFor = mutableListOf<String>()

    override suspend fun deleteParkingSessionsForVehicle(userId: String, vehicleId: String) {
        deletedHistoryFor += vehicleId
    }

    override suspend fun getProfile(userId: String): UserProfileDto? = null
    override suspend fun createOrUpdateProfile(profile: UserProfileDto) = Unit
    override suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?) = Unit
    override suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto) = Unit
    override suspend fun clearParkingSessionActiveFlag(userId: String, sessionId: String) = Unit
    override suspend fun updateParkingSessionAddressAndPlace(
        userId: String,
        sessionId: String,
        address: AddressDto?,
        placeInfo: PlaceInfoDto?,
    ) = Unit
    override suspend fun getParkingHistory(userId: String): List<ParkingHistoryDto> = emptyList()
    override suspend fun getVehicles(userId: String): List<VehicleDto> = emptyList()
    override suspend fun saveVehicle(userId: String, vehicle: VehicleDto) = Unit
    override suspend fun deleteVehicle(userId: String, vehicleId: String) = Unit
    override suspend fun updateVehicleActiveFlag(userId: String, vehicleId: String, isActive: Boolean) = Unit
    override suspend fun updateVehicleBluetoothDevice(userId: String, vehicleId: String, deviceAddress: String?) = Unit
    override suspend fun deleteUserData(userId: String) = Unit
}
