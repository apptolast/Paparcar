package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.VehicleType
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.fakes.MockScenario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Fake repository for tests and debug DI.
 * Covers the main vehicle config variants:
 *   vehicle_001 — no BT, isActive (detection via Coordinator)
 *   vehicle_002 — BT configured (detection via BluetoothStrategy), has active session
 *   vehicle_003 — motorcycle, no BT, rarely used
 *   vehicle_004 — van + BT configured, moderate use
 */
class FakeVehicleRepository(private val scenario: MockScenario? = null) : VehicleRepository {
    private val mockVehicles = listOf(
        Vehicle(
            id = "mock_vehicle_001",
            userId = "mock_user_001",
            name = "Mi Seat",
            brand = "Seat",
            model = "León",
            sizeCategory = VehicleSize.MEDIUM_SUV,
            vehicleType = VehicleType.CAR,
            isActive = true,
        ),
        Vehicle(
            id = "mock_vehicle_002",
            userId = "mock_user_001",
            brand = "Toyota",
            model = "Corolla",
            sizeCategory = VehicleSize.MEDIUM_SUV,
            vehicleType = VehicleType.CAR,
            bluetoothDeviceId = "AA:BB:CC:DD:EE:FF",
            isActive = false,
        ),
        Vehicle(
            id = "mock_vehicle_003",
            userId = "mock_user_001",
            name = "La Moto",
            brand = "Honda",
            model = "CBR 600",
            sizeCategory = VehicleSize.MOTORCYCLE,
            vehicleType = VehicleType.MOTORCYCLE,
            isActive = false,
        ),
        Vehicle(
            id = "mock_vehicle_004",
            userId = "mock_user_001",
            name = "Furgoneta",
            brand = "Ford",
            model = "Transit",
            sizeCategory = VehicleSize.VAN_HIGH,
            vehicleType = VehicleType.CAR,
            bluetoothDeviceId = "11:22:33:44:55:66",
            isActive = false,
        ),
    )

    private val _vehiclesFlow = MutableStateFlow(mockVehicles)

    /** The scenario list: empty for fresh accounts; the ACTIVE vehicle gains a paired-BT
     *  identity when the Dev Catalog flips that lever. [UX-PARKED-STATE-001] */
    private fun scenarioList(session: MockScenario.Session, activeBt: Boolean): List<Vehicle> =
        when {
            session != MockScenario.Session.LoggedInWithVehicles -> emptyList()
            activeBt -> mockVehicles.map { v ->
                if (v.isActive) v.copy(bluetoothDeviceId = ACTIVE_BT_MAC) else v
            }
            else -> mockVehicles
        }

    private fun currentList(): List<Vehicle> =
        if (scenario != null) {
            scenarioList(scenario.session.value, scenario.activeVehicleBluetooth.value)
        } else {
            mockVehicles
        }

    override fun observeVehicles(): Flow<List<Vehicle>> =
        if (scenario != null) {
            combine(scenario.session, scenario.activeVehicleBluetooth) { s, bt -> scenarioList(s, bt) }
        } else {
            _vehiclesFlow.asStateFlow()
        }

    override fun observeActiveVehicle(): Flow<Vehicle?> =
        observeVehicles().map { list -> list.find { it.isActive } }

    override suspend fun getActiveVehicle(userId: String): Vehicle? =
        currentList().find { it.isActive }

    override suspend fun getVehicleById(userId: String, vehicleId: String): Vehicle? =
        currentList().find { it.id == vehicleId }

    override suspend fun getVehicleByBluetoothDeviceId(deviceAddress: String): Vehicle? =
        currentList().find { it.bluetoothDeviceId == deviceAddress }

    override suspend fun syncFromRemote(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun saveVehicle(vehicle: Vehicle): Result<Unit> = Result.success(Unit)

    override suspend fun deleteVehicle(id: String): Result<Unit> = Result.success(Unit)

    override suspend fun setActiveVehicle(id: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateBluetoothDevice(vehicleId: String, deviceAddress: String?): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun hasVehicles(userId: String): Boolean = currentList().isNotEmpty()

    override suspend fun pushPendingVehicles(): Result<Unit> = Result.success(Unit)

    private companion object {
        /** MAC the Dev Catalog "BT on the active vehicle" lever pins to the active car. */
        const val ACTIVE_BT_MAC = "77:88:99:AA:BB:CC"
    }
}
