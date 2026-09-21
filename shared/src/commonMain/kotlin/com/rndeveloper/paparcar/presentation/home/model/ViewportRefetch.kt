package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.util.haversineMeters
import com.rndeveloper.paparcar.domain.parkings.ParkingZoneGrid

/**
 * When does a map layer owe the settled camera a fresh ask? [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
 *
 * Pure on purpose, and separate from any one layer: this is the reusable half of
 * "the layer shows what you look at" — the fuel layer is its first consumer, and the spots and
 * parkings layers are expected to be its next two (`spots-follow-the-camera-001.md`). The half a
 * layer keeps for itself is WHAT to fetch and where to store it.
 *
 * The rule: refetch when there is no anchor yet, or when the camera settled more than HALF the
 * query radius away from the last ask. Half, because a full radius would let the visible edge run
 * completely dry before anyone asks again, and much less would re-ask on every nervous pan.
 */
object ViewportRefetch {

    fun shouldRefetch(
        anchorLat: Double?,
        anchorLon: Double?,
        lat: Double,
        lon: Double,
        radiusMeters: Int,
    ): Boolean {
        if (anchorLat == null || anchorLon == null) return true
        return haversineMeters(anchorLat, anchorLon, lat, lon) > radiusMeters * REFETCH_AT_FRACTION
    }

    /**
     * ¿Es esto un SITIO? La única razón por la que un carril se niega a preguntar.
     *
     * ⛔ **(0, 0) no es un sitio**, es el golfo de Guinea — la coordenada que un mapa reporta
     * mientras todavía no tiene cámara. Preguntar ahí está medido en device
     * (`0 stations around 0.0,0.0`), y es la misma trampa que
     * `SPOT-A-REPORT-WITHOUT-A-PLACE-MUST-NOT-HAPPEN-001` cerró para los avisos.
     *
     * ## ⛔ Aquí había también un TOPE DE DISTANCIA al usuario, y se ha retirado
     * [NEARBY-A-DELIBERATE-LOOK-IS-NOT-A-PAN-001]
     *
     * El carril dejaba de seguir a la cámara más allá de 150 km (20 km antes de este ticket), y el
     * argumento escrito era: *«cada asentamiento de un arrastre cuesta una petición, y arrastrar el
     * mapa media España son cientos»*.
     *
     * **Ese argumento no se sostiene, y hay tres guardas que lo impiden — ninguno de ellos era
     * éste:**
     *
     *  1. **El asentamiento va con debounce.** `scheduleNearbyRefresh` cancela el trabajo anterior y
     *     vuelve a esperar `NEARBY_CAMERA_SETTLE_MS` en CADA movimiento de cámara. Un arrastre
     *     continuo, dure lo que dure y cruce lo que cruce, dispara **una** petición: la del final.
     *     Para gastar cientos habría que hacer cientos de pausas deliberadas.
     *  2. **Moverse poco no pregunta nada.** Las gasolineras exigen media radio ([shouldRefetch]);
     *     los parkings, cambiar de zona entera (`ParkingZoneGrid.sameZone`). El caso «paneo 5 m»
     *     que motivó esta revisión no pedía datos ni antes ni ahora.
     *  3. **Una región preguntada una vez no se vuelve a preguntar**: la respuesta se cachea por
     *     celda/zona en Room.
     *
     * Y lo que el tope SÍ costaba era real y visible: con el carril abierto no se podían mirar los
     * parkings de otra ciudad arrastrando el mapa, que es literalmente la issue #44. El tope
     * protegía de una tormenta que el debounce ya impedía, y cobraba la función que el usuario
     * pedía.
     *
     * Con él se va también el estado `tooFar` de los dos carriles y su frase, que no describían el
     * mundo sino nuestro propio límite.
     */
    fun isAPlace(lat: Double, lon: Double): Boolean = !(lat == 0.0 && lon == 0.0)

    /**
     * **¿La respuesta que este carril tiene en la mano es sobre DONDE SE ESTÁ MIRANDO?**
     * [HOME-THE-CAMERA-FRAMES-WHAT-THE-LIST-SHOWS-001]
     *
     * Es la misma pregunta que decide si hay que volver a preguntar, leída al revés — y por eso vive
     * aquí y no en dos sitios. El que quiere refrescar pregunta «¿se ha movido?»; el que quiere
     * ENCUADRAR pregunta «¿esto va de aquí?». Dos formulaciones de un invariante que, escritas por
     * separado, acaban discrepando: fue exactamente lo que pasó — la cámara volaba a Italia estando
     * en Madrid, porque el encuadre usaba una lista que ya nadie consideraba válida.
     *
     * ⛔ Sin ancla **no** va de aquí: una lista sin ancla es una lista que nadie ha situado.
     */
    fun answerCoversCamera(
        anchorLat: Double?,
        anchorLon: Double?,
        cameraLat: Double?,
        cameraLon: Double?,
        radiusMeters: Int,
        /** Los parkings se preguntan por ZONA, no por distancia. [PARKINGS-A-ZONE-IS-ASKED-ONCE-AND-PANNED-FOR-FREE-001] */
        byZone: Boolean = false,
    ): Boolean {
        if (anchorLat == null || anchorLon == null) return false
        if (cameraLat == null || cameraLon == null) return true // nadie ha posado la cámara: no hay conflicto
        return if (byZone) {
            ParkingZoneGrid.sameZone(anchorLat, anchorLon, cameraLat, cameraLon)
        } else {
            !shouldRefetch(anchorLat, anchorLon, cameraLat, cameraLon, radiusMeters)
        }
    }

    private const val REFETCH_AT_FRACTION = 0.5
}
