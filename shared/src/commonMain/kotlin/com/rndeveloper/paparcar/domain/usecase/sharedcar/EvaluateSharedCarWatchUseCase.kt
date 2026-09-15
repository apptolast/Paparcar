package com.rndeveloper.paparcar.domain.usecase.sharedcar

import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * Qué debe vigilar ESTE móvil de un coche que conducen varios.
 * [DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001]
 *
 * Un veredicto y no un predicado: su resultado decide qué valla existe en el sistema operativo y si
 * una sesión local sigue siendo verdad, y las dos cosas se citan en un diagnóstico.
 *
 * La vigilancia de un coche compartido tiene una sola fuente: el espejo del grupo dice dónde está el
 * coche AHORA. Este caso de uso traduce eso a lo que le toca hacer a un móvil concreto, que depende
 * de quién aparcó:
 *
 *  - **Lo aparqué yo** → la valla de mi propia sesión ya lo vigila; una compartida encima sería una
 *    segunda valla sobre el mismo punto, con el mismo coche y dos ids. Se quita.
 *  - **Lo aparcó otro** → registro la valla del grupo en su sitio. Es de UBICACIÓN: no arma nada.
 *  - **El grupo dice que no está aparcado** → no hay nada que vigilar.
 */
class EvaluateSharedCarWatchUseCase {

    operator fun invoke(
        car: SharedCar,
        myUid: String?,
        myActiveSessionForCar: UserParking?,
    ): SharedCarWatch {
        val state = car.state
        val fenceId = com.rndeveloper.paparcar.domain.detection.fence.SharedCarFence.idFor(car.carId)

        val supersededSessionId = supersededSessionId(state, myUid, myActiveSessionForCar)

        return when {
            state == null || !state.isParked -> SharedCarWatch(
                fenceId = fenceId,
                register = null,
                supersededSessionId = supersededSessionId,
            )

            // Mío: la valla de mi sesión hace el trabajo. Dos vallas sobre el mismo coche serían dos
            // EXIT por cada salida, y el segundo sin sesión detrás.
            state.authorUid == myUid && myActiveSessionForCar?.id == state.sessionId -> SharedCarWatch(
                fenceId = fenceId,
                register = null,
                supersededSessionId = null,
            )

            else -> SharedCarWatch(
                fenceId = fenceId,
                register = SharedFencePoint(
                    sessionId = state.sessionId,
                    latitude = state.location.latitude,
                    longitude = state.location.longitude,
                    accuracy = state.location.accuracy,
                ),
                supersededSessionId = supersededSessionId,
            )
        }
    }

    /**
     * Mi sesión para este coche, cuando el grupo demuestra que ya no es verdad. Null = se queda.
     *
     * ⛔ Tres condiciones, y las tres son freno. Cerrar una sesión propia por error borra un
     * aparcamiento real del historial de alguien, así que solo cede ante un hecho **ajeno, distinto
     * y más nuevo**:
     *  1. **Distinta** — el espejo nombra otra sesión, no la mía reflejada.
     *  2. **De otra persona** — si el autor soy yo desde otro móvil, sigue siendo mi aparcamiento.
     *  3. **Más nueva** — sin esto, una sincronización que llega tarde (yo aparco sin cobertura, el
     *     espejo aún cuenta lo de ayer) me borraría el aparcamiento que acabo de hacer. El espejo no
     *     es más autoridad que mi propio móvil: solo lo es cuando cuenta algo POSTERIOR.
     *
     * Y «no aparcado» NUNCA supersede: que el grupo no sepa de un aparcamiento no es prueba de que
     * no exista — es exactamente el caso del móvil sin cobertura.
     */
    private fun supersededSessionId(
        state: com.rndeveloper.paparcar.domain.model.SharedCarState?,
        myUid: String?,
        mySession: UserParking?,
    ): String? {
        val mine = mySession ?: return null
        val group = state?.takeIf { it.isParked } ?: return null
        val isSomebodyElses = group.authorUid != myUid && group.authorUid.isNotEmpty()
        val isAnotherParking = group.sessionId != mine.id
        val isNewer = group.parkedAtMs > mine.location.timestamp
        return mine.id.takeIf { isSomebodyElses && isAnotherParking && isNewer }
    }
}

/**
 * Lo que este móvil tiene que hacer con un coche del grupo.
 *
 * @property fenceId la valla compartida de este coche — siempre la misma, exista o no.
 * @property register dónde plantarla, o null para no tener ninguna (y quitar la que hubiera).
 * @property supersededSessionId mi sesión local que el grupo acaba de dejar obsoleta, si la hay.
 *   Se cierra SIN publicar plaza: el coche no se ha ido conmigo, se lo ha llevado otro.
 */
data class SharedCarWatch(
    val fenceId: String,
    val register: SharedFencePoint?,
    val supersededSessionId: String?,
)

/** El punto que vigila la valla del grupo — el aparcamiento vivo del coche, sea de quien sea. */
data class SharedFencePoint(
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
)
