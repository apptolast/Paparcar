package com.rndeveloper.paparcar.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * La puerta de Android al reparto, y nada más.
 * [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
 *
 * ⛔ **Aquí no se decide nada.** Qué dice un mensaje y qué se hace con él vive en
 * [PushMessageHandler], en `commonMain`, porque un `FirebaseMessagingService` es una clase que iOS
 * no puede tener y que ningún test puede instanciar — y el orden «primero la valla, después la
 * tarjeta» no puede existir dos veces. Lo que queda en este fichero es lo único que de verdad es de
 * Android: que el sistema entregue aquí, y que avise cuando el token rota.
 */
class PaparcarMessagingService : FirebaseMessagingService() {

    private val handler: PushMessageHandler by inject()
    private val subscriptions: ZoneAlertSubscriptionSync by inject()
    private val sharedCarMembers: SharedCarMemberSync by inject()

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
        scope.launch { handler.handle(message.data) }
    }

    private companion object {
        const val TAG = "ZonePush"
    }
}
