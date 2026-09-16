package com.rndeveloper.paparcar.data.datasource.remote

import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarInviteDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarMemberDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarSessionDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarStateDto
import kotlinx.coroutines.flow.Flow

/** Firestore side of a shared car: `sharedCars/{carId}` and `sharedCarInvites/{code}`.
 *  [VEH-SHARED-001] */
interface RemoteSharedCarDataSource {

    fun observeCar(carId: String): Flow<SharedCarDto?>
    fun observeState(carId: String): Flow<SharedCarStateDto?>
    fun observeMembers(carId: String): Flow<List<SharedCarMemberDto>>
    fun observeSessions(carId: String): Flow<List<SharedCarSessionDto>>

    suspend fun getCar(carId: String): SharedCarDto?
    suspend fun getMembers(carId: String): List<SharedCarMemberDto>

    suspend fun createCar(car: SharedCarDto, owner: SharedCarMemberDto)
    /** Removes the mirror, its state, its members and its sessions. The owner's own car and
     *  personal history are NOT touched — they never lived here. */
    suspend fun deleteCar(carId: String)

    suspend fun getState(carId: String): SharedCarStateDto?
    suspend fun putState(carId: String, state: SharedCarStateDto)
    /** Flips "still parked" without touching the position: the car keeps its last known spot. */
    suspend fun setStateParked(carId: String, isParked: Boolean, updatedAtMs: Long)

    /**
     * Otro miembro se lleva el coche: deja de estar aparcado y queda dicho QUIÉN lo conduce.
     * [SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001]
     *
     * La posición y la sesión no se tocan: la salida que esto anuncia es la de ESE aparcamiento, y
     * el servidor comprueba justamente eso antes de contarla.
     */
    suspend fun setStateTakenBy(carId: String, uid: String, name: String?, updatedAtMs: Long)
    suspend fun putSession(carId: String, session: SharedCarSessionDto)
    suspend fun closeSession(carId: String, sessionId: String, endedAtMs: Long, updatedAtMs: Long)
    suspend fun updateSessionAddress(
        carId: String,
        sessionId: String,
        address: AddressDto?,
        placeInfo: PlaceInfoDto?,
        updatedAtMs: Long,
    )

    /**
     * Takes this uid's NAME off the head of the mirror, wherever it is standing.
     *
     * `state/current` is not a session: it is the one document the group's card reads, and it
     * carries two names — quien lo dejó ahí ([SharedCarStateDto.authorName]) y quién se lo llevó
     * ([SharedCarStateDto.movedByName]). Borrar las sesiones y la ficha de miembro dejaba ese
     * documento intacto, así que el grupo seguía leyendo «aparcado por Nacho» de alguien que ya no
     * está. La posición NO se toca: dónde está el coche es del grupo, y borrarla dejaría al resto
     * sin saber dónde tienen su propio coche.
     *
     * ⛔ Como [deleteSessionsOf], tiene que correr ANTES de borrar la ficha de miembro: escribir en
     * `state` exige `isMember()`. [PRIV-LEAVING-A-GROUP-MUST-CLEAR-YOUR-NAME-FROM-ITS-HEAD-001]
     */
    suspend fun clearMemberFromState(carId: String, uid: String)

    /**
     * Every session this uid signed in this group, gone.
     *
     * ⛔ Has to run BEFORE the member doc is deleted: the reading rule is `isMember()`, so a
     * membership already torn down leaves the sessions unreachable — and unreadable is not
     * deleted. [PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001]
     */
    suspend fun deleteSessionsOf(carId: String, uid: String)

    suspend fun putMember(carId: String, member: SharedCarMemberDto)
    /** Publishes whether this member's routes are visible, so the others can be told why a line is
     *  missing instead of guessing. */
    suspend fun setMemberSharesRoutes(carId: String, uid: String, sharesRoutes: Boolean)

    /** The devices to tell when somebody parks this car. Being in the list IS the permission — the
     *  function asks nothing else. [SHARED-A-PARKING-TELLS-THE-GROUP-001] */
    suspend fun setMemberTokens(carId: String, uid: String, tokens: List<String>)
    suspend fun deleteMember(carId: String, uid: String)

    /**
     * Reemplaza ATÓMICAMENTE el código vivo del coche: borra el anterior (si lo hay), escribe el
     * nuevo y apunta el coche a él, todo en una sola transacción de Firestore.
     * [SEC-A-REPLACED-INVITE-STILL-OPENS-THE-DOOR-001]
     *
     * Antes eran tres escrituras sueltas (borrar el viejo, crear el nuevo, apuntar el puntero) con
     * el borrado tragado en silencio si fallaba: un hipo de red o una carrera entre dos taps dejaba
     * el código viejo vivo y VÁLIDO — el puntero avanzaba igual, y sin `list` sobre la colección no
     * había forma de volver a encontrarlo. Con la transacción, o pasan las tres cosas o no pasa
     * ninguna: nunca hay un instante con dos códigos utilizables para el mismo coche.
     */
    suspend fun replaceInvite(carId: String, invite: SharedCarInviteDto)
    suspend fun getInvite(code: String): SharedCarInviteDto?
    suspend fun claimInvite(code: String, uid: String)
    /** Best-effort: borra el código vivo del coche (si lo hay) al disolver el grupo entero. */
    suspend fun deleteInvitesFor(carId: String)
}
