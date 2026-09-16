@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.sharedcar

import com.rndeveloper.paparcar.data.datasource.local.room.UserParkingDao
import com.rndeveloper.paparcar.data.datasource.local.room.UserProfileDao
import com.rndeveloper.paparcar.data.datasource.local.room.VehicleDao
import com.rndeveloper.paparcar.data.datasource.remote.RemoteSharedCarDataSource
import com.rndeveloper.paparcar.data.mapper.toAddressDto
import com.rndeveloper.paparcar.data.mapper.toDomain
import com.rndeveloper.paparcar.data.mapper.toDto
import com.rndeveloper.paparcar.data.mapper.toPlaceInfoDto
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.sharedcar.ParkingMark
import com.rndeveloper.paparcar.domain.sharedcar.SharedCarMirror
import com.rndeveloper.paparcar.domain.sharedcar.isSameParking
import com.rndeveloper.paparcar.domain.sharedcar.sharedCarMirrorPayload
import com.rndeveloper.paparcar.domain.sharedcar.sharedCarStateOf
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * Writes the group's copy of a parking, hanging off the one door every session push goes through.
 * [VEH-SHARED-001]
 *
 * WHAT travels is not decided here — it is decided once, purely, by `sharedCarMirrorPayload()`.
 * This class only answers "is there a group to tell, and who is telling them", and does the I/O.
 *
 * ⛔ Every method is failure-swallowing on purpose: the personal parking is the record that must
 * not be lost, and a mirror that could fail it would trade the user's own data for a courtesy to
 * somebody else. What a failed mirror costs is one stale reading for the group, repaired by the
 * next push of the same session.
 */
