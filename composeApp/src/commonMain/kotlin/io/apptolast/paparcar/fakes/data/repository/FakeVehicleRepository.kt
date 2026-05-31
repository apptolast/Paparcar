package io.apptolast.paparcar.fakes.data.repository

import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeVehicleRepository : VehicleRepository {
    private val mockVehicles = listOf(
        Vehicle(
            id = "mock_vehicle_001",
            userId = "mock_user_001",
            brand = "Seat",
            model = "León",
            sizeCategory = VehicleSize.MEDIUM,
            vehicleType = VehicleType.CAR,
            isDefault = true
        ),
        Vehicle(
            id = "mock_vehicle_002",
            userId = "mock_user_001",
            brand = "Toyota",
            model = "Corolla",
            sizeCategory = VehicleSize.MEDIUM,
            vehicleType = VehicleType.CAR,
            isDefault = false
        )
    )

    private val _vehiclesFlow = MutableStateFlow(mockVehicles)

    override fun observeVehicles(): Flow<List<Vehicle>> = _vehiclesFlow.asStateFlow()

    override fun observeDefaultVehicle(): Flow<Vehicle?> =
        _vehiclesFlow.map { list -> list.find { it.isDefault } }

    override suspend fun getDefaultVehicle(userId: String): Vehicle? =
        mockVehicles.find { it.isDefault }

    override suspend fun getVehicleById(userId: String, vehicleId: String): Vehicle? =
        mockVehicles.find { it.id == vehicleId }

    override suspend fun getVehicleByBluetoothDeviceId(deviceAddress: String): Vehicle? = null

    override suspend fun syncFromRemote(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun saveVehicle(vehicle: Vehicle) {
        // no-op
    }

    override suspend fun deleteVehicle(id: String) {
        // no-op
    }

    override suspend fun setDefaultVehicle(id: String) {
        // no-op
    }

    override suspend fun updateBluetoothDevice(vehicleId: String, deviceAddress: String?) {
        // no-op
    }

    override suspend fun deleteAllData(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun hasVehicles(userId: String): Boolean = true
}
