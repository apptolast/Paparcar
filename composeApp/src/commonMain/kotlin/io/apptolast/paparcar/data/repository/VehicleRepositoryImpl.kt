package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.AuthRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.local.room.VehicleDao
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.data.mapper.toVehicleEntity
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import com.apptolast.customlogin.domain.model.AuthState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleRepositoryImpl(
    private val dao: VehicleDao,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
) : VehicleRepository {

    private suspend fun currentUserId(): String? =
        authRepository.getCurrentSession()?.userId

    override fun observeVehicles(): Flow<List<Vehicle>> =
        authRepository.observeAuthState()
            .filter { it is AuthState.Authenticated } // Solo procedemos si hay un usuario real
            .flatMapLatest { state ->
                val uid = (state as AuthState.Authenticated).session.userId
                
                flow {
                    // Si Room está vacío, sincronizamos una vez de Firebase
                    if (dao.countByUser(uid) == 0) {
                        syncFromFirestore(uid)
                    }
                    emitAll(dao.observeByUser(uid).map { list -> list.map { it.toDomain() } })
                }
            }

    override fun observeDefaultVehicle(): Flow<Vehicle?> =
        authRepository.observeAuthState()
            .filter { it is AuthState.Authenticated }
            .flatMapLatest { state ->
                val uid = (state as AuthState.Authenticated).session.userId
                dao.observeDefault(uid).map { it?.toDomain() }
            }

    override suspend fun saveVehicle(vehicle: Vehicle) {
        dao.insert(vehicle.toEntity())
        currentUserId()?.let { uid ->
            firestoreVehiclesCol(uid).document(vehicle.id).set(vehicle.toFirestoreMap())
        }
    }

    override suspend fun deleteVehicle(id: String) {
        dao.deleteById(id)
        currentUserId()?.let { uid ->
            firestoreVehiclesCol(uid).document(id).delete()
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
    }

    override suspend fun updateBluetoothDevice(vehicleId: String, deviceAddress: String?) {
        // On-device only — intentionally never synced to Firestore
        dao.updateBluetoothDevice(vehicleId, deviceAddress)
    }

    override suspend fun deleteAllData(userId: String): Result<Unit> = runCatching {
        firestoreVehiclesCol(userId).get().documents.forEach { it.reference.delete() }
        dao.deleteByUser(userId)
    }

    private suspend fun syncFromFirestore(userId: String) {
        val snapshot = firestoreVehiclesCol(userId).get()
        val entities = snapshot.documents.mapNotNull { it.data<Map<String, Any?>>().toVehicleEntity() }
        if (entities.isNotEmpty()) dao.insertAll(entities)
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