class SharedCarMirrorWriter(
    private val remote: RemoteSharedCarDataSource,
    private val vehicleDao: VehicleDao,
    private val parkingDao: UserParkingDao,
    private val profileDao: UserProfileDao,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : SharedCarMirror {

    override suspend fun onSessionSaved(session: UserParking) {
        val carId = sharedCarIdOf(session.userId, session.vehicleId) ?: return
        guard("onSessionSaved", session.id) {
            if (isEchoOfAParkingTheGroupAlreadyHas(carId, session)) {
                PaparcarLogger.d(TAG) {
                    "· ${session.id} es el mismo aparcamiento que el grupo ya tiene — no se espeja"
                }
                return@guard
            }
            val payload = sharedCarMirrorPayload(
                session = session,
                authorUid = session.userId,
                authorName = profileDao.getProfile(session.userId)?.displayName,
                nowMs = clock(),
                // The switch is read HERE, from the row this phone owns, and never assumed: the
                // payload refuses a default for it.
                // [PRIV-A-ROUTE-LEAVES-THE-PHONE-BY-ONE-DOOR-001]
                shareRoutes = sharesRoutes(session.userId, session.vehicleId),
            )
            remote.putSession(carId, payload.toDto())
            // The head of the mirror follows the session that was just written — including when it
            // arrives already closed (a replayed offline save), which is why the state is derived
            // from the payload rather than assumed to be "parked".
            remote.putState(carId, sharedCarStateOf(payload).toDto())
        }
    }

    override suspend fun onSessionClosed(sessionId: String, endedAtMs: Long) {
        val session = parkingDao.getById(sessionId)?.toDomain() ?: return
        val carId = sharedCarIdOf(session.userId, session.vehicleId) ?: return
        guard("onSessionClosed", sessionId) {
            val now = clock()
            remote.closeSession(carId, sessionId, endedAtMs, now)
            // ⛔ Only if this session is still the one the state is showing. Another member may
            // have parked the car since; marking THEIR parking as over because OUR older one
            // closed would tell the group the car is on the move when it is not.
            if (remote.getState(carId)?.sessionId == sessionId) {
                remote.setStateParked(carId, isParked = false, updatedAtMs = now)
            }
        }
    }

    override suspend fun onAddressResolved(
        sessionId: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ) {
        val session = parkingDao.getById(sessionId)?.toDomain() ?: return
        val carId = sharedCarIdOf(session.userId, session.vehicleId) ?: return
        guard("onAddressResolved", sessionId) {
            val now = clock()
            remote.updateSessionAddress(
                carId = carId,
                sessionId = sessionId,
                address = address?.toAddressDto(),
                placeInfo = placeInfo?.toPlaceInfoDto(),
                updatedAtMs = now,
            )
            val state = remote.getState(carId)
            if (state?.sessionId == sessionId) {
                remote.putState(
                    carId,
                    state.copy(
                        address = address?.toAddressDto(),
                        placeInfo = placeInfo?.toPlaceInfoDto(),
                        updatedAtMs = now,
                    ),
                )
            }
        }
    }

    /**
     * Si esto que acabo de aparcar ya está en el espejo, contado por el móvil de quien iba al lado.
     * [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001]
     *
     * ⛔ **Solo se calla el ESPEJO.** La sesión personal ya está escrita y no se toca: es la fuente
     * de verdad de quien la vivió, y su historial propio tiene que seguir contándola. Lo que no
     * puede haber es el mismo frenazo dos veces en las cifras del grupo.
     *
     * ⛔ Gana quien llega primero, y no se intenta adivinar quién conducía. Ningún dato de los que
     * tenemos distingue al conductor del copiloto —el Coordinator mide el viaje igual desde los dos
     * asientos—, así que inventar un criterio sería inventar un dato. Lo que sí se conserva es lo
     * único que importa para el grupo: un aparcamiento por frenazo, con sus kilómetros contados una
     * vez. La atribución del autor puede salir cambiada; las cifras, no.
     *
     * Solo se compara contra un aparcamiento VIVO, y solo si el mío también lo está: un guardado
     * reproducido desde el buffer offline llega ya cerrado y no está compitiendo con nadie.
     */
    private suspend fun isEchoOfAParkingTheGroupAlreadyHas(carId: String, session: UserParking): Boolean {
        if (!session.isActive) return false
        val state = remote.getState(carId)?.takeIf { it.isParked } ?: return false
        return isSameParking(
            mine = ParkingMark(
                sessionId = session.id,
                atMs = session.location.timestamp,
                latitude = session.location.latitude,
                longitude = session.location.longitude,
            ),
            theirs = ParkingMark(
                sessionId = state.sessionId,
                atMs = state.parkedAtMs,
                latitude = state.latitude,
                longitude = state.longitude,
            ),
        )
    }

    /** The group this parking belongs to, or null — which is every parking of every ordinary car. */
    private suspend fun sharedCarIdOf(userId: String, vehicleId: String?): String? {
        val id = vehicleId ?: return null
        return runCatching { vehicleDao.getById(id, userId)?.sharedCarId }.getOrNull()
    }

    /**
     * Whether this driver shares the drawn route of THIS car with the group.
     *
     * ⛔ Falls back to **false**, not to the field's own default of true: this is read on the way
     * out, and a row that could not be read is not a permission. Asymmetric failure applies to
     * privacy exactly as it applies to detection — the cheap mistake is a route the group does not
     * see, not one the author never agreed to send.
     * [PRIV-A-ROUTE-LEAVES-THE-PHONE-BY-ONE-DOOR-001]
     */
    private suspend fun sharesRoutes(userId: String, vehicleId: String?): Boolean {
        val id = vehicleId ?: return false
        return runCatching { vehicleDao.getById(id, userId)?.shareRoutesInGroup }.getOrNull() ?: false
    }

    private suspend inline fun guard(what: String, sessionId: String, block: () -> Unit) {
        try {
            block()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Throwable) {
            PaparcarLogger.w(TAG, "$what($sessionId) did not reach the group — personal save stands", e)
        }
    }

    private companion object {
        const val TAG = "SharedCarMirror"
    }
}
