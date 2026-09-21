package com.rndeveloper.paparcar.domain.spot

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SpotVotePolicy
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * Una plaza de la comunidad por la que el usuario **dio fe estando en ella**.
 * [SPOT-A-SPACE-THE-COMMUNITY-GAVE-YOU-IS-ONE-YOU-VOUCHED-FOR-001]
 *
 * Nace del voto «sigue libre», que **ya pasó el filtro de proximidad** de [SpotVotePolicy] — o sea,
 * la propia app ya decidió que el usuario estaba lo bastante cerca como para que su palabra contara
 * sobre esa plaza. Eso es lo que la convierte en evidencia y no en una suposición.
 */
data class VouchedSpot(
    val spotId: String,
    val latitude: Double,
    val longitude: Double,
    /** Cuándo deja de existir la plaza. Es el reloj de la PLAZA, no uno inventado para esto. */
    val expiresAtMs: Long,
)

/**
 * ¿Aparcaste en la plaza por la que acabas de dar fe?
 * [SPOT-A-SPACE-THE-COMMUNITY-GAVE-YOU-IS-ONE-YOU-VOUCHED-FOR-001]
 *
 * ### Por qué esta pregunta y no «¿aparcaste cerca de alguna plaza?»
 * Porque la segunda **no se puede contestar sin inventar umbrales**, y el ticket (#110) se abrió
 * con esa advertencia: mirar una plaza en la lista no es ir, y avisar de que vas tampoco —puede que
 * no llegaras, o que estuviera ocupada, que es justo para lo que existe el voto «ya no está»—.
 *
 * Ésta sí, porque **no inventa ni un número**:
 * - **Dónde**: [SpotVotePolicy.MAX_VOTE_DISTANCE_METERS], los 50 m que la app YA usa para decidir
 *   que alguien está en una plaza. No es un umbral nuevo: es la definición que el producto ya tiene
 *   de «estabas ahí», y es tan fuerte que con ella un solo voto retira una plaza para todos.
 * - **Cuándo**: la caducidad de la **propia plaza**. Más allá, la plaza no existe, así que no hay
 *   nada que te hayan dado.
 *
 * ⛔ **Fallo asimétrico, y hacia el lado bueno.** Esto sólo cuenta cuando el usuario VOTÓ, así que
 * subcontará siempre — habrá plazas que la comunidad le dio y que esta cifra no verá. Es el error
 * correcto: atribuirse una plaza que el conductor encontró solo convierte la cifra en propaganda, y
 * el silencio sólo la deja pequeña.
 *
 * @param vouch la última plaza por la que se dio fe, o null si no hay ninguna.
 */
fun communityGaveThisSpot(
    vouch: VouchedSpot?,
    parkedAt: GpsPoint,
): String? {
    if (vouch == null) return null
    // La plaza ya no existía cuando aparcaste: no te la dio nadie.
    if (parkedAt.timestamp > vouch.expiresAtMs) return null
    val meters = haversineMeters(
        vouch.latitude, vouch.longitude,
        parkedAt.latitude, parkedAt.longitude,
    )
    return vouch.spotId.takeIf { meters <= SpotVotePolicy.MAX_VOTE_DISTANCE_METERS }
}
