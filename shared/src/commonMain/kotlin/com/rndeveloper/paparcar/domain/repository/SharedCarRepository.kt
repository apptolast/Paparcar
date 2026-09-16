package com.rndeveloper.paparcar.domain.repository

import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarInvite
import com.rndeveloper.paparcar.domain.model.SharedCarMember
import com.rndeveloper.paparcar.domain.model.SharedCarSession
import com.rndeveloper.paparcar.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow

/**
 * The group's side of a shared car. [VEH-SHARED-001]
 *
 * Everything here is about the MIRROR (`sharedCars/{carId}`). The personal session repository is
 * untouched and stays the source of truth — this is the projection the group reads, and the only
 * place a second user's write ever lands.
 *
 * It is [UserScopedRepository] because the mirror holds two things that are unmistakably the
 * member's own: the trips they signed and the push tokens of their phone. Deleting the account has
 * to take both. [PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001]
 */
interface SharedCarRepository : UserScopedRepository {

    /** The car as the group sees it, live. Null while it is not (or no longer) shared. Served from
     *  the Room cache first so a screen opened without network still says where the car was. */
    fun observeSharedCar(carId: String): Flow<SharedCar?>

    /** Every shared car this user can see — theirs and the ones they joined. */
    fun observeSharedCars(): Flow<List<SharedCar>>

    fun observeMembers(carId: String): Flow<List<SharedCarMember>>

    /** The car's shared history, newest first. Mirror sessions only: no routes, ever. */
    fun observeSessions(carId: String, limit: Int = DEFAULT_SESSION_LIMIT): Flow<List<SharedCarSession>>

    /** Opens the group for [vehicle]: creates the mirror, enrols the owner, links the local row. */
    suspend fun shareVehicle(vehicle: Vehicle): Result<SharedCar>

    /** Dissolves the group: the mirror and its sessions go, the members' linked rows stop
     *  resolving, and the owner's own car and history are untouched. */
    suspend fun stopSharing(carId: String): Result<Unit>

    /** A fresh single-use code. Previous unclaimed invites for the car are revoked — one live code
     *  at a time is what makes "read it out loud" safe to do twice. */
    suspend fun createInvite(carId: String): Result<SharedCarInvite>

    /** Looks a code up without claiming it, so the join screen can show WHAT is being joined
     *  before the user commits. */
    suspend fun findInvite(code: String): Result<SharedCarInvite>

    /**
     * Redeems [code]: claims the invite, writes this user's member document and creates the linked
     * vehicle row. [bluetoothDeviceId] is the device the member paired for this car on THEIR phone
     * — the owner's MAC never travels, so each phone brings its own pairing.
     */
    suspend fun joinByCode(code: String, bluetoothDeviceId: String?): Result<SharedCar>

    /**
     * The member walks out. Their linked row goes with them; their personal history stays theirs.
     *
     * ⛔ And the trips they signed in the group go too. Until
     * [PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001] only the membership was removed, so
     * walking out left your recorded whereabouts in a group you no longer belong to — visible to
     * everyone still in it, and unreachable to you, because reading needs a membership you just
     * gave up. What stays theirs is the PERSONAL session, which never lived here.
     */
    suspend fun leave(carId: String): Result<Unit>

    /**
     * Turns THIS user's route sharing on or off for THIS car. [VEH-SHARED-001]
     *
     * Two writes on purpose: the local vehicle row (which is what the next push reads) and the
     * member document (so the others can be told why a line is missing). Turning it off does not
     * rewrite the past — the geometry already in the mirror goes on the next full push of each
     * session, exactly like the cloud-route retention promise keeps itself.
     */
    suspend fun setShareRoutes(carId: String, share: Boolean): Result<Unit>

    /** The owner shows somebody the door. */
    suspend fun removeMember(carId: String, uid: String): Result<Unit>

    /** One-shot pull of every mirror this user belongs to, into Room. Called at bootstrap next to
     *  the other syncs. */
    suspend fun syncFromRemote(): Result<Unit>

    companion object {
        const val DEFAULT_SESSION_LIMIT = 50
    }
}
