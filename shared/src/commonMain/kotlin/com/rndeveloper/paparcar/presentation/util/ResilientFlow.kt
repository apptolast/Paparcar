package com.rndeveloper.paparcar.presentation.util

import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive

/**
 * Un stream que alimenta la UI viva **vuelve** después de un fallo, en vez de quedarse muerto.
 * [HOME-A-LIVE-STREAM-MUST-NOT-DIE-IN-SILENCE-001]
 *
 * ## Por qué existe
 *
 * `.catch { log }` no es una política de errores: registra y **termina el flujo**. La suscripción no
 * vuelve nunca, y como el ViewModel vive mientras vive la pantalla, «nunca» es hasta que el usuario
 * reinicia la app. Issue #127: el mapa dejaba de pintar la ruta y el coche a mitad de un viaje y no
 * volvía; la detección seguía funcionando perfectamente al otro lado de un stream que ya no había.
 *
 * Y era invisible tres veces: la excepción que circula en campo es un `CancellationException`
 * (medido: 86 eventos de `JobCancellationException` sobre la lectura de sesiones activas), que no
 * tumba la app y que `PaparcarLogger` excluye de Crashlytics a propósito.
 *
 * ## Las dos mitades del contrato
 *
 * - **Reintenta lo que es un fallo**, con backoff exponencial acotado. Cada reintento deja su línea
 *   con el número de intento: un stream que reintenta cien veces es OTRO bug, y tiene que poder
 *   verse en vez de esconderse detrás de una recuperación silenciosa.
 * - **No reintenta lo que no es un fallo.** Cerrar la pantalla cancela el scope, y eso no se
 *   reintenta: se comprueba `isActive` del contexto del COLECTOR, que es quien sabe si esto sigue
 *   importando. Sin esa mitad, cada cierre de Home dispararía una ronda de reintentos sobre flujos
 *   que ya nadie mira.
 *
 * ⛔ La etiqueta es `PARKDIAG/…` y no la del ViewModel. `FileAntilog` sólo persiste ese prefijo, y
 * **ninguna etiqueta de `presentation/` lo llevaba**: la única línea que explicaba el #127 vivía en
 * Logcat, que es debug-only, así que en campo no había dónde mirar.
 * [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001]
 *
 * @param label qué stream es, para poder leerlo en el log.
 * @param onError se invoca **una sola vez, en el primer fallo** — es para avisar al usuario
 *   (snackbar), no para contar reintentos. Un aviso por cada reintento sería una tormenta sobre un
 *   problema que se está arreglando solo.
 */
fun <T> Flow<T>.resilient(
    label: String,
    onError: suspend (Throwable) -> Unit = {},
): Flow<T> = retryWhen { cause, attempt ->
    // La pantalla se fue: esto no es un fallo que reintentar, es el final legítimo.
    if (!currentCoroutineContext().isActive) return@retryWhen false
    if (attempt == 0L) runCatching { onError(cause) }
    if (attempt >= MAX_RETRIES) return@retryWhen false
    val backoffMs = BASE_BACKOFF_MS shl attempt.toInt().coerceAtMost(MAX_BACKOFF_SHIFT)
    PaparcarLogger.w(DIAG, "⟳ $label cayó (intento ${attempt + 1}) — reintenta en $backoffMs ms: ${cause::class.simpleName}")
    delay(backoffMs)
    true
}
    // ⛔ El `catch` NO es redundante con el `retryWhen`: cuando el predicado dice que no reintente,
    // `retryWhen` **relanza** — no termina el flujo. Sin esto, rendirse habría matado la corrutina
    // que sostiene la pantalla, que es EXACTAMENTE el defecto que este fichero viene a quitar, sólo
    // que seis intentos más tarde. (Escrito después de que el testigo lo cazara.)
    //
    // `catch` reenvía por sí mismo la cancelación del propio job, así que cerrar la pantalla sigue
    // siendo un final limpio y no una línea de error.
    .catch { cause ->
        PaparcarLogger.e(DIAG, "✗ $label se rinde — la pantalla se queda con su último valor", cause)
    }

private const val DIAG = "PARKDIAG/Stream"

/** Reintentos antes de rendirse. Con el backoff de abajo son ~25 s de insistencia: suficiente para
 *  un hipo de Room o de la red, y corto para que un fallo real no se disfrace de lentitud. */
private const val MAX_RETRIES = 6L

private const val BASE_BACKOFF_MS = 250L

/** Techo del backoff: 250 ms << 5 = 8 s por reintento. */
private const val MAX_BACKOFF_SHIFT = 5
