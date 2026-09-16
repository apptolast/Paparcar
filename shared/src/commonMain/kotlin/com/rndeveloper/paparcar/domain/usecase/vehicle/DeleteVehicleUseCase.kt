package com.rndeveloper.paparcar.domain.usecase.vehicle

import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.SharedCarRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository

/**
 * Borrar un coche, sabiendo si estaba compartido. [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001]
 *
 * `VehicleRepository.deleteVehicle` no puede hacerlo por sí solo y no es un descuido: el repositorio
 * de coche compartido ya depende del de vehículos, así que la dependencia inversa sería un ciclo en
 * Koin. El invariante —**una fila que se borra no deja un grupo detrás**— vive aquí, en un sitio,
 * en vez de repetido en cada pantalla que tenga un botón de borrar.
 *
 * Qué significa borrar depende de QUÉ fila es:
 *
 * | Fila | Qué pasa con el grupo |
 * |---|---|
 * | Coche **tuyo** compartido | se disuelve para todos (`stopSharing`), que es lo mismo que hace borrar la cuenta |
 * | Coche **prestado** | te sales tú (`leave`): sesiones, nombre en la cabecera y tokens |
 * | Coche normal | no hay grupo que tocar |
 *
 * ⛔ **El aparcamiento activo se comprueba ANTES de tocar el grupo.** `deleteVehicle` se niega a
 * borrar un coche aparcado, y hacerlo al revés disolvería un grupo de cuatro personas para después
 * fallar y dejar el coche donde estaba. Queda una carrera mínima —que alguien aparque entre la
 * comprobación y el borrado—, y ahí gana la negativa del repositorio: el grupo se habrá cerrado,
 * pero eso ya lo había pedido el usuario al pulsar borrar.
 */
class DeleteVehicleUseCase(
    private val vehicleRepository: VehicleRepository,
    private val sharedCarRepository: SharedCarRepository,
    private val appPreferences: AppPreferences,
) {

    suspend operator fun invoke(vehicle: Vehicle): Result<Unit> = runCatching {
        if (vehicleRepository.getParkingFootprint(vehicle.id).hasActiveParking) {
            throw PaparcarError.Vehicle.DeleteBlockedByActiveParking
        }

        vehicle.sharedCarId?.let { carId ->
            if (vehicle.isLinked) {
                sharedCarRepository.leave(carId).getOrThrow()
            } else {
                sharedCarRepository.stopSharing(carId).getOrThrow()
            }
        }

        vehicleRepository.deleteVehicle(vehicle.id).getOrThrow()
        // Si este coche era justo el que esperaba decisión, la decisión ya está tomada.
        appPreferences.clearDissolvedSharedCar(vehicle.id)
    }
}
