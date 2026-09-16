@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.repository

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.data.datasource.local.room.SharedCarDao
import com.rndeveloper.paparcar.data.datasource.local.room.UserProfileDao
import com.rndeveloper.paparcar.data.datasource.remote.RemoteSharedCarDataSource
import com.rndeveloper.paparcar.data.mapper.toDomain
import com.rndeveloper.paparcar.data.mapper.toDto
import com.rndeveloper.paparcar.data.mapper.toEntity
import com.rndeveloper.paparcar.domain.entitlement.canAddSharedCarMember
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarInvite
import com.rndeveloper.paparcar.domain.model.SharedCarMember
import com.rndeveloper.paparcar.domain.model.SharedCarRole
import com.rndeveloper.paparcar.domain.model.SharedCarSession
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.SharedCarRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.sharedcar.SharedCarInviteCode
import com.rndeveloper.paparcar.domain.sharedcar.SharedCarInviteRejection
import com.rndeveloper.paparcar.domain.sharedcar.rejectionFor
import com.rndeveloper.paparcar.domain.sharedcar.sharedCarBtToken
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.data.util.secureRandomBytes
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

/**
 * The group's side of a shared car. [VEH-SHARED-001]
 *
 * Reading is **cache-first and remote-authoritative**: the Room row answers instantly (offline
 * included) and every remote snapshot overwrites it. There is no outbox and no pendingSync — the
 * mirror is a projection, so a write that fails is corrected by the next push of the personal
 * session it mirrors, never by replaying local state.
 */
