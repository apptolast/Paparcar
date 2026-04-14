package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVehicleRepository(
    defaultVehicle: Vehicle? = null,
) : VehicleRepository {

    private val _defaultVehicle = MutableStateFlow(defaultVehicle)

    override fun observeVehicles(): Flow<List<Vehicle>> =
        MutableStateFlow(listOfNotNull(_defaultVehicle.value))

    override fun observeDefaultVehicle(): Flow<Vehicle?> = _defaultVehicle

    override suspend fun saveVehicle(vehicle: Vehicle) {
        _defaultVehicle.value = vehicle
    }

    override suspend fun deleteVehicle(id: String) {
        if (_defaultVehicle.value?.id == id) _defaultVehicle.value = null
    }

    override suspend fun setDefaultVehicle(id: String) {}

    override suspend fun updateBluetoothDevice(vehicleId: String, deviceAddress: String?) {}
}
