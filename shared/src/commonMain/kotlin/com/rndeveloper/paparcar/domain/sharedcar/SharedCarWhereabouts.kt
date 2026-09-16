package com.rndeveloper.paparcar.domain.sharedcar

import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * Dónde está el coche y quién lo dejó ahí.
 * [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]
 *
 * @param session el aparcamiento que manda, venga de tu móvil o del espejo del grupo.
 * @param parkedByName el nombre de quien lo dejó, **solo cuando no fuiste tú**. Null significa «lo
 *   dejaste tú» o «nadie ha dicho su nombre», y en los dos casos la línea no lo menciona: firmar
 *   tu propio aparcamiento es ruido.
 */
data class CarWhereabouts(
    val session: UserParking,
    val parkedByName: String?,
)

/**
 * La ÚNICA respuesta a «dónde está el coche».
 *
 * ⛔ Existía por duplicado y las dos copias se contradecían: la hero card leía tu sesión **local** y
 * la card «Dónde está el coche» leía el **espejo**. Si lo aparcaba otro, tu móvil no tenía esa
 * sesión local, así que arriba se leía tu aparcamiento viejo y abajo el de Marta — dos respuestas
 * distintas a la misma pregunta, a dos dedos. Ahora hay una sola, y el dato que justificaba la
 * segunda card (quién lo dejó) viaja dentro de ella.
 *
 * Manda **el más reciente**, no la fuente: el espejo gana cuando el grupo ha aparcado después que
 * tú, y tu sesión local gana cuando la última vez la aparcaste tú — que además es la copia rica,
 * con su provenance y su ruta.
 *
 * Para quien acaba de UNIRSE al grupo esto es lo que llena una card que hoy sale muda: no tiene
 * sesiones propias de ese coche, y sin el espejo no tendría nada que contar.
 */
fun carWhereabouts(
    ownLastSession: UserParking?,
    sharedCar: SharedCar?,
    currentUid: String?,
    vehicleId: String,
): CarWhereabouts? {
    val mirrored = sharedCar?.state
    if (mirrored == null) {
        return ownLastSession?.let { CarWhereabouts(it, parkedByName = null) }
    }
    // El espejo NO gana por ser del grupo: gana por ser posterior. Empate → la local, que es la rica.
    val ownAtMs = ownLastSession?.location?.timestamp
    if (ownAtMs != null && ownAtMs >= mirrored.parkedAtMs) {
        return CarWhereabouts(ownLastSession, parkedByName = null)
    }
    return CarWhereabouts(
        session = UserParking(
            id = mirrored.sessionId,
            userId = mirrored.authorUid,
            vehicleId = vehicleId,
            location = mirrored.location,
            isActive = mirrored.isParked,
            address = mirrored.address,
            placeInfo = mirrored.placeInfo,
        ),
        parkedByName = mirrored.authorName
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { mirrored.authorUid != currentUid },
    )
}
