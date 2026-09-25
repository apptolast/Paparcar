package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.EnergyLabel
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleParkingFootprint
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
            name = "My car",
            brand = "Seat",
            model = "León",
            sizeCategory = VehicleSize.MEDIUM_SUV,
            vehicleType = VehicleType.CAR,
            carbodyType = CarbodyType.HATCHBACK_MEDIUM,
            fuelType = FuelType.GASOLINE,
            isActive = true,
        ),
        Vehicle(
            id = "mock_vehicle_002",
            userId = "mock_user_001",
            name = "Second car",
            brand = "Toyota",
            model = "Corolla",
            sizeCategory = VehicleSize.MEDIUM_SUV,
            vehicleType = VehicleType.CAR,
            bluetoothDeviceId = "AA:BB:CC:DD:EE:FF",
            carbodyType = CarbodyType.SEDAN,
            fuelType = FuelType.HYBRID,
            energyLabel = EnergyLabel.ECO,
            isActive = false,
        ),
        Vehicle(
            id = "mock_vehicle_003",
            userId = "mock_user_001",
            name = "My bike",
            brand = "Honda",
            model = "CBR 600",
            sizeCategory = VehicleSize.MOTORCYCLE,
            vehicleType = VehicleType.MOTORCYCLE,
            fuelType = FuelType.GASOLINE,
            isActive = false,
        ),
        Vehicle(
            id = "mock_vehicle_004",
            userId = "mock_user_001",
            name = "Work van",
            brand = "Ford",
            model = "Transit",
            sizeCategory = VehicleSize.VAN_HIGH,
            vehicleType = VehicleType.CAR,
            bluetoothDeviceId = "11:22:33:44:55:66",
            carbodyType = CarbodyType.VAN_COMMERCIAL,
            fuelType = FuelType.DIESEL,
            consumptionPer100 = 8.5f,
            isActive = false,
        ),
    )

    private val _vehiclesFlow = MutableStateFlow(mockVehicles)

    /** The scenario list: empty for fresh accounts; the ACTIVE vehicle gains a paired-BT
     *  identity when the Dev Catalog flips that lever. [UX-PARKED-STATE-001] */
    private fun scenarioList(
        session: MockScenario.Session,
        activeBt: Boolean,
        sharedCarJoined: Boolean,
    ): List<Vehicle> =
        when {
            session != MockScenario.Session.LoggedInWithVehicles -> emptyList()
            else -> mockVehicles.map { v ->
                var out = v
                if (activeBt && v.isActive) out = out.copy(bluetoothDeviceId = ACTIVE_BT_MAC)
                // [VEH-SHARED-001] La fila VINCULADA: el coche activo pasa a ser de otra persona,
                // que es lo que hace que el garaje lo lea como «te has unido a este coche» y que el
                // límite free deje de contarlo.
                if (sharedCarJoined && v.isActive) {
                    out = out.copy(sharedCarId = v.id, ownerUid = "mock_user_002")
                }
                out
            }
        }

    private fun currentList(): List<Vehicle> =
        if (scenario != null) {
            scenarioList(
                scenario.session.value,
                scenario.activeVehicleBluetooth.value,
                scenario.sharedCarJoined.value,
            )
        } else {
            mockVehicles
        }

    override fun observeVehicles(): Flow<List<Vehicle>> =
        if (scenario != null) {
            combine(
                scenario.session,
                scenario.activeVehicleBluetooth,
                scenario.sharedCarJoined,
            ) { s, bt, joined -> scenarioList(s, bt, joined) }
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

    // Dev Catalog: a mock car has no parking history to lose and is never parked, so deleting it is
    // always allowed. [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001]
    override suspend fun getParkingFootprint(vehicleId: String): VehicleParkingFootprint =
        VehicleParkingFootprint(endedParkings = 0, hasActiveParking = false)

    override suspend fun setActiveVehicle(id: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateBluetoothDevice(vehicleId: String, deviceAddress: String?): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun hasVehicles(userId: String): Boolean = currentList().isNotEmpty()

    override suspend fun pushPendingVehicles(): Result<Unit> = Result.success(Unit)

    companion object {
        /** MAC the Dev Catalog "BT on the active vehicle" lever pins to the active car. */
        const val ACTIVE_BT_MAC = "77:88:99:AA:BB:CC"

        /** The one vehicle with `isActive = true` above. Shared so the fake BT scanner can report
         *  THIS car as the connected one under the same lever, instead of guessing.
         *  [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001] */
        const val ACTIVE_VEHICLE_ID = "mock_vehicle_001"
    }
}
