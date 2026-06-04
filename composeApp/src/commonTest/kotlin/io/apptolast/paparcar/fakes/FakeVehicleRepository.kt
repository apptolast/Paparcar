package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVehicleRepository(
    defaultVehicle: Vehicle? = null,
    extraVehicles: List<Vehicle> = emptyList(),
) : VehicleRepository {

    private val _vehicles = MutableStateFlow<List<Vehicle>>(
        listOfNotNull(defaultVehicle) + extraVehicles,
    )
    private val _defaultVehicle = MutableStateFlow(defaultVehicle)

    override fun observeVehicles(): Flow<List<Vehicle>> = _vehicles

    override fun observeActiveVehicle(): Flow<Vehicle?> = _defaultVehicle

    override suspend fun getActiveVehicle(userId: String): Vehicle? = _defaultVehicle.value

    override suspend fun getVehicleById(userId: String, vehicleId: String): Vehicle? =
        _vehicles.value.firstOrNull { it.id == vehicleId && it.userId == userId }

    override suspend fun getVehicleByBluetoothDeviceId(deviceAddress: String): Vehicle? =
        _vehicles.value.firstOrNull { it.bluetoothDeviceId.equals(deviceAddress, ignoreCase = true) }

    override suspend fun hasVehicles(userId: String): Boolean =
        _vehicles.value.any { it.userId == userId }

    var saveVehicleCallCount = 0
        private set
    val savedVehicleIds = mutableListOf<String>()
    /** Set to throw on next saveVehicle call. Cleared after each invocation. */
    var saveVehicleThrows: Throwable? = null
    /** Test hook: if set, saveVehicle awaits this Deferred before completing. Used to simulate
     *  an in-flight save so concurrent intents can be observed by the caller. */
    var saveVehicleAwait: CompletableDeferred<Unit>? = null

    override suspend fun saveVehicle(vehicle: Vehicle): Result<Unit> {
        saveVehicleCallCount++
        savedVehicleIds += vehicle.id
        saveVehicleThrows?.let { err -> saveVehicleThrows = null; return Result.failure(err) }
        saveVehicleAwait?.await()
        _vehicles.value = _vehicles.value.filter { it.id != vehicle.id } + vehicle
        _defaultVehicle.value = vehicle
        return Result.success(Unit)
    }

    override suspend fun deleteVehicle(id: String): Result<Unit> {
        _vehicles.value = _vehicles.value.filter { it.id != id }
        if (_defaultVehicle.value?.id == id) _defaultVehicle.value = null
        return Result.success(Unit)
    }

    override suspend fun setActiveVehicle(id: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateBluetoothDevice(vehicleId: String, deviceAddress: String?): Result<Unit> = Result.success(Unit)

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
