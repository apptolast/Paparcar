package com.rndeveloper.paparcar.push

import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.push.PushPayload
import com.rndeveloper.paparcar.domain.push.SharedCarFenceRefresher
import com.rndeveloper.paparcar.domain.push.pushPayloadFor
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.ZoneWatchRepository
import com.rndeveloper.paparcar.domain.sharedcar.ParkingMark
import com.rndeveloper.paparcar.domain.sharedcar.isSameParking
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * Qué se hace con un push, una sola vez para las dos plataformas.
 * [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
 *
 * Esto vivía dentro de `PaparcarMessagingService`, que es un `FirebaseMessagingService` de Android:
 * una clase que iOS no puede tener y que ningún test puede instanciar. El gemelo obvio habría sido
 * copiarla a `iosMain` — y con ella, **dos copias del orden que el commit `d6e890f2` puso por
 * escrito**. Un orden duplicado es un orden que en algún momento deja de coincidir, así que lo que
 * se comparte es esta clase entera y lo que cambia por plataforma son dos puertos.
 *
 * El orden, que es la regla entera:
 *  1. **La valla, primero y sin condiciones** ([SharedCarFenceRefresher]). No lee ni un campo del
 *     mensaje: relee el espejo. Un campo que falte puede costar la tarjeta, nunca la vigilancia.
 *  2. **La tarjeta, después**, y solo si hay con qué dibujarla y algo que contar.
 */
class PushMessageHandler(
    private val notificationPort: AppNotificationManager,
    private val zoneWatch: ZoneWatchRepository,
    private val parkingRepository: UserParkingRepository,
    private val sharedCarFences: SharedCarFenceRefresher,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    suspend fun handle(data: Map<String, String>) {
        when (val payload = pushPayloadFor(data, clock())) {
            // ⛔ Antes del `when` interno a propósito: las dos noticias del coche compartido mueven
            // la valla, y la que trae una tarjeta imposible de pintar también.
            is PushPayload.SharedCarNews -> {
                sharedCarFences.refreshNow()
                onSharedCarNews(payload)
            }

            is PushPayload.ZoneSpot -> onZoneSpot(payload)

            is PushPayload.Ignored -> PaparcarLogger.d(TAG) { "· ${payload.reason}" }
        }
    }

    /**
     * Una plaza libre en una zona vigilada.
     * [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
     *
     * La puerta única. El stream puede haberlo dicho ya con la app abierta, y lo que se ha dicho
     * vive en un solo sitio: `zone_alerted_spots`. Dejar que FCM pintara el aviso él mismo habría
     * puesto una segunda copia de esa regla dentro de la bandeja del sistema, donde nada la puede
     * comprobar.
     */
    private suspend fun onZoneSpot(payload: PushPayload.ZoneSpot) {
        if (zoneWatch.hasAnnounced(payload.zoneId, payload.spotId)) {
            PaparcarLogger.d(TAG) { "· spot ${payload.spotId} ya anunciada en ${payload.zoneId}" }
            return
        }
        zoneWatch.recordAnnounced(payload.zoneId, payload.spotId, payload.expiresAt, clock())
        notificationPort.showZoneSpotAlert(
            zoneName = payload.zoneName,
            latitude = payload.latitude,
            longitude = payload.longitude,
            isProvisional = payload.isProvisional,
        )
        PaparcarLogger.i(
            TAG,
            "▶ zone push mostrado: zona=${payload.zoneName} provisional=${payload.isProvisional}",
        )
    }

    private suspend fun onSharedCarNews(payload: PushPayload.SharedCarNews) {
        when (payload) {
            is PushPayload.SharedCarLeft ->
                // Sin sitio al que ir: la noticia es justo que ese punto ya no vale.
                notificationPort.showSharedCarLeft(payload.authorName)

            is PushPayload.SharedCarParked -> onSharedCarParked(payload)
        }
    }

    private suspend fun onSharedCarParked(payload: PushPayload.SharedCarParked) {
        val latitude = payload.latitude
        val longitude = payload.longitude
        if (latitude == null || longitude == null) {
            PaparcarLogger.w(TAG, "shared-car push sin posición — valla movida, tarjeta no")
            return
        }
        if (isMyOwnTrip(payload, latitude, longitude)) {
            PaparcarLogger.d(TAG) { "· el aviso habla del viaje que acabo de hacer yo — no se muestra" }
            return
        }
        notificationPort.showSharedCarParked(
            authorName = payload.authorName,
            addressLine = payload.addressLine,
            latitude = latitude,
            longitude = longitude,
        )
    }

    /**
     * Si el aparcamiento que anuncia el grupo es el que acabo de hacer yo, desde el asiento de al
     * lado. [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001]
     *
     * Dos personas del grupo en el mismo coche: los dos móviles detectan la misma parada, el espejo
     * se queda con el primero que llega y el segundo recibe un aviso de un viaje en el que iba. La
     * exclusión del autor la hace el servidor y aquí no alcanza — para el servidor son dos personas
     * distintas, y lo son; lo que comparten es el viaje, y eso solo lo sabe este móvil.
     *
     * ⛔ Se calla la TARJETA, nunca la valla: una duda sobre si esto era mi viaje no puede dejar una
     * geocerca donde el coche ya no está.
     */
    private suspend fun isMyOwnTrip(
        payload: PushPayload.SharedCarParked,
        latitude: Double,
        longitude: Double,
    ): Boolean {
        val carId = payload.carId ?: return false
        val sessionId = payload.sessionId ?: return false
        val parkedAtMs = payload.parkedAtMs ?: return false
        // El id local del coche vinculado ES el carId: compartir no re-keya nada. [VEH-SHARED-001]
        val mine = runCatching { parkingRepository.getActiveSessionByVehicle(carId) }
            .getOrNull() ?: return false
        return isSameParking(
            mine = ParkingMark(
                sessionId = mine.id,
                atMs = mine.location.timestamp,
                latitude = mine.location.latitude,
                longitude = mine.location.longitude,
            ),
            theirs = ParkingMark(
                sessionId = sessionId,
                atMs = parkedAtMs,
                latitude = latitude,
                longitude = longitude,
            ),
        )
    }

    private companion object {
        const val TAG = "Push"
    }
}
