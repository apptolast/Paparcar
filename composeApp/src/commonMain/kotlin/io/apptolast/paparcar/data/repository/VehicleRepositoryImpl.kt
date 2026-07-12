@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.AuthRepository
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import io.apptolast.paparcar.data.datasource.local.room.UserProfileDao
import io.apptolast.paparcar.data.datasource.local.room.VehicleDao
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toDto
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.vehicle.VehicleActiveStatePolicy
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class VehicleRepositoryImpl(
    private val dao: VehicleDao,
    private val profileDao: UserProfileDao,
    private val userProfileDataSource: RemoteUserProfileDataSource,
    private val authRepository: AuthRepository,
    /**
     * Long-lived scope for firing the Firestore mirror WITHOUT blocking the caller. Local Room writes
     * are the source of truth (protected by pendingSync + the reconcile), so a save/edit returns as
     * soon as they land; the remote write — a `set().await()` that suspends until server ack, i.e.
     * forever while offline — runs here in the background and self-heals via the sync. [SYNC-RECONCILE-001]
     */
    private val syncScope: CoroutineScope,
) : VehicleRepository {

    private suspend fun currentUserId(): String? =
        authRepository.getCurrentSession()?.userId

    /**
     * Observes the current user's vehicles.
     *
     * Resolves the userId once via [AuthRepository.getCurrentSession] (cache-backed)
     * instead of `observeAuthState()`. The previous flatMapLatest implementation was
     * racing: BaseLogin's auth state Flow can emit a non-Authenticated value first
     * even though the session cache is populated, causing `.first()` callers to get
     * empty/null instantly without waiting for the next emit. [AUTH-001]
     *
     * Sign-out is handled at the navigation layer (the screen using this flow is
     * destroyed when the user leaves the authenticated graph), so the userId
     * snapshot is safe for the lifetime of any active subscriber.
     */
    override fun observeVehicles(): Flow<List<Vehicle>> = flow {
        val uid = currentUserId()
        if (uid == null) {
            emit(emptyList())
        } else {
            emitAll(dao.observeByUser(uid).map { list -> list.map { it.toDomain() } })
        }
    }

    override fun observeActiveVehicle(): Flow<Vehicle?> = flow {
        val uid = currentUserId()
        if (uid == null) {
            emit(null)
        } else {
            emitAll(dao.observeActive(uid).map { it?.toDomain() })
        }
    }

    /**
     * Suspending one-shot read of the active vehicle for [userId], with fallback
     * via `user_profile.defaultVehicleId` if the `vehicles` table lost its
     * `isActive=1` flag for any reason. Returning null here means **neither**
     * the vehicles table nor the user profile cache could resolve an active vehicle —
     * the caller (typically [io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase])
     * should treat that as a fatal precondition and refuse to save. [AUTH-001]
     */
    override suspend fun getActiveVehicle(userId: String): Vehicle? {
        dao.getActive(userId)?.let { return it.toDomain() }
        val profileDefaultId = profileDao.getProfile(userId)?.defaultVehicleId ?: return null
        return dao.getById(profileDefaultId, userId)?.toDomain()
    }

    override suspend fun getVehicleById(userId: String, vehicleId: String): Vehicle? =
        dao.getById(vehicleId, userId)?.toDomain()

    override suspend fun getVehicleByBluetoothDeviceId(deviceAddress: String): Vehicle? {
        val uid = currentUserId() ?: return null
        return dao.getByBluetoothDevice(uid, deviceAddress)?.toDomain()
    }

    /**
     * Pulls vehicles from Firestore into Room.
     * Following the "pure remote sync" agreement: local state is overwritten by remote
     * during bootstrap to ensure cross-device consistency. [VEHICLES-001]
     */
    override suspend fun syncFromRemote(userId: String): Result<Unit> = runCatching {
        PaparcarLogger.d(DIAG, "▶ syncFromRemote userId=$userId")
        val remoteEntities = userProfileDataSource.getVehicles(userId)
            .map { it.toEntity() }
        PaparcarLogger.d(DIAG, "  ← Firestore returned ${remoteEntities.size} vehicle(s)")
        remoteEntities.forEach { v ->
            PaparcarLogger.d(DIAG, "    vehicle id=${v.id} isActive=${v.isActive} name=${v.brand} ${v.model}")
        }
        if (remoteEntities.isEmpty()) {
            PaparcarLogger.e(DIAG, "  ✗ no vehicles from Firestore — merge skipped (local kept)")
            return@runCatching
        }
        // Reconcile instead of blind remote-wins: local rows with an un-synced (pending) edit are
        // preserved so an offline change isn't clobbered by a stale server snapshot. [SYNC-RECONCILE-001]
        val local = dao.getByUser(userId)
        val merged = reconcileVehicles(local = local, remote = remoteEntities)
        val keptPending = merged.count { it.pendingSync }
        if (keptPending > 0) PaparcarLogger.d(DIAG, "  ⟲ kept $keptPending local pending vehicle(s) over remote")
        val normalized = VehicleActiveStatePolicy.normalizeSingleActive(
            items = merged,
            isActive = { it.isActive },
            deactivate = { it.copy(isActive = false) },
        )
        if (normalized.count { it.isActive } != merged.count { it.isActive }) {
            PaparcarLogger.w(DIAG, "  ⚠ multiple isActive=true after merge — normalized to single active")
        }
        // [AUDIT-DATA-001 M5] Atomic swap — a process death between the old delete+insert pair
        // left the vehicles table momentarily empty.
        dao.replaceAllForUser(userId, normalized)
        PaparcarLogger.d(DIAG, "■ syncFromRemote merged ${normalized.size} vehicle(s) into Room (kept $keptPending pending)")
    }

    override suspend fun saveVehicle(vehicle: Vehicle): Result<Unit> = runCatching {
        currentUserId()?.let { uid ->
            val now = Clock.System.now().toEpochMilliseconds()
            // LOCAL writes (synchronous) — the UI finishes saving as soon as these land, so the button
            // never hangs on the remote ack. Single-active invariant: clear siblings' flag; they
            // changed → mark pending. New/edited row is stamped pending too. [SYNC-RECONCILE-001]
            val siblings = if (vehicle.isActive) dao.getByUser(uid).filter { it.id != vehicle.id } else emptyList()
            if (vehicle.isActive) {
                dao.clearActive(uid)
                siblings.forEach { dao.markPending(it.id, now) }
            }
            dao.insert(vehicle.toEntity(updatedAt = now, pendingSync = true))
            if (vehicle.isActive) profileDao.updateDefaultVehicleId(uid, vehicle.id)

            // REMOTE mirror in the background (best-effort). clearPending on ack is a fast-path — the
            // reconcile self-heals it otherwise. [SYNC-RECONCILE-001]
            syncScope.launch {
                siblings.forEach { sibling ->
                    runCatching { userProfileDataSource.updateVehicleActiveFlag(uid, sibling.id, false) }
                        .onSuccess { dao.clearPending(sibling.id) }
                        .onFailure { e -> PaparcarLogger.w(DIAG, "saveVehicle: sibling flag failed ${sibling.id}", e) }
                }
                runCatching { userProfileDataSource.saveVehicle(uid, vehicle.toDto()) }
                    .onSuccess { dao.clearPending(vehicle.id) }
                    .onFailure { e -> PaparcarLogger.w(DIAG, "saveVehicle: remote save failed ${vehicle.id}", e) }
                if (vehicle.isActive) {
                    runCatching { userProfileDataSource.updateDefaultVehicleId(uid, vehicle.id) }
                        .onFailure { e -> PaparcarLogger.w(DIAG, "saveVehicle: defaultVehicleId failed", e) }
                }
            }
        }
    }

    override suspend fun deleteVehicle(id: String): Result<Unit> = runCatching {
        val uid = currentUserId()
        dao.deleteById(id) // local — the row is gone immediately
        if (uid != null) {
            val now = Clock.System.now().toEpochMilliseconds()
            // If we just deleted the active vehicle, promote another (if any) to keep the
            // UserProfile.defaultVehicleId pointer valid — or clear it. LOCAL promotion first.
            // (The Vehicles screen blocks deleting the last vehicle, so in practice there is always
            // another candidate when this branch fires.)
            val cached = profileDao.getProfile(uid)
            val wasActive = cached?.defaultVehicleId == id
            val promoteId: String? = if (wasActive) {
                val remaining = dao.getByUser(uid)
                val newActiveId = VehicleActiveStatePolicy.promotionTarget(remaining.map { it.id })
                profileDao.updateDefaultVehicleId(uid, newActiveId)
                if (newActiveId != null) {
                    dao.setActive(newActiveId)
                    dao.markPending(newActiveId, now) // promoted flag changed → protect until confirmed
                }
                newActiveId
            } else {
                null
            }

            // Remote in the background so the delete button doesn't hang on the server ack.
            // [SYNC-RECONCILE-001]
            syncScope.launch {
                runCatching { userProfileDataSource.deleteVehicle(uid, id) }
                    .onFailure { e -> PaparcarLogger.w(DIAG, "deleteVehicle: remote delete failed $id", e) }
                if (wasActive) {
                    runCatching { userProfileDataSource.updateDefaultVehicleId(uid, promoteId) }
                        .onFailure { e -> PaparcarLogger.w(DIAG, "deleteVehicle: remote defaultVehicleId failed", e) }
                    if (promoteId != null) {
                        runCatching { userProfileDataSource.updateVehicleActiveFlag(uid, promoteId, true) }
                            .onSuccess { dao.clearPending(promoteId) }
                            .onFailure { e -> PaparcarLogger.w(DIAG, "deleteVehicle: promote active flag failed $promoteId", e) }
                    }
                }
            }
        }
    }

    override suspend fun setActiveVehicle(id: String): Result<Unit> = runCatching {
        val uid = currentUserId() ?: return@runCatching
        val now = Clock.System.now().toEpochMilliseconds()

        // Local writes first — the active vehicle is correct immediately even if the remote mirror
        // stalls/queues while offline. Mark the rows whose active flag actually changed as pending so
        // the next inbound sync won't revert this choice until Firestore confirms it. [SYNC-RECONCILE-001]
        val previouslyActive = dao.getByUser(uid).filter { it.isActive }.map { it.id }
        dao.clearActive(uid)
        dao.setActive(id)
        (previouslyActive + id).distinct().forEach { dao.markPending(it, now) }
        profileDao.updateDefaultVehicleId(uid, id)

        // Mirror to Firestore in the background so the caller isn't blocked on the server ack. Each
        // call is isolated; clearing pending on ack is a fast-path — the merge self-heals it regardless.
        val vehicles = dao.getByUser(uid)
        syncScope.launch {
            vehicles.forEach { entity ->
                runCatching { userProfileDataSource.updateVehicleActiveFlag(uid, entity.id, entity.id == id) }
                    .onSuccess { dao.clearPending(entity.id) }
                    .onFailure { e -> PaparcarLogger.w(DIAG, "setActiveVehicle: Firestore flag update failed for vehicle=${entity.id}", e) }
            }
            runCatching { userProfileDataSource.updateDefaultVehicleId(uid, id) }
                .onFailure { e -> PaparcarLogger.w(DIAG, "setActiveVehicle: Firestore defaultVehicleId update failed for vehicle=$id", e) }
        }
    }

    override suspend fun updateBluetoothDevice(vehicleId: String, deviceAddress: String?): Result<Unit> = runCatching {
        val now = Clock.System.now().toEpochMilliseconds()
        dao.updateBluetoothDevice(vehicleId, deviceAddress)
        dao.markPending(vehicleId, now)
        val uid = currentUserId() ?: return@runCatching
        // Sync to Firestore in the background so the pairing survives the next inbound sync, without
        // blocking the caller on the ack. [SYNC-RECONCILE-001]
        syncScope.launch {
            runCatching { userProfileDataSource.updateVehicleBluetoothDevice(uid, vehicleId, deviceAddress) }
                .onSuccess { dao.clearPending(vehicleId) }
                .onFailure { e -> PaparcarLogger.w(DIAG, "updateBluetoothDevice: remote failed $vehicleId", e) }
        }
    }

    override suspend fun deleteAllData(userId: String): Result<Unit> = runCatching {
        userProfileDataSource.deleteUserData(userId) // This handles both profile and sub-collections
        dao.deleteByUser(userId)
    }

    override suspend fun pushPendingVehicles(): Result<Unit> = runCatching {
        val pending = dao.getPendingSync()
        if (pending.isEmpty()) return@runCatching
        PaparcarLogger.d(DIAG, "▶ pushPendingVehicles — draining ${pending.size} pending vehicle(s)")
        pending.forEach { entity ->
            // Full-doc set covers every mutation kind (save / active flag / BT / color) in one write,
            // and stamps updatedAt remotely so the merge self-heals. Clear pending on ack. [SYNC-RECONCILE-001]
            runCatching { userProfileDataSource.saveVehicle(entity.userId, entity.toDomain().toDto()) }
                .onSuccess { dao.clearPending(entity.id) }
                .onFailure { e -> PaparcarLogger.w(DIAG, "pushPendingVehicles: push failed for vehicle=${entity.id}", e) }
        }
    }

    override suspend fun hasVehicles(userId: String): Boolean =
        dao.countByUser(userId) > 0

    private companion object {
        const val DIAG = "PARKDIAG/VehicleSync"
    }
}
