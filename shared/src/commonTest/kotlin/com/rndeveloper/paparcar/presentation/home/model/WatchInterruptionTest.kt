package com.rndeveloper.paparcar.presentation.home.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Quién se gana desplegar la hoja sobre la fila de vigilancia.
 * [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
 */
class WatchInterruptionTest {

    @Test
    fun should_open_when_the_watch_dies_while_watching() {
        assertTrue(
            watchInterruptionEarnsAnOpen(
                from = ParkedWatchBadge.WATCHING,
                to = ParkedWatchBadge.WATCH_INTERRUPTED,
            ),
        )
    }

    /**
     * Llegar directamente a «en pausa» sí abre — pero sólo porque con
     * [com.rndeveloper.paparcar.domain.detection.ServicePresence.Unknown] como valor inicial, eso
     * exige que el servicio haya dicho `Dead`: una muerte MEDIDA.
     * [DET-A-SILENT-SERVICE-IS-NOT-A-DEAD-ONE-001]
     */
    @Test
    fun should_open_when_the_first_verdict_is_a_measured_death() {
        assertTrue(watchInterruptionEarnsAnOpen(from = null, to = ParkedWatchBadge.WATCH_INTERRUPTED))
    }

    /**
     * ⛔⛔ **El arranque en frío, que es el bug que esta función tuvo.** El camino normal es
     * «todavía no sé» → «vigilando», y ahí no hay nada que avisar. Antes de
     * [DET-A-SILENT-SERVICE-IS-NOT-A-DEAD-ONE-001] el primer valor era «en pausa» y la hoja se
     * abría sola **cada vez que abrías la app** con el coche aparcado.
     */
    @Test
    fun should_not_open_on_a_cold_start_that_turns_out_healthy() {
        assertFalse(
            watchInterruptionEarnsAnOpen(
                from = ParkedWatchBadge.WATCH_UNKNOWN,
                to = ParkedWatchBadge.WATCHING,
            ),
        )
    }

    /** Y la duda misma nunca interrumpe: no hay noticia, hay falta de noticia. */
    @Test
    fun should_not_open_while_the_watch_is_still_unknown() {
        assertFalse(watchInterruptionEarnsAnOpen(from = null, to = ParkedWatchBadge.WATCH_UNKNOWN))
        assertFalse(
            watchInterruptionEarnsAnOpen(
                from = ParkedWatchBadge.WATCHING,
                to = ParkedWatchBadge.WATCH_UNKNOWN,
            ),
        )
    }

    /** Pero si la duda se resuelve en muerte, eso SÍ es un suceso medido y se avisa. */
    @Test
    fun should_open_when_the_unknown_resolves_into_a_death() {
        assertTrue(
            watchInterruptionEarnsAnOpen(
                from = ParkedWatchBadge.WATCH_UNKNOWN,
                to = ParkedWatchBadge.WATCH_INTERRUPTED,
            ),
        )
    }

    @Test
    fun should_open_when_a_fragile_watch_then_dies() {
        assertTrue(
            watchInterruptionEarnsAnOpen(
                from = ParkedWatchBadge.WATCHING_FRAGILE,
                to = ParkedWatchBadge.WATCH_INTERRUPTED,
            ),
        )
    }

    /**
     * ⛔ La frágil NO interrumpe: es una condición de configuración permanente, la vigilancia sigue
     * viva, y abrir la hoja por ella sería abrirla en cada arranque.
     */
    @Test
    fun should_not_open_when_the_watch_only_becomes_fragile() {
        assertFalse(
            watchInterruptionEarnsAnOpen(
                from = ParkedWatchBadge.WATCHING,
                to = ParkedWatchBadge.WATCHING_FRAGILE,
            ),
        )
    }

    /** Ni siquiera llegando ya frágil. */
    @Test
    fun should_not_open_when_home_arrives_with_a_fragile_watch() {
        assertFalse(watchInterruptionEarnsAnOpen(from = null, to = ParkedWatchBadge.WATCHING_FRAGILE))
    }

    /** Se abre UNA vez por muerte: seguir muerta no es noticia nueva. */
    @Test
    fun should_not_open_twice_for_the_same_death() {
        assertFalse(
            watchInterruptionEarnsAnOpen(
                from = ParkedWatchBadge.WATCH_INTERRUPTED,
                to = ParkedWatchBadge.WATCH_INTERRUPTED,
            ),
        )
    }

    /** Y recuperarse no abre nada: la fila que explicaría el problema ya no existe. */
    @Test
    fun should_not_open_when_the_watch_recovers() {
        assertFalse(
            watchInterruptionEarnsAnOpen(
                from = ParkedWatchBadge.WATCH_INTERRUPTED,
                to = ParkedWatchBadge.WATCHING,
            ),
        )
    }

    /** Salir del contexto de vigilancia (coche liberado) tampoco. */
    @Test
    fun should_not_open_when_the_watch_context_disappears() {
        assertFalse(watchInterruptionEarnsAnOpen(from = ParkedWatchBadge.WATCH_INTERRUPTED, to = null))
        assertFalse(watchInterruptionEarnsAnOpen(from = null, to = null))
    }

    /** Un coche sin marcar no es una vigilancia rota. */
    @Test
    fun should_not_open_for_an_unmarked_car() {
        assertFalse(
            watchInterruptionEarnsAnOpen(
                from = ParkedWatchBadge.WATCHING,
                to = ParkedWatchBadge.PARK_MY_VEHICLE,
            ),
        )
    }
}
