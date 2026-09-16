@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.sharedcar

import com.rndeveloper.paparcar.data.datasource.local.room.SharedCarDao
import com.rndeveloper.paparcar.data.datasource.local.room.UserParkingDao
import com.rndeveloper.paparcar.data.datasource.local.room.UserProfileDao
import com.rndeveloper.paparcar.data.datasource.remote.RemoteSharedCarDataSource
import com.rndeveloper.paparcar.data.mapper.toDomain
import com.rndeveloper.paparcar.domain.usecase.sharedcar.EvaluateSharedCarTakeoverUseCase
import com.rndeveloper.paparcar.domain.usecase.sharedcar.SharedCarTakeover
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * Le dice al grupo que me llevo el coche, cuando ya está PROBADO que me lo llevo.
 * [SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001]
 *
 * ⛔ **No decide nada**: el veredicto es de [EvaluateSharedCarTakeoverUseCase], puro y testeado, y
 * la prueba de que el coche se mueve la trae el llamante — el grabador de viaje de la línea
 * Bluetooth, en el momento en que mide conducción real. Aquí solo hay I/O: leer la caché del
 * espejo, preguntar y escribir.
 *
 * ⛔ **Solo el carril Bluetooth llama aquí, y es deliberado.** El connect ata el viaje a la MAC de
 * ESE coche; el Coordinator no puede hacer esa atadura sin adivinar —salir andando de la plaza y
 * subirse a un autobús produciría conducción medida igual— y adivinarlo pondría en el espejo que el
 * coche se ha movido cuando sigue aparcado, dejando además a todo el grupo sin la valla del sitio
 * real. Mejor no enterarse que contar algo falso.
 *
 * ⛔ Y como todo lo que escribe el espejo, se traga sus fallos: la conducción es del usuario, no del
 * grupo, y una cortesía no puede romper un viaje.
 */
class SharedCarTakeoverAnnouncer(
    private val sharedCarDao: SharedCarDao,
    private val parkingDao: UserParkingDao,
    private val profileDao: UserProfileDao,
    private val remote: RemoteSharedCarDataSource,
    private val evaluate: EvaluateSharedCarTakeoverUseCase = EvaluateSharedCarTakeoverUseCase(),
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /**
     * @param vehicleId el coche cuyo viaje acaba de medirse. Si no es de ningún grupo, no pasa nada
     *   — que es el caso de todos los coches de casi todo el mundo.
     * @param myUid quién conduce.
     */
    suspend fun onDrivingProven(vehicleId: String, myUid: String?) {
        val entity = runCatching { sharedCarDao.getById(vehicleId) }.getOrNull() ?: return
        val car = entity.toDomain()
        val mine = runCatching { parkingDao.getActiveByVehicle(vehicleId) }.getOrNull()?.toDomain()

        when (val verdict = evaluate(car = car, myUid = myUid, myActiveSessionForCar = mine)) {
            is SharedCarTakeover.Silent -> {
                PaparcarLogger.d(TAG) { "· no se anuncia la salida: ${verdict.reason}" }
            }

            is SharedCarTakeover.Announce -> {
                val name = myUid?.let { runCatching { profileDao.getProfile(it)?.displayName }.getOrNull() }
                runCatching {
                    remote.setStateTakenBy(
                        carId = car.carId,
                        uid = myUid.orEmpty(),
                        name = name,
                        updatedAtMs = clock(),
                    )
                }.onSuccess {
                    PaparcarLogger.i(
                        TAG,
                        "▶ el grupo sabe que me llevo ${car.carId.take(8)} (termina ${verdict.endingSessionId.take(8)})",
                    )
                }.onFailure {
                    PaparcarLogger.w(TAG, "✗ no se pudo avisar al grupo de la salida — el viaje sigue", it)
                }
            }
        }
    }

    private companion object {
        const val TAG = "SharedCarTakeover"
    }
}
