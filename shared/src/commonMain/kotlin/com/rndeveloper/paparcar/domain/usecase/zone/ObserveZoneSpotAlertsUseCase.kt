@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.zone

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.repository.ZoneWatchRepository
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.zone.zoneAlertRadiusMeters
import com.rndeveloper.paparcar.domain.zone.zoneSpotsWorthTelling
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform
import kotlin.time.Clock

/** A free community spot just appeared inside one of the user's alert-subscribed zones. */
data class ZoneSpotAlert(
    val zoneId: String,
    val zoneName: String,
    val spot: Spot,
)

/**
 * El stream de alertas de zona: emite una vez por cada plaza NUEVA dentro de una zona con
 * [Zone.notifyOnSpot] encendido, **mientras el usuario tenga Plus** y el interruptor maestro de
 * Ajustes esté abierto. [MONEY-001]
 *
 * ## El gate de Plus está VIVO, y es el mecanismo de PAUSA por degradación
 * [ZONE-A-PAID-ALERT-MUST-NOT-BE-DOCUMENTED-AS-FREE-001]
 *
 * El aviso de zona **es de pago** — confirmado por el user el 27-09 — y este gate no es un adorno
 * duplicado de los interruptores: es lo único que se entera de que Plus ha CADUCADO. El opt-in de
 * cada zona sobrevive a propósito (para que recuperar Plus reanude las mismas zonas en vez de una
 * lista vacía), así que sin este gate un usuario al que le caduca la suscripción **sigue recibiendo
 * avisos de zona en local**, banner y notificación incluidos. Perder Plus calla este stream sin tocar
 * ningún opt-in; recuperarlo lo reanuda.
 *
 * ⛔ **Estuvo fuera del 21-09 al 27-09** por una decisión de lanzamiento (*«no será de pago, al menos
 * al principio»*) que el user revirtió. Lo grave no fue quitarlo, fue que se quitó del carril
 * equivocado: los dos interruptores que lo encienden siguieron abriendo el paywall, así que el
 * comportamiento nunca fue gratis y quedaron un KDoc y **dos tests verdes** afirmando que sí. Si algún
 * día vuelve a abrirse, se abre en los tres sitios a la vez o en ninguno.
 *
 * ## Dos carriles, una memoria
 *
 * Este stream es uno de DOS, y comparten el registro durable de qué se ha contado ya
 * ([ZoneWatchRepository]) para que no puedan discrepar sobre qué es noticia:
 *
 * 1. **Éste**, mientras la app vive, con el canal elegido por `alertChannelFor`.
 * 2. **El push del servidor** ([ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]), que
 *    entra por `PushMessageHandler`. Su puerta es el índice de suscripciones, y ésa ya gatea por Plus
 *    en `ZoneAlertSubscriptionSync` — de hecho era el único carril que seguía respetando el plan.
 *
 * ⚠️ **No hay ningún worker**, aunque este KDoc lo dijera hasta el 27-09: la mirada periódica de
 * [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001] se sustituyó por el fanout del servidor y el texto se
 * quedó. Lo que sigue siendo verdad es POR QUÉ la memoria salió de aquí: vivía en un `var
 * baselineTaken` y un `mutableSetOf` que ningún otro carril podía heredar, y además reseteaba su
 * baseline con CUALQUIER edición de zona, así que renombrar una zona hacía que todas sus plazas
 * volvieran a leerse como «ya estaban».
 *
 * La decisión es [zoneSpotsWorthTelling], compartida con el carril de push.
 */
class ObserveZoneSpotAlertsUseCase(
    private val zoneRepository: ZoneRepository,
    private val spotRepository: SpotRepository,
    private val watchRepository: ZoneWatchRepository,
    private val appPreferences: AppPreferences,
    private val authRepository: AuthRepository,
    private val observeIsPlus: ObserveIsPlusUseCase,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    operator fun invoke(): Flow<ZoneSpotAlert> =
        // ⛔ El gate se lee EN VIVO y por eso va en el `combine`: es lo que convierte una caducidad de
        // Plus en silencio inmediato sin borrar ningún opt-in.
        // [ZONE-A-PAID-ALERT-MUST-NOT-BE-DOCUMENTED-AS-FREE-001]
        combine(zoneRepository.observeZones(), observeIsPlus()) { zones, isPlus ->
            if (isPlus) zones.filter { it.notifyOnSpot } else emptyList()
        }
            .distinctUntilChanged()
            .flatMapLatest { subscribed ->
                if (subscribed.isEmpty()) {
                    emptyFlow()
                } else {
                    val ownUserId = authRepository.getCurrentSession()?.userId
                    subscribed.map { zone -> zoneAlerts(zone, ownUserId) }.merge()
                }
            }

    private fun zoneAlerts(zone: Zone, ownUserId: String?): Flow<ZoneSpotAlert> {
        val centre = GpsPoint(
            latitude = zone.lat,
            longitude = zone.lon,
            accuracy = 0f,
            timestamp = 0L,
            speed = 0f,
        )
        // The SAME ground the server's fanout asks for: two lanes watching different circles would
        // make "what is news" depend on whether the app happened to be open. (It said "the worker"
        // until 27-09; there is no worker. [ZONE-A-PAID-ALERT-MUST-NOT-BE-DOCUMENTED-AS-FREE-001])
        // [ZONE-A-LOOK-THAT-READS-THE-CACHE-IS-NOT-A-LOOK-001]
        return spotRepository.observeNearbySpots(centre, zoneAlertRadiusMeters(zone).toDouble())
            .transform { spots ->
                val now = clock()
                val look = zoneSpotsWorthTelling(
                    zone = zone,
                    spots = spots,
                    ownUserId = ownUserId,
                    alreadyAlerted = watchRepository.alertedSpotIds(zone.id),
                    hasBaseline = watchRepository.hasBaseline(zone.id),
                    nowMs = now,
                )
                // Recorded BEFORE emitting, and recorded whether or not the switch lets it speak:
                // what must never happen twice is the announcement, and a spot swallowed by a
                // closed master gate must not come back the moment it reopens.
                watchRepository.recordLook(
                    zoneId = zone.id,
                    spots = look.toRecord,
                    nowMs = now,
                    markBaseline = look.takingBaseline,
                )
                if (!appPreferences.notifyZoneSpots) return@transform
                look.alertable.forEach { spot ->
                    emit(ZoneSpotAlert(zoneId = zone.id, zoneName = zone.name, spot = spot))
                }
            }
            .catch { e ->
                // Stream isolation: one zone's failing geo query must not kill the whole merge.
                PaparcarLogger.w(TAG, "zone-alert stream failed for zone=${zone.id}", e)
            }
    }

    private companion object {
        const val TAG = "ZoneSpotAlerts"
    }
}
