package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [UI-A-STATUS-BADGE-CAN-BE-DISMISSED-A-QUESTION-CANNOT-001] Se puede callar un AVISO; una PREGUNTA
 * no.
 */
class DetectionNoticeTest {

    private fun watching(badge: ParkedWatchBadge) = DetectionStory.Watching(
        vehicleName = "Škoda Kamiq",
        isParked = true,
        watch = VehicleWatch.Assisted,
        watchBadge = badge,
    )

    @Test
    fun should_offer_no_way_to_silence_a_question() {
        // ⛔ EL TESTIGO de la mitad que importa: una pregunta callada sin contestar es una respuesta
        // perdida, y las tres ya traen su propia salida («Descartar», «Sigo conduciendo», «No voy en
        // mi coche»). Como `notice()` devuelve null, el llamante no tiene qué pasarle a la ×: la
        // imposibilidad es estructural, no una regla escrita en un comentario.
        assertNull(DetectionStory.AwaitingAnswer(PendingPromptWindow(shownAtMs = 0L)).notice())
        assertNull(DetectionStory.PendingAsk.notice())
        assertNull(DetectionStory.AwaitingFirstPark.notice())
        // Y la línea calmada tampoco: no hay aviso que callar cuando no hay aviso.
        assertNull(watching(ParkedWatchBadge.WATCHING).notice())
    }

    @Test
    fun should_name_every_system_health_notice() {
        // La lista es exactamente la de las historias que NO hospeda la card. Si alguien mueve una
        // de sitio, este test y `isTheCarsQuestion` tienen que moverse a la vez — que es el punto.
        assertEquals(DetectionNotice.LocationBlocked, DetectionStory.BlockedCore.notice())
        assertEquals(DetectionNotice.DetectionOff, DetectionStory.Inactive.notice())
        assertEquals(DetectionNotice.NoVehicle, DetectionStory.NoVehicle.notice())
        assertEquals(DetectionNotice.WatchFragile, watching(ParkedWatchBadge.WATCHING_FRAGILE).notice())
        assertEquals(DetectionNotice.WatchInterrupted, watching(ParkedWatchBadge.WATCH_INTERRUPTED).notice())
    }

    @Test
    fun should_be_silenceable_exactly_when_the_card_does_not_host_it() {
        // Las dos fronteras son LA MISMA, y vale la pena atarlas: lo que la card hospeda es lo que se
        // contesta, y lo que se calla es lo que la card no hospeda. Un tercer criterio suelto sería
        // la forma de acabar con un aviso que no se puede callar Y que nadie hospeda.
        val everyStory = listOf(
            DetectionStory.AwaitingAnswer(PendingPromptWindow(shownAtMs = 0L)),
            DetectionStory.PendingAsk,
            DetectionStory.AwaitingFirstPark,
            DetectionStory.BlockedCore,
            DetectionStory.Inactive,
            DetectionStory.NoVehicle,
            watching(ParkedWatchBadge.WATCHING_FRAGILE),
            watching(ParkedWatchBadge.WATCH_INTERRUPTED),
        )
        everyStory.forEach { story ->
            assertTrue(
                story.isTheCarsQuestion() != (story.notice() != null),
                "$story: o lo hospeda la card (y se contesta), o es un aviso (y se calla)",
            )
        }
    }

    @Test
    fun should_silence_only_the_notice_the_user_dismissed() {
        val silenced = setOf(DetectionNotice.WatchFragile)
        assertEquals(DetectionStory.Hidden, watching(ParkedWatchBadge.WATCHING_FRAGILE).silencedBy(silenced))
        // Y nada más: callar el aviso frágil no calla el del vigilante MUERTO, que es otro hecho y
        // más grave. Apartar de más es como se pierde el aviso que sí importaba.
        assertEquals(
            watching(ParkedWatchBadge.WATCH_INTERRUPTED),
            watching(ParkedWatchBadge.WATCH_INTERRUPTED).silencedBy(silenced),
        )
        assertEquals(DetectionStory.BlockedCore, DetectionStory.BlockedCore.silencedBy(silenced))
    }

    @Test
    fun should_never_silence_a_question_even_if_the_set_says_so() {
        // El conjunto no puede contener una pregunta (no tienen `DetectionNotice`), pero el testigo
        // ata el resultado: con TODOS los avisos callados, las preguntas siguen en pantalla.
        val allSilenced = DetectionNotice.entries.toSet()
        val ask = DetectionStory.AwaitingAnswer(PendingPromptWindow(shownAtMs = 0L))
        assertEquals(ask, ask.silencedBy(allSilenced))
        assertEquals(DetectionStory.PendingAsk, DetectionStory.PendingAsk.silencedBy(allSilenced))
        assertEquals(DetectionStory.AwaitingFirstPark, DetectionStory.AwaitingFirstPark.silencedBy(allSilenced))
    }
}
