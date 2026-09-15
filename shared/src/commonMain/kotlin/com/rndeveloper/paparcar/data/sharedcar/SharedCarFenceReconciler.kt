@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.sharedcar

import com.rndeveloper.paparcar.data.datasource.local.room.SharedCarDao
import com.rndeveloper.paparcar.data.datasource.local.room.UserParkingDao
import com.rndeveloper.paparcar.data.mapper.toDomain
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.usecase.sharedcar.EvaluateSharedCarWatchUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * Pone las vallas del grupo donde el espejo dice que está el coche.
 * [DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001]
 *
 * ⛔ **No decide nada**: el veredicto es de [EvaluateSharedCarWatchUseCase], puro y testeado. Aquí
 * solo hay I/O — leer la caché del espejo, registrar o quitar la valla, y cerrar la sesión que el
 * grupo dejó obsoleta.
 *
 * Vive donde ya se reconcilian vallas (el janitor) y no en un sitio nuevo: dos lugares registrando
 * vallas es exactamente lo que [FenceRegistrationPolicy] documenta como la forma de abrir ventanas
 * ciegas en el trigger que intentamos proteger.
 */
class SharedCarFenceReconciler(
    private val sharedCarDao: SharedCarDao,
    private val parkingDao: UserParkingDao,
    private val geofenceManager: GeofenceManager,
    private val config: ParkingDetectionConfig,
    private val evaluate: EvaluateSharedCarWatchUseCase = EvaluateSharedCarWatchUseCase(),
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /**
     * Una pasada sobre todos los coches del grupo de este usuario.
     *
     * @param myUid quién soy — decide si el coche lo aparqué yo (y entonces la valla es la de mi
     *   sesión) o lo aparcó otro.
     * @return cuántas vallas quedaron registradas, para la línea de log del llamante.
     */
    suspend fun reconcile(myUid: String?): Int {
        val cars = runCatching { sharedCarDao.getAll() }.getOrElse {
            // Igual que el barrido de huérfanas: una lectura FALLIDA no es «no hay coches». Sin
            // población no se toca ninguna valla. [DET-A-RELEASED-PIN-TAKES-ITS-FENCES-WITH-IT-001]
            PaparcarLogger.w(TAG, "✗ no se pudo leer la caché de coches compartidos — no se toca ninguna valla", it)
            return 0
        }
        if (cars.isEmpty()) return 0

        var registered = 0
        for (entity in cars) {
            val car = entity.toDomain()
            val mine = runCatching { parkingDao.getActiveByVehicle(car.carId) }.getOrNull()?.toDomain()
            val watch = evaluate(car = car, myUid = myUid, myActiveSessionForCar = mine)

            watch.supersededSessionId?.let { sessionId ->
                // El coche se lo ha llevado otra persona: mi sesión termina, pero NO es una salida
                // mía — no publica plaza, y el historial la conserva con su hora.
                PaparcarLogger.i(
                    TAG,
                    "↺ sesión ${sessionId.take(8)} superada por el grupo (coche ${car.carId.take(8)}) — cierro sin publicar",
                )
                runCatching { parkingDao.clearActiveByVehicle(car.carId, clock()) }
                    .onFailure { PaparcarLogger.w(TAG, "✗ no se pudo cerrar la sesión superada", it) }
            }

            val point = watch.register
            if (point == null) {
                runCatching { geofenceManager.removeGeofence(watch.fenceId) }
                continue
            }
            val radius = config.geofenceRadiusFor(
                // El espejo lleva el tamaño del coche; sin él, el mismo respaldo que usa el janitor.
                car.sizeCategory ?: VehicleSize.MEDIUM_SUV,
                point.accuracy,
            )
            geofenceManager.createGeofence(
                geofenceId = watch.fenceId,
                latitude = point.latitude,
                longitude = point.longitude,
                radiusMeters = radius,
            ).onSuccess {
                registered++
                PaparcarLogger.d(
                    TAG,
                    "✓ valla del grupo ${watch.fenceId.take(16)} en ${point.latitude},${point.longitude} r=${radius}m",
                )
            }.onFailure {
                PaparcarLogger.w(TAG, "✗ no se pudo registrar la valla del grupo ${watch.fenceId.take(16)}", it)
            }
        }
        return registered
    }

    private companion object {
        const val TAG = "SharedCarFence"
    }
}
