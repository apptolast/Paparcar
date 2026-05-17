package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.AuthRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.local.room.UserProfileDao
import io.apptolast.paparcar.data.datasource.local.room.VehicleDao
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.data.mapper.toVehicleEntity
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class VehicleRepositoryImpl(
    private val dao: VehicleDao,
    private val profileDao: UserProfileDao,
    private val userProfileDataSource: RemoteUserProfileDataSource,
    private val firestore: FirebaseFirestore,
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

    /**
     * Pulls vehicles from Firestore into Room with REPLACE-conflict semantics so changes
     * made on other devices (e.g. `isDefault` flip) actually land here. [VEHICLES-001]
     *
     * Preserves `bluetoothDeviceId` per-row: that field is on-device only, never written
     * to Firestore, so a naive REPLACE would wipe the pairing on every sync. For each
     * remote entity we look up the existing local row and merge its BT id back in before
     * the upsert.
     */
    override suspend fun syncFromRemote(userId: String): Result<Unit> = runCatching {
        val snapshot = firestoreVehiclesCol(userId).get()
        val remoteEntities = snapshot.documents.mapNotNull { it.toVehicleEntity() }
        if (remoteEntities.isEmpty()) return@runCatching
        val merged = remoteEntities.map { remote ->
            val localBt = dao.getById(remote.id, userId)?.bluetoothDeviceId
            if (localBt != null) remote.copy(bluetoothDeviceId = localBt) else remote
        }
        dao.upsertAll(merged)
    }

    override suspend fun saveVehicle(vehicle: Vehicle) {
        dao.insert(vehicle.toEntity())
        currentUserId()?.let { uid ->
            firestoreVehiclesCol(uid).document(vehicle.id).set(vehicle.toFirestoreMap())
            // If this is being saved as the default, mirror it on the user profile
            // so the splash can decide hasVehicle without a list query.
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
            firestoreVehiclesCol(uid).document(id).delete()
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
            @Suppress("DEPRECATION")
            firestoreVehiclesCol(uid).document(entity.id)
                .update("isDefault" to (entity.id == id))
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
        firestoreVehiclesCol(userId).get().documents.forEach { it.reference.delete() }
        dao.deleteByUser(userId)
    }

    private fun firestoreVehiclesCol(userId: String) =
        firestore.collection("users").document(userId).collection("vehicles")

    private companion object {
        private fun Vehicle.toFirestoreMap(): Map<String, Any?> = mapOf(
            "id" to id,
            "userId" to userId,
            "brand" to brand,
            "model" to model,
            "sizeCategory" to sizeCategory.name,
            "showBrandModelOnSpot" to showBrandModelOnSpot,
            "isDefault" to isDefault,
            // bluetoothDeviceId intentionally excluded — on-device only
        )
    }
}
