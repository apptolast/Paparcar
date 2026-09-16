package com.rndeveloper.paparcar.push

import com.rndeveloper.paparcar.domain.push.PushTokenProvider
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lo que Swift tiene que saber hacer para que este móvil pueda recibir avisos.
 * [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
 *
 * Implementado en Swift por la misma razón que [com.rndeveloper.paparcar.core.crash.CrashReporterBridge]
 * y que la programación de `BGTaskScheduler`: el registro en APNs y la entrega del token pasan por
 * delegates de UIKit y del SDK de Firebase para iOS, que llega como framework nativo por SPM. Desde
 * Kotlin/Native no hay a quién llamar.
 *
 * Cableado (una vez, en `iosApp/iOSApp.swift`):
 *
 *     class SwiftPushRegistrationBridge: PushRegistrationBridge {
 *         func registerForRemoteNotifications() {
 *             DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
 *         }
 *     }
 *
 *     IosPushRegistry.shared.bridge = SwiftPushRegistrationBridge()
 */
interface PushRegistrationBridge {

    /**
     * Registra la app en APNs. Idempotente: iOS devuelve el mismo token mientras no cambie nada, y
     * llamar dos veces no cuesta nada.
     */
    fun registerForRemoteNotifications()
}

/**
 * El token de push de este iPhone, que Swift entrega y Kotlin espera.
 *
 * ⛔ **No es `@ThreadLocal`, y ahí está la diferencia con `CrashReporter`.** El token lo escribe el
 * hilo principal (el delegate de Firebase) y lo lee una corrutina en `Dispatchers.Default`: un
 * singleton por hilo dejaría al lector mirando un slot vacío para siempre, que es exactamente el
 * fallo mudo que este ticket existe para quitar. El modelo de memoria de Kotlin/Native permite
 * estado mutable compartido desde 1.7, y [MutableStateFlow] es seguro entre hilos.
 *
 * ⛔ **El registro se pide bajo demanda, no al arrancar.** La doctrina de [ZoneAlertSubscriptionSync]
 * dice que el token no se pide si no hay nada que suscribir; en iOS pedirlo ES registrarse en APNs,
 * así que registrar en el arranque convertiría esa regla en letra muerta. Quien llame a
 * [currentToken] es quien tiene algo que publicar.
 */
object IosPushRegistry : PushTokenProvider {

    var bridge: PushRegistrationBridge? = null

    private val token = MutableStateFlow<String?>(null)

    /** El delegate de Firebase, cada vez que APNs contesta o el token rota. */
    /**
     * ⛔ En iOS no se invalida un token de APNs desde Kotlin: lo que hay es lo que Swift entregó.
     * Lo que sí se puede es OLVIDARLO, para que nada vuelva a publicarlo con la sesión cerrada; el
     * registro se rehace bajo demanda en cuanto haya algo que suscribir, que es la doctrina de esta
     * clase. La retirada real de las filas la hace el barrido de cierre de sesión.
     * [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001]
     */
    override suspend fun retireToken() {
        token.value = null
    }

    fun onToken(value: String) {
        val rotated = token.value != null && token.value != value
        token.value = value
        PaparcarLogger.d(TAG) { if (rotated) "token rotado" else "token recibido" }
    }

    override suspend fun currentToken(): String? {
        token.value?.let { return it }
        val bridge = this.bridge ?: run {
            PaparcarLogger.w(TAG, "sin puente de registro — este móvil no puede recibir avisos")
            return null
        }
        bridge.registerForRemoteNotifications()
        // Si APNs no contesta a tiempo, quien llama sigue sin token y lo dice. La siguiente pasada
        // de su flujo vuelve a preguntar, y para entonces el token ya suele estar puesto.
        return withTimeoutOrNull(TOKEN_WAIT_MS) { token.filterNotNull().first() }
            ?: run {
                PaparcarLogger.w(TAG, "APNs no ha devuelto token en ${TOKEN_WAIT_MS}ms")
                null
            }
    }

    private const val TAG = "IosPush"

    /** Un registro en APNs con red tarda menos de un segundo; sin red no llega nunca. */
    private const val TOKEN_WAIT_MS = 10_000L
}
