package com.rndeveloper.paparcar.domain.usecase.sharedcar

import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * Si el grupo tiene que enterarse de que me estoy llevando el coche.
 * [SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001]
 *
 * Un veredicto: su resultado cambia lo que ve el resto del grupo —el coche deja de estar donde
 * decía y su valla compartida se levanta— y se puede citar tal cual en un diagnóstico.
 *
 * ⛔ **Aquí solo se decide si la noticia procede, no si el coche se mueve.** Que el coche se mueva
 * lo prueba quien llama: el grabador de viaje de la línea Bluetooth, en el instante en que mide
 * conducción de verdad. La doctrina entera está en ese reparto — *el evento NOMINA, solo el
 * movimiento MEDIDO confirma*: el connect de Bluetooth dice **en qué coche** estoy (es su MAC, no
 * la de un autobús), y la conducción medida dice **que ese coche va a algún sitio**. Ni una sola de
 * las dos basta: sentarse en el coche a oír la radio es un connect sin viaje —el caso que
 * `BtTripShutoff` ya conoce como «null trip»— y conducir sin connect no dice qué coche conduces.
 *
 * Tres respuestas, y la del medio es la que evita contar la misma salida dos veces.
 */
class EvaluateSharedCarTakeoverUseCase {

    operator fun invoke(
        car: SharedCar,
        myUid: String?,
        myActiveSessionForCar: UserParking?,
    ): SharedCarTakeover {
        val state = car.state
            ?: return SharedCarTakeover.Silent("el grupo no sabe de ningún aparcamiento de este coche")

        if (!state.isParked) {
            return SharedCarTakeover.Silent("el grupo ya lo da por en marcha")
        }

        // ⛔ Mi propio aparcamiento, todavía vivo: me vuelvo a subir a MI coche. Esa salida ya la
        // cuenta el espejo cuando mi sesión se cierra, y esa vía es mejor que esta — la cierra la
        // detección, con su medida. Anunciarla aquí sería la misma noticia dos veces, y la primera
        // llegaría antes de que mi propia sesión lo supiera.
        if (state.authorUid == myUid && myActiveSessionForCar?.id == state.sessionId) {
            return SharedCarTakeover.Silent("es mi aparcamiento y sigue siendo mío")
        }

        return SharedCarTakeover.Announce(endingSessionId = state.sessionId)
    }
}

/** Qué hacer con el espejo cuando me pongo a conducir un coche del grupo. */
sealed interface SharedCarTakeover {

    /**
     * Contarlo: el coche ya no está donde el grupo cree.
     *
     * @property endingSessionId el aparcamiento que esto termina — viaja para que el servidor pueda
     *   comprobar que la salida que anuncia es la de ESE aparcamiento y no un documento reescrito.
     */
    data class Announce(val endingSessionId: String) : SharedCarTakeover

    /** No hay noticia. [reason] va al log de diagnóstico, no a ninguna pantalla. */
    data class Silent(val reason: String) : SharedCarTakeover
}
