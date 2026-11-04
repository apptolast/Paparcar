package com.rndeveloper.paparcar.presentation.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La gramática de dos líneas: la 2 dice dónde y a cuánto, y nunca repite la 1.
 * [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
 */
class SubjectLinesTest {

    @Test
    fun should_put_the_place_before_the_distance() {
        assertEquals(
            "Ronda de Valencia 12 · a 450 m",
            subjectWhereLine(
                name = "Repsol",
                placeLine = "Ronda de Valencia 12",
                secondaryLine = "Madrid",
                farText = "a 450 m",
            ),
        )
    }

    /** ⛔ El defecto que el user midió: el sitio ya es el nombre, así que abajo va el nivel de abajo. */
    @Test
    fun should_drop_a_level_when_the_name_already_is_the_street() {
        assertEquals(
            "Madrid · a 450 m",
            subjectWhereLine(
                name = "Ronda de Valencia 12",
                placeLine = "Ronda de Valencia 12",
                secondaryLine = "Madrid",
                farText = "a 450 m",
            ),
        )
    }

    /** Y si no queda nivel al que bajar, la línea es sólo la distancia — no una repetición. */
    @Test
    fun should_keep_only_the_distance_when_every_place_repeats_the_name() {
        assertEquals(
            "a 450 m",
            subjectWhereLine(
                name = "Madrid",
                placeLine = "Madrid",
                secondaryLine = "Madrid",
                farText = "a 450 m",
            ),
        )
    }

    /** ⛔ Sin fix no hay distancia que decir, y el sitio sigue siendo una respuesta. */
    @Test
    fun should_keep_the_place_without_a_fix() {
        assertEquals(
            "Ronda de Valencia 12",
            subjectWhereLine(
                name = "Repsol",
                placeLine = "Ronda de Valencia 12",
                secondaryLine = null,
                farText = null,
            ),
        )
    }

    @Test
    fun should_say_nothing_when_there_is_nothing_to_say() {
        assertNull(subjectWhereLine(name = "Repsol", placeLine = null, secondaryLine = null, farText = null))
    }

    /** Una cadena en blanco no es un sitio: no se escribe ni se queda con el separador. */
    @Test
    fun should_not_treat_a_blank_place_as_a_place() {
        assertEquals(
            "Madrid · a 450 m",
            subjectWhereLine(name = "Repsol", placeLine = "   ", secondaryLine = "Madrid", farText = "a 450 m"),
        )
        assertEquals(
            "a 450 m",
            subjectWhereLine(name = "Repsol", placeLine = "", secondaryLine = null, farText = "a 450 m"),
        )
    }

    /** Sin nivel de abajo tampoco se inventa: el sitio que repite se cae y queda la distancia. */
    @Test
    fun should_not_invent_a_second_level() {
        assertEquals(
            "a 120 m",
            subjectWhereLine(
                name = "Parking Benavente",
                placeLine = "Parking Benavente",
                secondaryLine = null,
                farText = "a 120 m",
            ),
        )
    }
}
