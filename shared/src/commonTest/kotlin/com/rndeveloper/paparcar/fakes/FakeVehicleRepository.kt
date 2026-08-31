package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleParkingFootprint
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVehicleRepository(
    defaultVehicle: Vehicle? = null,
    extraVehicles: List<Vehicle> = emptyList(),
) : VehicleRepository {

    // The default vehicle IS the active one (production's getActiveVehicle returns the isActive=1
    // row), so when a fixture flagged NO vehicle active — older tests that didn't read the flag —
    // promote the default. Never create a SECOND active: a fixture that deliberately flags an extra
    // active (to test "active wins over default") keeps its default inactive. [VEH-ACTIVE-FENCE-001]
    private val activeDefault: Vehicle? =
        if ((listOfNotNull(defaultVehicle) + extraVehicles).any { it.isActive }) defaultVehicle
        else defaultVehicle?.copy(isActive = true)
    private val _vehicles = MutableStateFlow<List<Vehicle>>(
        listOfNotNull(activeDefault) + extraVehicles,
    )
    private val _defaultVehicle = MutableStateFlow(activeDefault)

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

    /** What [getParkingFootprint] answers, per vehicle id. A vehicle with no entry has no history
     *  and no active parking. [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001] */
    val parkingFootprints = mutableMapOf<String, VehicleParkingFootprint>()

    override suspend fun getParkingFootprint(vehicleId: String): VehicleParkingFootprint =
        parkingFootprints[vehicleId]
            ?: VehicleParkingFootprint(endedParkings = 0, hasActiveParking = false)

    /** Ids whose parkings were deleted along with the vehicle, in order. */
    val deletedVehicleIds = mutableListOf<String>()

    override suspend fun deleteVehicle(id: String): Result<Unit> {
        // Mirrors production: a parked vehicle is refused, not silently deleted.
        if (getParkingFootprint(id).hasActiveParking) {
            return Result.failure(PaparcarError.Vehicle.DeleteBlockedByActiveParking)
        }
        deletedVehicleIds += id
        parkingFootprints.remove(id)
        _vehicles.value = _vehicles.value.filter { it.id != id }
        if (_defaultVehicle.value?.id == id) _defaultVehicle.value = null
        return Result.success(Unit)
    }

    /** Records every [setActiveVehicle] call, in order — for asserting vehicle-scoped arming. */
    val setActiveCalls = mutableListOf<String>()

    override suspend fun setActiveVehicle(id: String): Result<Unit> {
        setActiveCalls += id
        // Reflect the promotion so observeActiveVehicle() sees the newly declared (now active) vehicle.
        _vehicles.value.firstOrNull { it.id == id }?.let { _defaultVehicle.value = it.copy(isActive = true) }
        return Result.success(Unit)
    }

    override suspend fun updateBluetoothDevice(vehicleId: String, deviceAddress: String?): Result<Unit> = Result.success(Unit)

    var pushPendingCallCount = 0
        private set

    override suspend fun pushPendingVehicles(): Result<Unit> {
        pushPendingCallCount++
        return Result.success(Unit)
    }

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
