package com.rndeveloper.paparcar.data.repository

import com.apptolast.baselogin.domain.model.UserSession
import com.rndeveloper.paparcar.data.datasource.local.room.UserProfileDao
import com.rndeveloper.paparcar.data.datasource.local.room.UserProfileEntity
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.UserProfileDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.VehicleDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [PROFILE-PHOTO-STUCK-AND-STALE-001] The remote profile doc is a snapshot of the FIRST sign-in;
 * the session is what Google says today. getOrCreateProfile must reconcile identity fields
 * (photo, name, email) — overwriting stale values, never erasing remote ones with provider nulls.
 */
class UserProfileRepositoryImplTest {

    private val remote = RecordingRemoteUserProfileDataSource()
    private val dao = FakeUserProfileDao()
    private val repository = UserProfileRepositoryImpl(remote, dao, "android", "1.8.3")

    private fun session(photoUrl: String? = null, displayName: String? = "Ana", email: String? = "ana@x.com") =
        UserSession(userId = "uid-1", email = email, displayName = displayName, photoUrl = photoUrl)

    private fun remoteDoc(photoUrl: String? = "https://old/photo.jpg") = UserProfileDto(
        userId = "uid-1",
        email = "ana@x.com",
        displayName = "Ana",
        photoUrl = photoUrl,
        createdAt = 1L,
        updatedAt = 1L,
        defaultVehicleId = "veh-1",
        firstStepsDismissedAt = 42L,
    )

    @Test
    fun should_pushIdentityUpdateAndServeMergedProfile_when_sessionPhotoDiffersFromRemote() = runTest {
        remote.profile = remoteDoc(photoUrl = "https://old/photo.jpg")

        val result = repository.getOrCreateProfile(session(photoUrl = "https://new/photo.jpg"))

        val profile = result.getOrThrow()
        assertEquals("https://new/photo.jpg", profile.photoUrl)
        val update = assertNotNull(remote.identityUpdate, "expected a field-targeted identity update")
        assertEquals("https://new/photo.jpg", update.photoUrl)
        // Field-targeted, never set(): the whole-doc write path must stay untouched.
        assertNull(remote.lastSetProfile)
        // The merged value also lands in Room, so the UI observes the fresh photo immediately.
        assertEquals("https://new/photo.jpg", dao.getProfile("uid-1")?.photoUrl)
    }

    @Test
    fun should_notTouchRemote_when_sessionMatchesRemoteDoc() = runTest {
        remote.profile = remoteDoc(photoUrl = "https://old/photo.jpg")

        repository.getOrCreateProfile(session(photoUrl = "https://old/photo.jpg"))

        assertNull(remote.identityUpdate)
        assertNull(remote.lastSetProfile)
    }

    @Test
    fun should_keepRemotePhoto_when_sessionPhotoIsNull() = runTest {
        remote.profile = remoteDoc(photoUrl = "https://old/photo.jpg")

        val result = repository.getOrCreateProfile(session(photoUrl = null))

        assertEquals("https://old/photo.jpg", result.getOrThrow().photoUrl)
        assertNull(remote.identityUpdate)
    }

    @Test
    fun should_createRemoteDoc_when_noRemoteAndNoCache() = runTest {
        remote.profile = null

        val result = repository.getOrCreateProfile(session(photoUrl = "https://new/photo.jpg"))

        assertEquals("https://new/photo.jpg", result.getOrThrow().photoUrl)
        assertEquals("https://new/photo.jpg", remote.lastSetProfile?.photoUrl)
        assertEquals("1.8.3", remote.lastSetProfile?.appVersion)
        assertEquals("android", remote.lastSetProfile?.platform)
    }

    @Test
    fun should_syncVersionWithoutReplacingProfile_when_versionIsMissing() = runTest {
        remote.profile = remoteDoc()
        repository.getOrCreateProfile(session()).getOrThrow()
        assertEquals("1.8.3", remote.profile?.appVersion)
        assertEquals("veh-1", remote.profile?.defaultVehicleId)
        assertEquals(42L, remote.profile?.firstStepsDismissedAt)
        assertNull(remote.lastSetProfile)
        repository.getOrCreateProfile(session()).getOrThrow()
        assertEquals(1, remote.appIdentityWrites)
    }

    @Test
    fun should_notReplaceAccount_when_remoteReadFailsWithoutLocalCache() = runTest {
        remote.failRead = true
        kotlin.test.assertTrue(repository.getOrCreateProfile(session()).isFailure)
        assertNull(remote.lastSetProfile)
    }

    @Test
    fun should_notCreateOrWriteProfile_when_syncIsCancelledDuringRemoteRead() = runTest {
        remote.readGate = CompletableDeferred()
        val job = launch { repository.getOrCreateProfile(session()) }
        testScheduler.runCurrent()
        job.cancel()
        job.join()
        assertNull(remote.lastSetProfile)
        assertEquals(0, remote.appIdentityWrites)
        assertNull(dao.getProfile("uid-1"))
    }

