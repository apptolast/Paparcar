@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.rndeveloper.paparcar.presentation.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Que un stream de la UI viva **vuelva** después de un fallo.
 * [HOME-A-LIVE-STREAM-MUST-NOT-DIE-IN-SILENCE-001]
 *
 * ⛔ EL TESTIGO de la issue #127. Con `.catch { log }`, un fallo registraba y TERMINABA el flujo: la
 * suscripción no volvía, y como el ViewModel vive lo que vive la pantalla, eso era «hasta que el
 * usuario reinicie la app». El mapa dejaba de pintar la ruta y el coche a mitad de viaje mientras la
 * detección seguía funcionando al otro lado de un stream que ya no existía.
 */
class ResilientFlowTest {

    @Test
    fun should_keepDelivering_when_theStreamFailsOnce() = runTest {
        var subscriptions = 0
        val flaky = flow {
            subscriptions++
            if (subscriptions == 1) {
                emit("antes")
                error("hipo de Room")
            }
            emit("después")
        }

        val seen = flaky.resilient("test").take(2).toList()

        assertEquals(listOf("antes", "después"), seen, "el valor de después del fallo LLEGA")
        assertEquals(2, subscriptions, "y llega porque se resuscribió, no por casualidad")
    }

    @Test
    fun should_keepDelivering_when_theFailureIsACancellationThatIsNotOurs() = runTest {
        // ⛔ La mitad que hace invisible al #127. La excepción que circula en campo es un
        // `CancellationException` ajeno —86 eventos medidos sobre la lectura de sesiones activas—,
        // que no tumba la app y que `PaparcarLogger` excluye de Crashlytics a propósito. Si el
        // operador no la reintentara, el arreglo no cubriría el caso real.
        var subscriptions = 0
        val flaky = flow {
            subscriptions++
            if (subscriptions == 1) throw CancellationException("job ajeno cancelado")
            emit("vivo")
        }

        assertEquals(listOf("vivo"), flaky.resilient("test").take(1).toList())
        assertEquals(2, subscriptions)
    }

    @Test
    fun should_warnTheUserOnce_when_theStreamKeepsFailing() = runTest {
        // El aviso es para el usuario, no un contador de reintentos: una tormenta de snackbars
        // sobre un problema que se está arreglando solo es peor que el problema.
        var warned = 0
        var subscriptions = 0
        val alwaysBroken = flow<String> {
            subscriptions++
            error("roto")
        }

        alwaysBroken.resilient("test", onError = { warned++ }).toList()

        assertEquals(1, warned, "un aviso, en el primer fallo")
        assertTrue(subscriptions > 1, "pero sí reintentó")
    }

    @Test
    fun should_giveUpEventually_when_nothingEverWorks() = runTest {
        // Se rinde, y el flujo TERMINA sin lanzar: la pantalla se queda con su último valor en vez
        // de llevarse por delante la corrutina que la sostiene — que es el defecto original.
        var subscriptions = 0
        val alwaysBroken = flow<String> {
            subscriptions++
            error("roto")
        }

        val seen = alwaysBroken.resilient("test").toList()

        assertEquals(emptyList(), seen)
        assertTrue(subscriptions in 2..10, "insiste, pero no para siempre — fueron $subscriptions")
    }

    @Test
    fun should_notRetry_when_theScreenIsGone() = runTest {
        // La otra mitad del contrato: cerrar Home cancela el scope, y eso NO es un fallo. Sin esta
        // comprobación, cada salida de la pantalla dispararía una ronda de reintentos sobre flujos
        // que ya no mira nadie.
        var subscriptions = 0
        val hanging = flow<String> {
            subscriptions++
            awaitCancellation()
        }

        val job = launch { hanging.resilient("test").collect() }
        advanceUntilIdle()
        job.cancelAndJoin()
        advanceTimeBy(60_000)

        assertEquals(1, subscriptions, "una pantalla cerrada no se resuscribe")
    }
}
