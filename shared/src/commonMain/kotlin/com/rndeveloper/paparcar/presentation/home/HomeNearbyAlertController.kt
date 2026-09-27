@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.home

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.notification.NearbySpotAlerts
import com.rndeveloper.paparcar.domain.spot.nearbySpotsWorthTelling
import kotlin.time.Clock

/**
 * El carril de «una plaza acaba de quedar libre donde estás»: la memoria de sesión y el disparo.
 * [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
 *
 * Vive en Home, como los otros cuatro controllers, porque es el único sitio donde ya están las dos
 * entradas que hay que cruzar: **el fix del GPS** (tu cuerpo, no la cámara) y **las plazas de la
 * suscripción viva**. Pedirlas otra vez a nivel de app serían dos suscripciones y dos verdades sobre
 * dónde estás. Lo que decide es [nearbySpotsWorthTelling], puro y testeable sin Android; esto sólo
 * recuerda y entrega.
 *
 * ## Por qué la memoria es de sesión y no se persiste
 *
 * El carril existe exactamente mientras el mapa está delante, así que —al contrario que el de zona—
 * no hay ningún worker que herede nada: `ZoneWatchRepository` existe porque un worker arranca de cero
 * cada quince minutos, y aquí eso no pasa nunca.
 *
 * ⛔ **Y no se borra al irse el mapa a segundo plano**, a propósito: volver a Home no es una sesión
 * nueva, y limpiar aquí haría que la vuelta re-anunciara plazas que el usuario ya ha visto. El
 * conjunto crece durante la sesión, acotado por las plazas que han pasado a 500 m de ti — decenas en
 * un día de conducir, no algo que haya que recortar.
 *
 * ⚠️ `suspend` por una sola razón: el uid se pregunta a la sesión de auth, que es suspend. Sin uid no
 * se puede aplicar el veto que más importa —*tu propia plaza no es tu aviso*— así que se pregunta en
 * cada mirada en vez de cachearlo: el uid cambia al cambiar de cuenta, y un aviso de más por un uid
 * viejo sería anunciarte el sitio del que te acabas de ir.
 */
class HomeNearbyAlertController(
    private val authRepository: AuthRepository,
    private val nearbySpotAlerts: NearbySpotAlerts,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val told = mutableSetOf<String>()
    private var hasBaseline = false
    private var lastAlertAtMs: Long? = null

    /**
     * Una mirada alrededor del usuario. Se llama con cada fix nuevo y con cada tanda de plazas: las
     * dos cosas cambian la respuesta, y llamar sólo en una dejaría el carril mudo la mitad del tiempo.
     *
     * @param anchor la posición del usuario. **Null es «todavía no lo sé»**, y entonces este carril no
     *        dice nada: sin saber dónde estás, «cerca de ti» no tiene significado. Nunca la cámara.
     */
    suspend fun look(anchor: GpsPoint?, spots: List<Spot>) {
        if (anchor == null) return

        val now = clock()
        val look = nearbySpotsWorthTelling(
            anchor = anchor,
            spots = spots,
            ownUserId = authRepository.getCurrentSession()?.userId,
            alreadyTold = told,
            hasBaseline = hasBaseline,
            lastAlertAtMs = lastAlertAtMs,
            nowMs = now,
        )

        told += look.toRecord.map { it.id }
        if (look.takingBaseline) hasBaseline = true
        look.alert?.let { spot ->
            lastAlertAtMs = now
            nearbySpotAlerts.announce(spot)
        }
    }
}
