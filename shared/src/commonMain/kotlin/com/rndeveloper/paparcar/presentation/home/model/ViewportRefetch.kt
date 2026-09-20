package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.util.haversineMeters

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
     * Is this point somewhere the layer should even look?
     *
     * ⛔ **(0, 0) is not a place**, it is the Gulf of Guinea — the coordinate a map reports before
     * it has a camera. Asking there was measured on device (`0 stations around 0.0,0.0`), and it is
     * the same trap `SPOT-A-REPORT-WITHOUT-A-PLACE-MUST-NOT-HAPPEN-001` closed for reports.
     *
     * Y el carril deja de SEGUIR a la cámara más allá de [MAX_FROM_USER_METERS]: cada asentamiento
     * de un arrastre cuesta una petición a un servicio público, y arrastrar el mapa media España
     * son cientos. Más allá el carril enseña NADA en vez de las gasolineras de tu ciudad sobre un
     * mapa ajeno — que es la mentira que este ticket cerró.
     *
     * ⛔ **Pero un ARRASTRE no es lo mismo que PEDIRLO** [NEARBY-A-DELIBERATE-LOOK-IS-NOT-A-PAN-001].
     * El tope existe para el seguimiento automático, no para la pregunta explícita: quien busca
     * «Cádiz» y abre el carril de parkings ha pedido una respuesta sobre Cádiz, y contestarle
     * «demasiado lejos» es negarle lo único que había pedido. Con [deliberate] el tope no aplica —
     * es UNA petición, la que el usuario acaba de pedir, no un reguero de ellas.
     */
    fun isWorthAsking(
        lat: Double,
        lon: Double,
        userLat: Double?,
        userLon: Double?,
        /** El usuario ha pedido ESTE sitio (abrir el carril), en vez de arrastrar el mapa hasta él. */
        deliberate: Boolean = false,
    ): Boolean {
        if (lat == 0.0 && lon == 0.0) return false
        if (deliberate) return true
        if (userLat == null || userLon == null) return true
        return haversineMeters(userLat, userLon, lat, lon) <= MAX_FROM_USER_METERS
    }

    private const val REFETCH_AT_FRACTION = 0.5

    /**
     * Hasta dónde SIGUE el carril a una cámara que el usuario arrastra.
     * [NEARBY-A-DELIBERATE-LOOK-IS-NOT-A-PAN-001]
     *
     * 150 km — el destino de un viaje de hoy, hora y media de coche. Estaba en **20 km**, que es la
     * provincia y no el viaje: salir de casa hacia otra ciudad y querer mirar allí los parkings
     * antes de llegar —el caso exacto de la issue #44— caía fuera por diez veces.
     *
     * Lo que este número protege NO es la cuota: es el reguero. Cada asentamiento de la cámara
     * pasada media radio dispara una petición, así que arrastrar el mapa 600 km son cientos de
     * ellas. Lo que lo hace asumible a 150 km es que la respuesta se CACHEA por celda (la cobertura
     * OSM en Room), así que una región preguntada una vez no se vuelve a preguntar.
     *
     * Y por encima de todo esto manda `deliberate`: pedir un sitio no tiene tope, porque es UNA
     * pregunta. El tope es para el arrastre, que es el que se multiplica.
     */
    const val MAX_FROM_USER_METERS = 150_000.0

    // ⛔ No hay aquí un `maxFromUserKm`. Existió para que la frase del carril no divergiera de esta
    // constante, y hacía media pregunta: derivaba la CIFRA y dejaba el «km» escrito en los nueve
    // locales, o sea seguía diciendo kilómetros a quien había pedido millas. Las dos mitades las
    // resuelve ahora `radiusString(MAX_FROM_USER_METERS)` en el sitio donde se dice.
    // [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001] [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
}