class SharedCarRepositoryImpl(
    private val remote: RemoteSharedCarDataSource,
    private val dao: SharedCarDao,
    private val profileDao: UserProfileDao,
    private val vehicleRepository: VehicleRepository,
    private val authRepository: AuthRepository,
    /** Solo para anotar una disolución que el usuario no pidió — ver `syncFromRemote`.
     *  [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001] */
    private val appPreferences: AppPreferences,
    /** Injected so a test can pin a code; production passes the platform's secure source. */
    private val randomBytes: (Int) -> ByteArray = ::secureRandomBytes,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : SharedCarRepository {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeSharedCar(carId: String): Flow<SharedCar?> =
        dao.observeById(carId).map { it?.toDomain() }
            // onStart(null): combine waits for BOTH sides, and offline the remote one may never
            // speak. Without the seed the screen would stay empty holding a perfectly good cached
            // answer — the exact failure the cache exists to prevent.
            .combine(remoteCar(carId).onStart { emit(null) }) { cached, live ->
                // Remote wins when it has spoken; the cache carries the answer until it does — and
                // keeps carrying it when the network never answers at all.
                live ?: cached
            }
            .distinctUntilChanged()

    private fun remoteCar(carId: String): Flow<SharedCar?> =
        remote.observeCar(carId).combine(remote.observeState(carId)) { car, state ->
            car?.toDomain(state)
        }.onEach { car ->
            if (car != null) dao.upsert(car.toEntity()) else dao.deleteById(carId)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeSharedCars(): Flow<List<SharedCar>> =
        // WHICH cars to watch is answered by the local vehicle rows, not by a Firestore query:
        // membership is a subcollection (see the ticket), and a `where memberUids contains me`
        // would need the array this design deliberately does not keep. Every shared car — mine or
        // one I joined — leaves a row in `vehicles` with `sharedCarId`, so that list IS the index.
        vehicleRepository.observeVehicles()
            .map { vehicles -> vehicles.mapNotNull { it.sharedCarId }.distinct() }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(emptyList())
                else combine(ids.map { observeSharedCar(it) }) { cars -> cars.filterNotNull() }
            }

    override fun observeMembers(carId: String): Flow<List<SharedCarMember>> =
        remote.observeMembers(carId).map { members -> members.map { it.toDomain() } }

    override fun observeSessions(carId: String, limit: Int): Flow<List<SharedCarSession>> =
        remote.observeSessions(carId).map { sessions ->
            // Sorted and capped here rather than with an orderBy+limit query: a group's mirror is
            // small by construction (one car, five people) and a server-side order would buy a
            // composite index for a list that fits in a phone's hand.
            sessions.map { it.toDomain() }
                .sortedByDescending { it.parkedAtMs }
                .take(limit)
        }

    override suspend fun shareVehicle(vehicle: Vehicle): Result<SharedCar> = runCatching {
        val uid = requireUid()
        // A car you joined is not yours to share onwards — the owner's group is the only group.
        if (vehicle.isLinked || vehicle.userId != uid) throw PaparcarError.SharedCar.NotTheOwner
        val now = clock()
        val profile = profileDao.getProfile(uid)
        val car = SharedCar(
            carId = vehicle.id,
            ownerUid = uid,
            ownerName = profile?.displayName,
            displayName = vehicle.displayName(fallback = vehicle.sizeCategory.name),
            btMacHash = sharedCarBtToken(vehicle.id, vehicle.bluetoothDeviceId),
            sizeCategory = vehicle.sizeCategory,
            carbodyType = vehicle.carbodyType,
            color = vehicle.color,
            // The car's spec travels so the group's figures come out the same for everyone; the
            // price does not, because what a litre costs is each reader's own. [VEH-SHARED-001]
            fuelType = vehicle.fuelType,
            consumptionPer100 = vehicle.consumptionPer100,
            consumptionElectricPer100 = vehicle.consumptionElectricPer100,
            createdAtMs = now,
        )
        remote.createCar(
            car = car.toDto(),
            owner = SharedCarMember(
                uid = uid,
                displayName = profile?.displayName,
                photoUrl = profile?.photoUrl,
                joinedAtMs = now,
                role = SharedCarRole.OWNER,
                sharesRoutes = vehicle.shareRoutesInGroup,
            ).toDto(),
        )
        // The owner's OWN row learns it is shared. `ownerUid` stays null: a car you opened up is
        // still yours, and the free limit must keep counting it. [VEH-SHARED-001]
        vehicleRepository.saveVehicle(vehicle.copy(sharedCarId = car.carId)).getOrThrow()
        dao.upsert(car.toEntity())
        car
    }

    override suspend fun stopSharing(carId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val car = remote.getCar(carId) ?: throw PaparcarError.SharedCar.SyncFailed
        if (car.ownerUid != uid) throw PaparcarError.SharedCar.NotTheOwner
        remote.deleteCar(carId)
        dao.deleteById(carId)
        // The car itself survives, with its history and its pairing: dissolving the group takes
        // away the mirror, never the vehicle.
        vehicleRepository.getVehicleById(uid, carId)?.let { own ->
            vehicleRepository.saveVehicle(own.copy(sharedCarId = null)).getOrThrow()
        }
    }

    override suspend fun createInvite(carId: String): Result<SharedCarInvite> = runCatching {
        val uid = requireUid()
        val car = remote.getCar(carId) ?: throw PaparcarError.SharedCar.SyncFailed
        if (car.ownerUid != uid) throw PaparcarError.SharedCar.NotTheOwner
        val members = remote.getMembers(carId).count { it.uid != car.ownerUid }
        if (!canAddSharedCarMember(members)) throw PaparcarError.SharedCar.GroupFull
        // One live code per car: the previous one dies here, so a code read out loud yesterday
        // cannot be used by whoever overheard it tomorrow.
        remote.deleteInvitesFor(carId)
        val now = clock()
        val invite = SharedCarInvite(
            code = SharedCarInviteCode.of(randomBytes(SharedCarInviteCode.LENGTH)),
            carId = carId,
            ownerUid = uid,
            createdAtMs = now,
            expiresAtMs = now + SharedCarInviteCode.INVITE_TTL_MS,
        )
        remote.putInvite(invite.toDto(carName = car.displayName))
        remote.setActiveInvite(carId, invite.code, invite.expiresAtMs)
        invite
    }

    override suspend fun findInvite(code: String): Result<SharedCarInvite> = runCatching {
        val normalized = SharedCarInviteCode.normalize(code)
            ?: throw PaparcarError.SharedCar.InviteNotFound
        val invite = remote.getInvite(normalized)?.toDomain()
            ?: throw PaparcarError.SharedCar.InviteNotFound
        rejectionFor(invite, nowMs = clock())?.let { throw it.asError() }
        invite
    }

    override suspend fun joinByCode(code: String, bluetoothDeviceId: String?): Result<SharedCar> =
        runCatching {
            val uid = requireUid()
            val invite = findInvite(code).getOrThrow()
            if (invite.ownerUid == uid) throw PaparcarError.SharedCar.AlreadyJoined
            val now = clock()
            val profile = profileDao.getProfile(uid)
            // ⛔ Nothing about the car is read BEFORE this write, and that is the rules' doing:
            // reading `sharedCars/{carId}` requires being a member, so the invite has to be enough
            // on its own. The membership document is the door — it cites the code, and the rule
            // checks the invite on the server. Only afterwards is the car readable.
            remote.putMember(
                carId = invite.carId,
                member = SharedCarMember(
                    uid = uid,
                    displayName = profile?.displayName,
                    photoUrl = profile?.photoUrl,
                    joinedAtMs = now,
                    role = SharedCarRole.MEMBER,
                ).toDto(inviteCode = invite.code),
            )
            // Claimed AFTER the membership lands: a code burnt by a join that then failed would
            // leave the person outside with nothing left to type.
            remote.claimInvite(invite.code, uid)
            val joined = (remote.getCar(invite.carId) ?: throw PaparcarError.SharedCar.SyncFailed).toDomain()
            linkVehicle(uid, joined, bluetoothDeviceId)
            dao.upsert(joined.toEntity())
            joined
        }

    /**
     * The member's own row for somebody else's car: same id as the mirror, their OWN pairing, and
     * `ownerUid` set so the free limit skips it. From here the BT strategy treats it like any other
     * car — which is the whole reason the linked row exists instead of a special case in detection.
     */
    private suspend fun linkVehicle(uid: String, car: SharedCar, bluetoothDeviceId: String?) {
        val existing = vehicleRepository.getVehicleById(uid, car.carId)
        val linked = (existing ?: Vehicle(
            id = car.carId,
            userId = uid,
            name = car.displayName,
            sizeCategory = car.sizeCategory ?: VehicleSize.MEDIUM_SUV,
            carbodyType = car.carbodyType,
            color = car.color,
        )).copy(
            bluetoothDeviceId = bluetoothDeviceId ?: existing?.bluetoothDeviceId,
            sharedCarId = car.carId,
            ownerUid = car.ownerUid,
        )
        vehicleRepository.saveVehicle(linked).getOrThrow()
    }

    override suspend fun leave(carId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        // ⛔ Sessions and the head of the mirror BEFORE the membership: both need `isMember()`, so
        // the door has to still be open when we sweep. Inverting these lines deletes nothing and
        // reports success. [PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001]
        remote.deleteSessionsOf(carId, uid)
        // The sessions were the history; this is the card the group looks at every day, and it
        // kept naming whoever had just walked out.
        // [PRIV-LEAVING-A-GROUP-MUST-CLEAR-YOUR-NAME-FROM-ITS-HEAD-001]
        remote.clearMemberFromState(carId, uid)
        remote.deleteMember(carId, uid)
        dao.deleteById(carId)
        vehicleRepository.getVehicleById(uid, carId)
            ?.takeIf { it.isLinked }
            ?.let {
                vehicleRepository.saveVehicle(it.unlinked()).getOrThrow()
                // La MISMA pregunta que cuando lo cierra el dueño, y por el mismo motivo: salir del
                // grupo es una decisión sobre el GRUPO, no sobre el coche. La fila se queda y pasa
                // a ocupar plaza, y hoy eso no lo dice nadie — «Salir de este coche» no tiene
                // siquiera diálogo. [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001]
                appPreferences.addDissolvedSharedCar(it.id)
            }
    }

    /**
     * Account deletion, expressed as the two exits that already exist. [GDPR right-to-erasure]
     *
     * There is no third verb for this: a car you own dissolves ([stopSharing]) and a car you were
     * lent you walk out of ([leave]). Dissolving takes the mirror away from the whole group, and
     * that is the right reading, not collateral damage — the mirror is a projection of YOUR
     * vehicle, and nobody loses their own history, which lives in their own account. The doc on
     * `Vehicle.unlinked()` says it for the other direction: sharing lends the car, not the memory
     * of using it.
     *
     * ⛔ Must run BEFORE `VehicleRepository` in the deletion chain: the cars are how the groups are
     * found. Wired that way in `DomainModule`, and a guard test holds the wiring.
     */
    override suspend fun deleteAllData(userId: String): Result<Unit> = runCatching {
        val shared = vehicleRepository.observeVehicles().first().filter { it.isShared }
        shared.forEach { vehicle ->
            val carId = vehicle.sharedCarId ?: return@forEach
            // Failure-tolerant per car on purpose: a group that cannot be reached must not stop
            // the account deletion, which is the thing the user actually asked for. What a skipped
            // group costs is logged, not swallowed in silence.
            val outcome = if (remote.getCar(carId)?.ownerUid == userId) {
                stopSharing(carId)
            } else {
                leave(carId)
            }
            outcome.onFailure { PaparcarLogger.w(TAG, "shared car $carId not swept — ${it.message}") }
        }
    }

    override suspend fun setShareRoutes(carId: String, share: Boolean): Result<Unit> = runCatching {
        val uid = requireUid()
        val vehicle = vehicleRepository.getVehicleById(uid, carId)
            ?: throw PaparcarError.SharedCar.SyncFailed
        vehicleRepository.saveVehicle(vehicle.copy(shareRoutesInGroup = share)).getOrThrow()
        remote.setMemberSharesRoutes(carId, uid, share)
    }

    override suspend fun removeMember(carId: String, uid: String): Result<Unit> = runCatching {
        val me = requireUid()
        val car = remote.getCar(carId) ?: throw PaparcarError.SharedCar.SyncFailed
        if (car.ownerUid != me) throw PaparcarError.SharedCar.NotTheOwner
        // ⛔ Lo MISMO que se lleva quien se va por su pie, y por una razón más fuerte: al expulsado
        // se le cierra la puerta detrás. Su fila se desvincula al sincronizar, así que su propio
        // borrado de cuenta ya no encontrará este grupo — si no se limpia aquí, sus recorridos se
        // quedan dentro para siempre, firmados por alguien que ya no está.
        // [PRIV-A-REMOVED-MEMBER-LEAVES-THEIR-TRIPS-BEHIND-001]
        remote.deleteSessionsOf(carId, uid)
        remote.clearMemberFromState(carId, uid)
        remote.deleteMember(carId, uid)
    }

    override suspend fun retirePushTokens(): Result<Unit> = runCatching {
        val uid = requireUid()
        val carIds = vehicleRepository.observeVehicles().first().mapNotNull { it.sharedCarId }.distinct()
        carIds.forEach { carId ->
            // Tolerante por coche, como el resto del espejo: un grupo inalcanzable no puede impedir
            // que el usuario cierre sesion, que es lo que ha pedido.
            runCatching { remote.setMemberTokens(carId, uid, emptyList()) }
                .onFailure { PaparcarLogger.w(TAG, "no se pudo retirar el token de $carId — ${it.message}") }
        }
    }

    override suspend fun syncFromRemote(): Result<Unit> = runCatching {
        val uid = requireUid()
        val shared = vehicleRepository.observeVehicles().first().filter { it.isShared }
        shared.forEach { vehicle ->
            val carId = vehicle.sharedCarId ?: return@forEach
            val remoteCar = remote.getCar(carId)
            if (remoteCar == null) {
                // The group is gone — dissolved, or we were shown the door. Same treatment as
                // walking out: the row stops being linked, it does not disappear.
                PaparcarLogger.i(TAG, "shared car $carId no longer readable — unlinking")
                dao.deleteById(carId)
                vehicleRepository.saveVehicle(vehicle.unlinked())
                // ⛔ Y se anota, porque esto NO lo pidió quien lo sufre: la fila deja de ser
                // prestada y pasa a contar para su límite de coches sin que haya tocado nada.
                // Que lo conserve o lo borre es decisión suya, y la pregunta tiene que sobrevivir
                // a que la app estuviera cerrada cuando el dueño cerró el grupo.
                // [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001]
                appPreferences.addDissolvedSharedCar(vehicle.id)
            } else {
                dao.upsert(remoteCar.toDomain().toEntity())
            }
        }
    }

    private suspend fun requireUid(): String =
        authRepository.getCurrentSession()?.userId ?: throw PaparcarError.Auth.NotAuthenticated

    private fun SharedCarInviteRejection.asError(): PaparcarError.SharedCar = when (this) {
        SharedCarInviteRejection.EXPIRED -> PaparcarError.SharedCar.InviteExpired
        SharedCarInviteRejection.ALREADY_USED -> PaparcarError.SharedCar.InviteAlreadyUsed
        SharedCarInviteRejection.WRONG_CAR -> PaparcarError.SharedCar.InviteNotFound
    }

    private companion object {
        const val TAG = "SharedCarRepository"
    }
}

/**
 * The row, no longer part of any group. [VEH-SHARED-001]
 *
 * ⛔ **Leaving a group never DELETES the car row**, tempting as it looks for a car that was never
 * theirs: `deleteVehicle` takes the vehicle's whole parking history with it
 * [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001], and those parkings are the member's own —
 * they drove, they parked, the trips are theirs. Sharing lends the car, not the memory of using it.
 * What walking out removes is the LINK; the car stays as an ordinary one of theirs, and the free
 * limit treats it like every other existing car: nothing is deleted, only the NEXT one is gated.
 */
private fun Vehicle.unlinked(): Vehicle = copy(sharedCarId = null, ownerUid = null)
