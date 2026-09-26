package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cuándo late la cápsula del contador del peek, y cuándo enseña su cifra.
 * [HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001]
 *
 * La regla es la MISMA que la de las filas de debajo (`isLoading && lista vacía`), y el testigo del
 * defecto de campo es [should_showTheFigure_when_thereAreRowsOnShow]: cinco parkings recordados
 * pintados, la pestaña diciendo «5» y esta cápsula escondiéndolo.
 */
class NearbyCounterPulseTest {

    @Test
    fun should_pulse_when_theLaneIsAskingWithNothingToShow() {
        assertTrue(nearbyCounterPulses(count = null, isLoading = true))
        assertTrue(nearbyCounterPulses(count = 0, isLoading = true))
    }

    /** ⛔ El defecto: la respuesta recordada del disco YA está en pantalla mientras vuela la de red. */
    @Test
    fun should_showTheFigure_when_thereAreRowsOnShow() {
        assertFalse(nearbyCounterPulses(count = 5, isLoading = true))
    }

    /** Sin pregunta viva no hay latido, haya cifra o no: un hueco quieto no es un hueco cargando. */
    @Test
    fun should_notPulse_when_nobodyIsAsking() {
        assertFalse(nearbyCounterPulses(count = null, isLoading = false))
        assertFalse(nearbyCounterPulses(count = 0, isLoading = false))
        assertFalse(nearbyCounterPulses(count = 5, isLoading = false))
    }
}
