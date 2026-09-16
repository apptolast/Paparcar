package com.rndeveloper.paparcar.push

import com.google.firebase.messaging.FirebaseMessaging
import com.rndeveloper.paparcar.domain.push.PushTokenProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * El token de este móvil, según FCM. [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
 *
 * Envuelto a mano en vez de arrastrar `kotlinx-coroutines-play-services` por una sola llamada: una
 * dependencia, un grafo transitivo y una cosa más que mantener en su última estable, contra seis
 * líneas que hacen exactamente esto.
 *
 * ⚠️ `getToken()` y `deleteToken()` los marca como deprecados firebase-messaging 25.1.3 (la migración de registro
 * FCM→FID) y **todavía no hay sustituto**: la documentación apunta a `onNewToken`, que el mismo SDK
 * también marca. No hay fecha de retirada anunciada. Suprimido a sabiendas, no por reflejo — sin
 * esta llamada, un móvil que nunca haya rotado su token no entraría jamás en ninguna lista.
 */
class FirebaseMessagingTokenProvider : PushTokenProvider {

    @Suppress("DEPRECATION")
    override suspend fun currentToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> if (cont.isActive) cont.resume(token) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }

    /**
     * ⛔ Falla en silencio a propósito: esto corre al cerrar sesión, y un token que no se pudo
     * invalidar —sin red, típicamente— no puede impedir que el usuario salga. Lo que protege de
     * verdad contra un mensaje ajeno es el filtro de destinatario del handler; esto solo acorta la
     * ventana. [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001]
     */
    @Suppress("DEPRECATION")
    override suspend fun retireToken() {
        suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().deleteToken()
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { if (cont.isActive) cont.resume(Unit) }
        }
    }
}