    @Test
    fun should_retryVersionAndKeepServingProfile_when_writeFails() = runTest {
        remote.profile = remoteDoc().copy(platform = "ios", appVersion = "1.0")
        remote.failAppIdentity = true
        repository.getOrCreateProfile(session()).getOrThrow()
        assertNull(remote.lastSetProfile)
        remote.failAppIdentity = false
        repository.getOrCreateProfile(session()).getOrThrow()
        assertEquals(2, remote.appIdentityWrites)
        assertEquals("android", remote.profile?.platform)
        assertEquals("1.8.3", remote.profile?.appVersion)
    }
}

// ─── Fakes ────────────────────────────────────────────────────────────────────

private class RecordingRemoteUserProfileDataSource : RemoteUserProfileDataSource {
    var profile: UserProfileDto? = null
    var lastSetProfile: UserProfileDto? = null
    var identityUpdate: IdentityUpdate? = null

    data class IdentityUpdate(val email: String?, val displayName: String?, val photoUrl: String?, val updatedAt: Long)

    var failRead = false
    var readGate: CompletableDeferred<Unit>? = null
    var appIdentityWrites = 0
    var failAppIdentity = false
    override suspend fun updateAppIdentity(userId: String, platform: String, appVersion: String) {
        appIdentityWrites++
        check(!failAppIdentity) { "offline" }
        profile = profile?.copy(platform = platform, appVersion = appVersion)
    }
    override suspend fun getProfile(userId: String): UserProfileDto? {
        readGate?.await()
        check(!failRead) { "read failed" }
        return profile
    }
    override suspend fun createOrUpdateProfile(profile: UserProfileDto) {
        lastSetProfile = profile
    }

    override suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?) = Unit
    override suspend fun updateFirstStepsDismissedAt(userId: String, dismissedAtMs: Long?) = Unit

    /** [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001] Recorded, not swallowed: the whole point of
     *  the field-targeted writer is WHICH fields it sends, and a `= Unit` double cannot say. */
    var detectionStateWrites = 0
    var lastDetectionState: List<Any?>? = null
    override suspend fun updateDetectionState(
        userId: String,
        autoDetectParking: Boolean,
        detectionBlockers: List<String>,
        batteryOptimizationExempt: Boolean,
        platform: String,
        appVersion: String,
        updatedAtMs: Long,
    ) {
        detectionStateWrites++
        lastDetectionState = listOf(userId, autoDetectParking, detectionBlockers, batteryOptimizationExempt, updatedAtMs)
    }
    override suspend fun updateInstallAttribution(
        userId: String,
        channel: String,
        source: String?,
        medium: String?,
        campaign: String?,
        metaCampaignId: String?,
        metaAdsetId: String?,
        metaAdId: String?,
        rawReferrer: String?,
        resolvedAtMs: Long,
    ) = Unit
    override suspend fun updateIdentityFields(
        userId: String,
        email: String?,
        displayName: String?,
        photoUrl: String?,
        updatedAt: Long,
    ) {
        identityUpdate = IdentityUpdate(email, displayName, photoUrl, updatedAt)
    }

    override suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto) = Unit
    override suspend fun clearParkingSessionActiveFlag(userId: String, sessionId: String) = Unit
    override suspend fun updateParkingSessionAddressAndPlace(
        userId: String,
        sessionId: String,
        address: AddressDto?,
        placeInfo: PlaceInfoDto?,
    ) = Unit

    override suspend fun getParkingHistory(userId: String): List<ParkingHistoryDto> = emptyList()
    override suspend fun deleteParkingSessionsForVehicle(userId: String, vehicleId: String) = Unit
    override suspend fun getVehicles(userId: String): List<VehicleDto> = emptyList()
    override suspend fun saveVehicle(userId: String, vehicle: VehicleDto) = Unit
    override suspend fun deleteVehicle(userId: String, vehicleId: String) = Unit
    override suspend fun updateVehicleActiveFlag(userId: String, vehicleId: String, isActive: Boolean) = Unit
    override suspend fun updateVehicleBluetoothDevice(userId: String, vehicleId: String, deviceAddress: String?) = Unit
    override suspend fun deleteUserData(userId: String) = Unit
}

private class FakeUserProfileDao : UserProfileDao {
    private val rows = MutableStateFlow<Map<String, UserProfileEntity>>(emptyMap())

    override suspend fun insertOrUpdate(profile: UserProfileEntity) {
        rows.value = rows.value + (profile.userId to profile)
    }

    override suspend fun getProfile(userId: String): UserProfileEntity? = rows.value[userId]

    override fun observeProfile(userId: String): Flow<UserProfileEntity?> =
        MutableStateFlow(rows.value[userId])

    override suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?) {
        rows.value[userId]?.let { insertOrUpdate(it.copy(defaultVehicleId = vehicleId)) }
    }

    override suspend fun deleteByUser(userId: String) {
        rows.value = rows.value - userId
    }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }
}
