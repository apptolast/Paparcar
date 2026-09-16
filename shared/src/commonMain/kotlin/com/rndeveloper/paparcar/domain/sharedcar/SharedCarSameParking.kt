package com.rndeveloper.paparcar.domain.sharedcar

import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * Un aparcamiento del coche compartido, tal y como lo vio UN móvil.
 * [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001]
 *
 * @property atMs el instante en que el coche SE PARÓ (el ancla), nunca el instante en que este
 *   móvil lo confirmó — ver [isSameParking].
 */
data class ParkingMark(
    val sessionId: String,
    val atMs: Long,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Si dos móviles están contando el MISMO aparcamiento.
 * [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001]
 *
 * Un coche compartido lo pueden llevar dos personas a la vez, y las dos llevan Paparcar. Los dos
 * móviles detectan la misma parada, los dos escriben el espejo, y el grupo acaba con **dos
 * aparcamientos donde hubo uno**: el doble de kilómetros, el doble de tiempo buscando, el doble de
 * plazas liberadas. No es un aviso duplicado, es una estadística falsa.
 *
 * Predicado y no caso de uso porque lo preguntan DOS veredictos distintos: si espejar lo que acabo
 * de aparcar, y si enseñar la tarjeta de un aviso que habla de mi propio viaje.
 *
 * ⛔ Se comparan **anclas, no confirmaciones**. Dos móviles del mismo coche confirman con minutos de
 * diferencia —uno por Bluetooth en segundos, el otro por pasos y egress— pero los dos fijan el ancla
 * al final de la conducción, que es el mismo instante físico. Comparar cuándo lo supo cada móvil
 * haría que el carril lento no se dedupara nunca, que es justo el caso que más duele.
 */
fun isSameParking(mine: ParkingMark, theirs: ParkingMark): Boolean {
    if (mine.sessionId == theirs.sessionId) return false // mi propio reflejo, no otro móvil
    if (kotlin.math.abs(mine.atMs - theirs.atMs) > SAME_PARKING_WINDOW_MS) return false
    val metres = haversineMeters(mine.latitude, mine.longitude, theirs.latitude, theirs.longitude)
    return metres <= SAME_PARKING_RADIUS_METERS
}

/**
 * Tres minutos sobre el ANCLA. La ventana la manda la diferencia entre dos anclas del mismo
 * frenazo —error de GPS y el congelado de cada móvil al final de la conducción—, no la diferencia
 * entre dos confirmaciones. Y lo que hay al otro lado es tranquilizador: el mismo coche no se puede
 * aparcar dos veces de verdad en tres minutos, porque entre un aparcamiento y el siguiente hay que
 * arrancar, conducir y volver a parar.
 */
const val SAME_PARKING_WINDOW_MS: Long = 3 * 60 * 1000L

/**
 * 150 m. El freno de verdad es la ventana; el radio está para que un fix malo de uno de los dos
 * móviles no rompa la coincidencia, no para distinguir dos aparcamientos — a esa distancia y dentro
 * de la ventana no hay dos aparcamientos que distinguir.
 */
const val SAME_PARKING_RADIUS_METERS: Double = 150.0
