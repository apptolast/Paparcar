package com.rndeveloper.paparcar.domain.usecase.user

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.push.PushTokenProvider
import com.rndeveloper.paparcar.domain.repository.SharedCarRepository
import com.rndeveloper.paparcar.domain.repository.ZoneAlertSubscriptionRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

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
 * ⛔ Y **ningún fallo ni ninguna espera pueden impedir salir**, que es más fuerte que no fallar:
 * una escritura de Firestore sin red **no falla, se queda esperando el ack del servidor**, así que
 * un `runCatching` no la atrapa — suspende, y `signOut()` no llega nunca. Por eso cada paso remoto
 * va con plazo ([SIGN_OUT_STEP_TIMEOUT_MS]) y `signOut()` queda FUERA de todos ellos.
 * [PRIV-A-SIGN-OUT-WITHOUT-NETWORK-NEVER-SIGNS-OUT-001]
 *
 * ⚠️ Lo que se queda en la cola offline de Firestore **solo sale si ese mismo usuario vuelve a
 * entrar** en este móvil: la sesión que la autoriza es la que se está cerrando. O sea que sin red
 * el token puede quedar vivo en el servidor, y eso no lo arregla este caso de uso. Lo cubre la otra
 * mitad: el filtro de destinatario de `PushMessageHandler`, que no se fía de a quién iba dirigido
 * el mensaje. Dos mitades, y hacen falta las dos.
 */
class SignOutUseCase(
    private val authRepository: AuthRepository,
    private val zoneAlertSubscriptions: ZoneAlertSubscriptionRepository,
    private val sharedCarRepository: SharedCarRepository,
    private val pushTokens: PushTokenProvider,
) {

    suspend operator fun invoke(): Result<Unit> = runCatching {
        val uid = authRepository.getCurrentSession()?.userId

        // ⛔ UN presupuesto para las tres, y en paralelo. En serie eran tres plazos de 3 s, o sea
        // hasta NUEVE segundos mirando una pantalla que ya dijiste que querías abandonar — y las
        // tres retiradas son independientes: nada de lo que hace una cambia lo que hace otra.
        // [PRIV-SIGN-OUT-WAITS-ONCE-NOT-THREE-TIMES-001]
        withTimeoutOrNull(SIGN_OUT_TOTAL_TIMEOUT_MS) {
            coroutineScope {
                listOf(
                    // Las zonas: la fila entera se va, como al perder Plus o apagar el aviso.
                    async { if (uid != null) step("alertas de zona") { zoneAlertSubscriptions.replaceAll(uid, emptyList()).getOrThrow() } },
                    // Los grupos: se retira el TOKEN, no la pertenencia — salir no es salirse.
                    async { if (uid != null) step("tokens del grupo") { sharedCarRepository.retirePushTokens().getOrThrow() } },
                    // Y el propio token, para que ni una fila escapada alcance a este teléfono.
                    async { step("token de este móvil") { pushTokens.retireToken() } },
                ).awaitAll()
            }
        } ?: PaparcarLogger.w(TAG, "se agotó el plazo retirando tokens — se sale igual")

        // ⛔ FUERA del plazo: es lo que el usuario pidió, y es local.
        authRepository.signOut().getOrThrow()
    }

    /**
     * Un paso remoto que no puede secuestrar la salida: ni por fallar ni por no contestar.
     * [PRIV-A-SIGN-OUT-WITHOUT-NETWORK-NEVER-SIGNS-OUT-001]
     */
    private suspend fun step(what: String, block: suspend () -> Unit) {
        runCatching { block() }
            .onFailure { PaparcarLogger.w(TAG, "no se pudo retirar: $what", it) }
    }

    private companion object {
        const val TAG = "SignOut"

        /**
         * Lo que se espera a las tres retiradas JUNTAS. Tres segundos porque hay una persona
         * mirando la pantalla: pasado ese punto, seguir esperando no retira más tokens, solo
         * retiene a quien pidió salir.
         */
        const val SIGN_OUT_TOTAL_TIMEOUT_MS = 3_000L
    }
}
