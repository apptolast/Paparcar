package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.data.datasource.local.room.UserProfileDao
import io.apptolast.paparcar.data.datasource.local.room.VehicleDao
import io.apptolast.paparcar.data.datasource.local.room.VehicleEntity
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toDto
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.repository.VehicleRepository
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

    override fun observeDefaultVehicle(): Flow<Vehicle?> = flow {
        val uid = currentUserId()
        if (uid == null) {
            emit(null)
        } else {
            emitAll(dao.observeDefault(uid).map { it?.toDomain() })
        }
    }

    /**
     * Suspending one-shot read of the default vehicle for [userId], with fallback
     * via `user_profile.defaultVehicleId` if the `vehicles` table lost its
     * `isDefault=1` flag for any reason. Returning null here means **neither**
     * the vehicles table nor the user profile cache could resolve a default —
     * the caller (typically [io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase])
     * should treat that as a fatal precondition and refuse to save. [AUTH-001]
     */
    override suspend fun getDefaultVehicle(userId: String): Vehicle? {
        dao.getDefault(userId)?.let { return it.toDomain() }
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
            PaparcarLogger.d(DIAG, "    vehicle id=${v.id} isDefault=${v.isDefault} name=${v.brand} ${v.model}")
        }
        if (remoteEntities.isEmpty()) {
            PaparcarLogger.e(DIAG, "  ✗ no vehicles from Firestore — upsert skipped")
            return@runCatching
        }
        val normalized = enforceAtMostOneDefault(remoteEntities)
        if (normalized.count { it.isDefault } != remoteEntities.count { it.isDefault }) {
            PaparcarLogger.w(DIAG, "  ⚠ multiple isDefault=true in remote data — normalized to single default")
        }
        dao.deleteByUser(userId)
        dao.upsertAll(normalized)
        PaparcarLogger.d(DIAG, "■ syncFromRemote replaced local with ${normalized.size} remote vehicle(s) in Room")
    }

    override suspend fun saveVehicle(vehicle: Vehicle) {
        currentUserId()?.let { uid ->
            if (vehicle.isDefault) {
                // Enforce single-default invariant before inserting: clear the flag on
                // all sibling vehicles in Room and Firestore so we never end up with two
                // rows where isDefault=1.
                dao.clearDefault(uid)
                dao.getByUser(uid)
                    .filter { it.id != vehicle.id }
                    .forEach { userProfileDataSource.updateVehicleDefaultFlag(uid, it.id, false) }
            }
            dao.insert(vehicle.toEntity())
            userProfileDataSource.saveVehicle(uid, vehicle.toDto())
            if (vehicle.isDefault) {
                profileDao.updateDefaultVehicleId(uid, vehicle.id)
                userProfileDataSource.updateDefaultVehicleId(uid, vehicle.id)
            }
        }
    }

    override suspend fun deleteVehicle(id: String) {
        val uid = currentUserId()
        dao.deleteById(id)
        if (uid != null) {
            userProfileDataSource.deleteVehicle(uid, id)
            // If we just deleted the default vehicle, promote another (if any) to
            // keep the UserProfile.defaultVehicleId pointer valid — or clear it.
            // (The Vehicles screen blocks deleting the last vehicle, so in practice
            // there is always another candidate when this branch fires.)
            val cached = profileDao.getProfile(uid)
            if (cached?.defaultVehicleId == id) {
                val remaining = dao.getByUser(uid)
                val newDefault = remaining.firstOrNull()
                profileDao.updateDefaultVehicleId(uid, newDefault?.id)
                userProfileDataSource.updateDefaultVehicleId(uid, newDefault?.id)
                if (newDefault != null) dao.setDefault(newDefault.id)
            }
        }
    }

    override suspend fun setDefaultVehicle(id: String) {
        val uid = currentUserId() ?: return
        dao.clearDefault(uid)
        dao.setDefault(id)

        // Mirror isDefault flag in Firestore for all user's vehicles
        val vehicles = dao.getByUser(uid)
        vehicles.forEach { entity ->
            userProfileDataSource.updateVehicleDefaultFlag(uid, entity.id, entity.id == id)
        }

        // And on the user profile cache (local + remote).
        profileDao.updateDefaultVehicleId(uid, id)
        userProfileDataSource.updateDefaultVehicleId(uid, id)
    }

    override suspend fun updateBluetoothDevice(vehicleId: String, deviceAddress: String?) {
        // On-device only — intentionally never synced to Firestore
        dao.updateBluetoothDevice(vehicleId, deviceAddress)
    }

    override suspend fun deleteAllData(userId: String): Result<Unit> = runCatching {
        userProfileDataSource.deleteUserData(userId) // This handles both profile and sub-collections
        dao.deleteByUser(userId)
    }

    override suspend fun hasVehicles(userId: String): Boolean =
        dao.countByUser(userId) > 0

    private fun enforceAtMostOneDefault(entities: List<VehicleEntity>): List<VehicleEntity> {
        if (entities.count { it.isDefault } <= 1) return entities
        var kept = false
        return entities.map { entity ->
            when {
                !entity.isDefault -> entity
                !kept -> entity.also { kept = true }
                else -> entity.copy(isDefault = false)
            }
        }
    }

    private companion object {
        const val DIAG = "PARKDIAG/VehicleSync"
    }
}
