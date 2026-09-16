package com.rndeveloper.paparcar.domain.usecase.user

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.push.PushTokenProvider
import com.rndeveloper.paparcar.domain.repository.SharedCarRepository
import com.rndeveloper.paparcar.domain.repository.ZoneAlertSubscriptionRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

/**
 * Cerrar sesión, retirando antes lo que seguiría llegando a este móvil.
 * [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001]
 *
 * `SESSION-ISOLATION-001` ya limpiaba Room, las geovallas y las preferencias, y dejó fuera el único
 * dato que no vive en el móvil ni en la cuenta sino **en el servidor apuntando al móvil**: el token
 * de push. Cerrar sesión lo dejaba escrito en dos sitios —`zoneAlertSubs/{uid}_{zoneId}` y
 * `sharedCars/{carId}/members/{uid}`— y el reparto seguía, así que el siguiente que entrara en ese
 * teléfono recibía plazas de zonas ajenas y dónde había aparcado el coche de un grupo al que no
 * pertenece. Las suscripciones caducan a los 30 días: la ventana era larga.
 *
 * ## El orden es la mitad del arreglo
 *
 * ⛔ **Todo lo remoto va ANTES de `signOut()`**, mientras el uid sigue siendo el de la sesión: las
 * reglas exigen ser ese uid para escribir en sus filas, así que retirarlo después no falla — hace
 * algo peor, se queda a medias sin decirlo.
 *
 * ⛔ Y **ningún fallo puede impedir salir**. Cerrar sesión es una petición del usuario, no una
 * transacción: si el móvil está sin red, se sale igual y el token queda retirado en lo local. Lo
 * que cubre ese hueco es el filtro de destinatario de `PushMessageHandler`, que no se fía de a
 * quién iba dirigido el mensaje. Dos mitades, y hacen falta las dos.
 */
class SignOutUseCase(
    private val authRepository: AuthRepository,
    private val zoneAlertSubscriptions: ZoneAlertSubscriptionRepository,
    private val sharedCarRepository: SharedCarRepository,
    private val pushTokens: PushTokenProvider,
) {

    suspend operator fun invoke(): Result<Unit> = runCatching {
        val uid = authRepository.getCurrentSession()?.userId
        if (uid != null) {
            // Las zonas: la fila entera se va, que es lo que ya hace perder Plus o apagar el aviso.
            zoneAlertSubscriptions.replaceAll(uid, emptyList())
                .onFailure { PaparcarLogger.w(TAG, "no se pudieron retirar las alertas de zona", it) }
            // Los grupos: se retira el TOKEN, no la pertenencia — cerrar sesión no es salirse.
            sharedCarRepository.retirePushTokens()
                .onFailure { PaparcarLogger.w(TAG, "no se pudieron retirar los tokens del grupo", it) }
        }
        // Y el propio token, para que ni una fila que se nos haya escapado alcance a este teléfono.
        runCatching { pushTokens.retireToken() }
            .onFailure { PaparcarLogger.w(TAG, "no se pudo invalidar el token de este móvil", it) }

        authRepository.signOut().getOrThrow()
    }

    private companion object {
        const val TAG = "SignOut"
    }
}
