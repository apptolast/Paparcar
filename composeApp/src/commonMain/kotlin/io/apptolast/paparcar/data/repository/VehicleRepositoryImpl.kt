package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.AuthRepository
import com.apptolast.customlogin.domain.model.AuthState
import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.local.room.UserProfileDao
import io.apptolast.paparcar.data.datasource.local.room.VehicleDao
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.data.mapper.toVehicleEntity
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
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
     * Observes the current user's vehicles, auto-resubscribing when auth state changes.
     *
     * Unlike the previous implementation, this never completes prematurely on a
     * null userId — it emits an empty list instead, which keeps downstream
     * `first()` / `firstOrNull()` calls safe during the login race window where
     * AuthState reports Authenticated before the session cache is populated.
     */
    override fun observeVehicles(): Flow<List<Vehicle>> =
        authRepository.observeAuthState().flatMapLatest { auth ->
            val uid = (auth as? AuthState.Authenticated)?.session?.userId
            if (uid == null) flowOf(emptyList())
            else dao.observeByUser(uid).map { list -> list.map { it.toDomain() } }
        }

    override fun observeDefaultVehicle(): Flow<Vehicle?> =
        authRepository.observeAuthState().flatMapLatest { auth ->
            val uid = (auth as? AuthState.Authenticated)?.session?.userId
            if (uid == null) flowOf(null)
            else dao.observeDefault(uid).map { it?.toDomain() }
        }

    override suspend fun syncFromRemote(userId: String): Result<Unit> = runCatching {
        val snapshot = firestoreVehiclesCol(userId).get()
        val entities = snapshot.documents.mapNotNull { it.data<Map<String, Any?>>().toVehicleEntity() }
        // Replace local copy: remote is the source of truth for cross-device sync.
        // Bluetooth pairing is on-device only (excluded from Firestore), so re-import
        // does not clobber it because the field lives on the entity, not the remote doc —
        // but `insertAll` with REPLACE conflict resolution would. Keep insertAll IGNORE
        // semantics for now to preserve local-only fields. Edit/delete go through
        // explicit per-vehicle methods that know to handle BT.
        if (entities.isNotEmpty()) dao.insertAll(entities)
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
