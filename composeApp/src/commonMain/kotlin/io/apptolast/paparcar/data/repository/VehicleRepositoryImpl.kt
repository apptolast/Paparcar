package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.AuthRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.local.room.VehicleDao
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class VehicleRepositoryImpl(
    private val dao: VehicleDao,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
) : VehicleRepository {

    private suspend fun currentUserId(): String? =
        authRepository.getCurrentSession()?.userId

    override fun observeVehicles(): Flow<List<Vehicle>> = flow {
        val uid = currentUserId() ?: return@flow
        emitAll(dao.observeByUser(uid).map { list -> list.map { it.toDomain() } })
    }

    override fun observeDefaultVehicle(): Flow<Vehicle?> = flow {
        val uid = currentUserId() ?: return@flow
        emitAll(dao.observeDefault(uid).map { it?.toDomain() })
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
            firestoreVehiclesCol(uid).document(entity.id)
                .update("isDefault" to (entity.id == id))
        }
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
