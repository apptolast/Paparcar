package com.rndeveloper.paparcar.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import androidx.work.WorkManager
import com.rndeveloper.paparcar.detection.worker.GeofenceJanitorWorker
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.ZoneWatchRepository
import com.rndeveloper.paparcar.domain.sharedcar.ParkingMark
import com.rndeveloper.paparcar.domain.sharedcar.isSameParking
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * The receiving end of the zone push. [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
 *
 * ⛔ The message is a **data** one, so nothing is shown until this class says so — and that is the
 * whole reason it is not a notification message. Two routes can announce the same free space (this
 * one, and the live stream while the app is open), and what has already been said lives in exactly
 * one place: `zone_alerted_spots`. Letting FCM draw the card itself would have put a second copy of
 * that rule inside the notification tray, where nothing can check it.
 */
class PaparcarMessagingService : FirebaseMessagingService() {

    private val notificationPort: AppNotificationManager by inject()
    private val zoneWatch: ZoneWatchRepository by inject()
    private val subscriptions: ZoneAlertSubscriptionSync by inject()
    private val sharedCarMembers: SharedCarMemberSync by inject()
    private val parkingRepository: UserParkingRepository by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * A token is not forever: Firebase rotates it on reinstall, restore and clear-data. A rotation
     * nobody wrote down is a device that keeps its subscription row and silently stops receiving
     * anything — the exact failure this whole ticket exists to avoid, one layer lower.
     */
    @Deprecated(
        "Flagged by firebase-messaging 25.1.3 as part of the FCM→FID registration migration. " +
            "There is no replacement callback yet and no removal date has been announced, so the " +
            "choice is between this and having no idea the token rotated. Revisit when Firebase " +
            "ships the successor — a rotation nobody hears about is a device that silently stops " +
            "receiving alerts.",
    )
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        PaparcarLogger.d(TAG) { "FCM token rotated — resyncing subscriptions" }
        subscriptions.resyncNow()
        // El mismo token vale para las dos vigilancias, y las dos se quedan mudas si nadie lo
        // apunta. [SHARED-A-PARKING-TELLS-THE-GROUP-001]
        sharedCarMembers.resyncNow()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] == TYPE_SHARED_CAR_PARKED) {
            onSharedCarParked(data)
            return
        }
        if (data["type"] == TYPE_SHARED_CAR_LEFT) {
            onSharedCarLeft(data)
            return
        }
        if (data["type"] != TYPE_ZONE_SPOT) {
            PaparcarLogger.w(TAG, "unknown push type=${data["type"]}")
            return
        }
        val spotId = data["spotId"] ?: return
        val zoneId = data["zoneId"] ?: return
        val zoneName = data["zoneName"].orEmpty()
        val latitude = data["latitude"]?.toDoubleOrNull() ?: return
        val longitude = data["longitude"]?.toDoubleOrNull() ?: return
        val isProvisional = data["provisional"]?.toBooleanStrictOrNull() == true
        val expiresAt = data["expiresAt"]?.toLongOrNull() ?: 0L

        scope.launch {
            val now = System.currentTimeMillis()
            // Already dead by the time it reached us: a push about a space that no longer exists is
            // worse than no push at all.
            if (expiresAt != 0L && expiresAt <= now) {
                PaparcarLogger.d(TAG) { "· spot $spotId expired before delivery" }
                return@launch
            }
            // The single door. The stream may have said it already while the app was open.
            if (zoneWatch.hasAnnounced(zoneId, spotId)) {
                PaparcarLogger.d(TAG) { "· spot $spotId already announced for zone $zoneId" }
                return@launch
            }
            zoneWatch.recordAnnounced(zoneId, spotId, expiresAt, now)
            notificationPort.showZoneSpotAlert(
                zoneName = zoneName,
                latitude = latitude,
                longitude = longitude,
                isProvisional = isProvisional,
            )
            PaparcarLogger.i(TAG, "▶ zone push shown: zone=$zoneName provisional=$isProvisional")
        }
    }

    /**
     * Alguien del grupo ha aparcado el coche. [SHARED-A-PARKING-TELLS-THE-GROUP-001]
     *
     * Dos cosas, y la segunda es la que la geocerca compartida estaba esperando:
     *  1. **Contarlo** — la tarjeta se construye AQUÍ, que es donde están los nueve idiomas. El
     *     servidor no sabe ninguno y no tiene por qué.
     *  2. **Mover la vigilancia AHORA** — el reconciliador ya sabe poner la valla donde está el
     *     coche, pero hasta este mensaje solo se enteraba en la siguiente pasada del janitor (15
     *     min) o al abrir la app. Esto es lo que convierte «acaba con enterarse» en «se entera».
     *
     * ⛔ El autor no recibe este mensaje: a quién se le cuenta lo decide el servidor, que lo excluye
     * del reparto. Un móvil no puede saber si el aviso era para otro.
     */
    private fun onSharedCarParked(data: Map<String, String>) {
        moveTheGroupFenceNow()

        val latitude = data["latitude"]?.toDoubleOrNull()
        val longitude = data["longitude"]?.toDoubleOrNull()
        if (latitude == null || longitude == null) {
            PaparcarLogger.w(TAG, "shared-car push sin posición — valla movida, tarjeta no")
            return
        }
        scope.launch {
            if (isMyOwnTrip(data, latitude, longitude)) {
                PaparcarLogger.d(TAG) { "· el aviso habla del viaje que acabo de hacer yo — no se muestra" }
                return@launch
            }
            notificationPort.showSharedCarParked(
                authorName = data["authorName"]?.takeIf { it.isNotBlank() },
                addressLine = data["addressLine"]?.takeIf { it.isNotBlank() },
                latitude = latitude,
                longitude = longitude,
            )
        }
    }

    /**
     * El coche compartido se ha ido. [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001]
     *
     * Las mismas dos cosas y el mismo orden, y aquí la valla pesa todavía más: lo que hay que hacer
     * es QUITARLA de un sitio que el coche ha dejado. Una valla plantada donde ya no hay coche es un
     * trigger de detección esperando a que su dueño pase andando por esa calle.
     */
    private fun onSharedCarLeft(data: Map<String, String>) {
        moveTheGroupFenceNow()
        notificationPort.showSharedCarLeft(data["authorName"]?.takeIf { it.isNotBlank() })
    }

    /**
     * ⛔ Primero y sin condiciones: el reconciliador no lee ni un campo de estos mensajes —relee el
     * espejo—, así que un campo que falte puede costar la tarjeta, nunca la vigilancia. Y el janitor
     * sigue siendo el ÚNICO sitio que registra vallas; esto solo le dice que pase ya.
     * [DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001]
     */
    private fun moveTheGroupFenceNow() {
        GeofenceJanitorWorker.enqueueOnce(
            WorkManager.getInstance(applicationContext),
            GeofenceJanitorWorker.TRIGGER_SHARED_CAR_PUSH,
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
    private suspend fun isMyOwnTrip(data: Map<String, String>, latitude: Double, longitude: Double): Boolean {
        val carId = data["carId"] ?: return false
        val sessionId = data["sessionId"] ?: return false
        val parkedAtMs = data["parkedAtMs"]?.toLongOrNull() ?: return false
        // El id local del coche vinculado ES el carId: compartir no re-keya nada. [VEH-SHARED-001]
        val mine = runCatching { parkingRepository.getActiveSessionByVehicle(carId) }.getOrNull() ?: return false
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
        const val TAG = "ZonePush"

        /** ⛔ Mirrored in `functions/src/index.ts`. */
        const val TYPE_SHARED_CAR_PARKED = "shared_car_parked"
        const val TYPE_SHARED_CAR_LEFT = "shared_car_left"
        const val TYPE_ZONE_SPOT = "zone_spot"
    }
}
