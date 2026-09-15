package com.rndeveloper.paparcar.data.repository

import com.apptolast.baselogin.domain.model.UserSession
import com.rndeveloper.paparcar.data.datasource.local.room.UserProfileEntity
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarInviteDto
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeSharedCarDao
import com.rndeveloper.paparcar.fakes.FakeSharedCarRemote
import com.rndeveloper.paparcar.fakes.FakeUserProfileDao
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Opening a group, joining one, and walking out. [VEH-SHARED-001]
 *
 * The cases that matter are the ones where a wrong answer costs the user something real: a linked
 * car that spends their free slot, a code that lets a stranger in twice, and above all a departure
 * that takes somebody's own parking history with it.
 */
class SharedCarRepositoryImplTest {

    private val now = 1_000_000L
    private val ownerCar = Vehicle(
        id = "car-1",
        userId = "marta",
        name = "Kamiq",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        bluetoothDeviceId = "AA:BB:CC:DD:EE:FF",
    )

    private fun repository(
        uid: String,
        vehicles: FakeVehicleRepository,
        remote: FakeSharedCarRemote = FakeSharedCarRemote(),
        dao: FakeSharedCarDao = FakeSharedCarDao(),
    ) = SharedCarRepositoryImpl(
        remote = remote,
        dao = dao,
        profileDao = FakeUserProfileDao(
            UserProfileEntity(
                userId = uid,
                email = null,
                displayName = if (uid == "marta") "Marta" else "Nacho",
                photoUrl = null,
                createdAt = 0,
                updatedAt = 0,
            ),
        ),
        vehicleRepository = vehicles,
        authRepository = FakeAuthRepository(
            UserSession(userId = uid, email = null, displayName = null, photoUrl = null),
        ),
        randomBytes = { size -> ByteArray(size) { 7 } },
        clock = { now },
    )

    @Test
    fun should_linkTheOwnRowAndEnrolTheOwner_when_sharingACar() = runTest {
        val remote = FakeSharedCarRemote()
        val vehicles = FakeVehicleRepository(defaultVehicle = ownerCar)
        val repo = repository("marta", vehicles, remote)

        val car = repo.shareVehicle(ownerCar).getOrThrow()

        assertEquals("car-1", car.carId)
        assertEquals("marta", car.ownerUid)
        // The owner is a member of their own group, or nobody could read the document they created.
        assertEquals(listOf("marta"), remote.getMembers("car-1").map { it.uid })
        val own = vehicles.getVehicleById("marta", "car-1")!!
        assertEquals("car-1", own.sharedCarId)
        // ⛔ Still THEIRS: `ownerUid` stays null so the free limit keeps counting it.
        assertNull(own.ownerUid)
    }

    @Test
    fun should_publishAComparisonTokenAndNotTheMac_when_sharingACar() = runTest {
        val remote = FakeSharedCarRemote()
        val repo = repository("marta", FakeVehicleRepository(defaultVehicle = ownerCar), remote)

        repo.shareVehicle(ownerCar).getOrThrow()

        val published = remote.getCar("car-1")!!
        assertNotNull(published.btMacHash)
        assertTrue(published.toString().contains("AA:BB:CC:DD:EE:FF").not())
    }

    @Test
    fun should_refuse_when_sharingACarThatIsNotMine() = runTest {
        val joined = ownerCar.copy(userId = "nacho", ownerUid = "marta", sharedCarId = "car-1")
        val repo = repository("nacho", FakeVehicleRepository(defaultVehicle = joined))

        val error = repo.shareVehicle(joined).exceptionOrNull()

        assertEquals(PaparcarError.SharedCar.NotTheOwner, error)
    }

    @Test
    fun should_createALinkedRowWithTheirOwnPairing_when_joiningWithACode() = runTest {
        val remote = FakeSharedCarRemote()
        repository("marta", FakeVehicleRepository(defaultVehicle = ownerCar), remote)
            .shareVehicle(ownerCar).getOrThrow()
        remote.putInvite(liveInvite())
        val memberVehicles = FakeVehicleRepository()
        val member = repository("nacho", memberVehicles, remote)

        val car = member.joinByCode("ab3k9p", bluetoothDeviceId = "11:22:33:44:55:66").getOrThrow()

        assertEquals("car-1", car.carId)
        val linked = memberVehicles.getVehicleById("nacho", "car-1")!!
        assertEquals("marta", linked.ownerUid)
        assertEquals("car-1", linked.sharedCarId)
        // Each phone brings its own pairing: the owner's MAC never travelled.
        assertEquals("11:22:33:44:55:66", linked.bluetoothDeviceId)
        assertTrue(linked.isLinked)
    }

