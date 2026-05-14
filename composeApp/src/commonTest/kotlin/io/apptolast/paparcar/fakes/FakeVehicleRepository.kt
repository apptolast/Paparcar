package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVehicleRepository(
    defaultVehicle: Vehicle? = null,
) : VehicleRepository {

    private val _vehicles = MutableStateFlow<List<Vehicle>>(listOfNotNull(defaultVehicle))
    private val _defaultVehicle = MutableStateFlow(defaultVehicle)

    override fun observeVehicles(): Flow<List<Vehicle>> = _vehicles

    override fun observeDefaultVehicle(): Flow<Vehicle?> = _defaultVehicle

    override suspend fun getDefaultVehicle(userId: String): Vehicle? = _defaultVehicle.value

    override suspend fun saveVehicle(vehicle: Vehicle) {
        _vehicles.value = _vehicles.value.filter { it.id != vehicle.id } + vehicle
        _defaultVehicle.value = vehicle
    }

    override suspend fun deleteVehicle(id: String) {
        _vehicles.value = _vehicles.value.filter { it.id != id }
        if (_defaultVehicle.value?.id == id) _defaultVehicle.value = null
    }

    override suspend fun setDefaultVehicle(id: String) {}

    override suspend fun updateBluetoothDevice(vehicleId: String, deviceAddress: String?) {}

    var syncFromRemoteCallCount = 0
        private set
    var syncFromRemoteResult: Result<Unit> = Result.success(Unit)

    override suspend fun syncFromRemote(userId: String): Result<Unit> {
        syncFromRemoteCallCount++
        return syncFromRemoteResult
    }

    var deleteAllDataCallCount = 0
        private set
    var deleteAllDataResult: Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(userId: String): Result<Unit> {
        deleteAllDataCallCount++
        if (deleteAllDataResult.isSuccess) {
            _vehicles.value = emptyList()
            _defaultVehicle.value = null
        }
        return deleteAllDataResult
    }
}
