package com.rndeveloper.paparcar.presentation.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cuándo la app puede decir cuánto se tarda, y cuándo se calla.
 * [COPY-A-DURATION-ROLLS-INTO-HOURS-AND-A-CAPACITY-IS-NOT-FREE-SPACES-001]
 *
 * El caso que abrió el ticket: un parking a **477 km** enseñaba «955 min en coche» — una constante
 * urbana de 30 km/h extrapolada a un viaje de autovía, con un error de más del triple. La decisión
 * de callarse vive aquí, en una función pura, y no dentro del `@Composable` que la pintaba, que es
 * donde nadie podía mirarla.
 */
class TravelEstimateTest {

    @Test
    fun should_estimateDrive_when_theTripIsUrban() {
        // 2 km a 30 km/h ≈ 4 min.
        assertEquals(4, driveMinutesOrNull(2_000f))
        // 20 km es el borde declarado del terreno urbano, y el borde CUENTA.
        assertNotNull(driveMinutesOrNull(20_000f), "20 km cae dentro del terreno de la estimación")
    }

    @Test
    fun should_sayNothing_when_theDriveLeavesTheUrbanConstantBehind() {
        // El caso real de la issue #130: 477,3 km en línea recta.
        assertNull(
            driveMinutesOrNull(477_300f),
            "una constante urbana no puede hablar de un viaje de 477 km",
        )
        assertNull(driveMinutesOrNull(20_001f), "pasado el borde, ninguna cifra")
    }

    @Test
    fun should_neverClaimZeroMinutes_when_theThingIsRightThere() {
        assertEquals(1, driveMinutesOrNull(5f))
        assertEquals(1, walkMinutes(5f))
    }

    /**
     * La de andar **no** tiene techo, y es deliberado: la velocidad de una persona no cambia con la
     * distancia, así que su estimación sigue siendo cierta donde la del coche ya no lo es. Lo que
     * caduca es la constante urbana del coche, no el paso de nadie.
     */
    @Test
    fun should_keepEstimating_when_theWalkIsLong() {
        val minutes = walkMinutes(6_000f) // 6 km a 4,8 km/h
        assertEquals(75, minutes)
        assertTrue(minutes > 60, "un paseo largo se sigue pudiendo contar, en horas y minutos")
    }
}