    @Test
    fun should_burnTheCode_when_itHasBeenUsedOnce() = runTest {
        val remote = FakeSharedCarRemote()
        repository("marta", FakeVehicleRepository(defaultVehicle = ownerCar), remote)
            .shareVehicle(ownerCar).getOrThrow()
        remote.putInvite(liveInvite())
        repository("nacho", FakeVehicleRepository(), remote)
            .joinByCode("AB3K9P", null).getOrThrow()

        val third = repository("laura", FakeVehicleRepository(), remote)
            .joinByCode("AB3K9P", null).exceptionOrNull()

        assertEquals(PaparcarError.SharedCar.InviteAlreadyUsed, third)
    }

    @Test
    fun should_refuseAnExpiredCode_when_joining() = runTest {
        val remote = FakeSharedCarRemote()
        repository("marta", FakeVehicleRepository(defaultVehicle = ownerCar), remote)
            .shareVehicle(ownerCar).getOrThrow()
        remote.putInvite(liveInvite().copy(expiresAtMs = now - 1))

        val error = repository("nacho", FakeVehicleRepository(), remote)
            .joinByCode("AB3K9P", null).exceptionOrNull()

        assertEquals(PaparcarError.SharedCar.InviteExpired, error)
    }

    @Test
    fun should_keepTheCarAndItsHistory_when_aMemberLeaves() = runTest {
        val remote = FakeSharedCarRemote()
        repository("marta", FakeVehicleRepository(defaultVehicle = ownerCar), remote)
            .shareVehicle(ownerCar).getOrThrow()
        remote.putInvite(liveInvite())
        val memberVehicles = FakeVehicleRepository()
        val member = repository("nacho", memberVehicles, remote)
        member.joinByCode("AB3K9P", null).getOrThrow()

        member.leave("car-1").getOrThrow()

        // ⛔ The row survives, unlinked. Deleting it would delete the member's own parkings in that
        // car [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001] — sharing lends the car, not the
        // memory of having used it.
        val left = memberVehicles.getVehicleById("nacho", "car-1")
        assertNotNull(left)
        assertNull(left.ownerUid)
        assertNull(left.sharedCarId)
        assertEquals(emptyList(), remote.getMembers("car-1").map { it.uid }.filter { it == "nacho" })
    }

    @Test
    fun should_unshareTheOwnRowAndSweepTheInvites_when_theOwnerDissolvesTheGroup() = runTest {
        val remote = FakeSharedCarRemote()
        val vehicles = FakeVehicleRepository(defaultVehicle = ownerCar)
        val owner = repository("marta", vehicles, remote)
        owner.shareVehicle(ownerCar).getOrThrow()
        owner.createInvite("car-1").getOrThrow()

        owner.stopSharing("car-1").getOrThrow()

        assertNull(remote.getCar("car-1"))
        assertEquals(emptyMap(), remote.invites.value)
        val own = vehicles.getVehicleById("marta", "car-1")!!
        assertNull(own.sharedCarId)
    }

    @Test
    fun should_refuseANewCode_when_theGroupIsAlreadyFull() = runTest {
        val remote = FakeSharedCarRemote()
        val owner = repository("marta", FakeVehicleRepository(defaultVehicle = ownerCar), remote)
        owner.shareVehicle(ownerCar).getOrThrow()
        listOf("a", "b", "c", "d").forEach { uid ->
            remote.putMember("car-1", com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarMemberDto(uid = uid))
        }

        val error = owner.createInvite("car-1").exceptionOrNull()

        assertEquals(PaparcarError.SharedCar.GroupFull, error)
    }

    @Test
    fun should_serveTheCachedCar_when_theMirrorHasNotAnsweredYet() = runTest {
        val remote = FakeSharedCarRemote()
        val dao = FakeSharedCarDao()
        val vehicles = FakeVehicleRepository(defaultVehicle = ownerCar)
        val repo = repository("marta", vehicles, remote, dao)
        repo.shareVehicle(ownerCar).getOrThrow()

        // The cache was filled by the share; a second repository over the SAME dao but an empty
        // remote is the offline reading.
        val offline = repository("marta", vehicles, FakeSharedCarRemote(), dao)

        assertEquals("Kamiq", offline.observeSharedCar("car-1").first()?.displayName)
    }

    private fun liveInvite() = SharedCarInviteDto(
        code = "AB3K9P",
        carId = "car-1",
        ownerUid = "marta",
        carName = "Kamiq",
        createdAtMs = now,
        expiresAtMs = now + 60_000L,
    )
}
