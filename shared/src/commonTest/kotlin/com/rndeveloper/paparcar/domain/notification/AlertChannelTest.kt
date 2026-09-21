package com.rndeveloper.paparcar.domain.notification

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
 *
 * Existe porque el aviso salía SIEMPRE por notificación del sistema, también con el mapa delante —
 * y una notificación mientras miras el mapa obliga a bajar la persiana para leer algo que ya
 * estabas mirando.
 */
class AlertChannelTest {

    @Test
    fun should_tellItInsideTheApp_when_theMapIsOnScreen() {
        assertEquals(AlertChannel.IN_APP, alertChannelFor(mapOnScreen = true))
    }

    @Test
    fun should_fallBackToTheSystemNotification_when_nobodyIsLookingAtTheMap() {
        // ⛔ Y esto cubre MÁS que «la app cerrada»: con Ajustes o el garaje en pantalla la app está
        // en primer plano y el usuario no está mirando dónde salen las plazas. La notificación del
        // sistema es la única que sobrevive a que cambie de pantalla.
        assertEquals(AlertChannel.SYSTEM_NOTIFICATION, alertChannelFor(mapOnScreen = false))
    }

    @Test
    fun should_neverUseBothChannels_forTheSameAlert() {
        // El invariante, escrito como testigo: la función es total y devuelve UN valor, así que no
        // hay forma de que una plaza suene dos veces. Si algún día esto pasara a devolver una
        // lista, este test es el que hay que mirar antes de hacerlo.
        val channels = listOf(true, false).map { alertChannelFor(it) }
        assertEquals(channels.size, channels.toSet().size, "cada situación tiene su propio canal")
    }

    @Test
    fun should_startWithTheMapOffScreen() {
        // Una señal de visibilidad que naciera en `true` entregaría el primer aviso dentro de una
        // app que todavía no ha pintado nada.
        assertEquals(false, MapVisibility().onScreen.value)
    }

    @Test
    fun should_followTheLifecycleBracket() {
        val visibility = MapVisibility()
        visibility.set(true)
        assertEquals(true, visibility.onScreen.value)
        visibility.set(false)
        assertEquals(false, visibility.onScreen.value)
    }
}
