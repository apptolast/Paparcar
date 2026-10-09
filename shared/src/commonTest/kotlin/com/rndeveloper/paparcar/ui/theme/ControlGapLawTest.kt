package com.rndeveloper.paparcar.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * La ley del hueco junto a un control. [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001]
 *
 * > hueco visible = `spacedBy` + (48 dp − altura del control), por cada lado que sea un control
 *
 * Medida en device el 08-10 con dos sondas sobre la pareja de cápsulas de la pregunta: con la
 * cápsula a 40 dp y `spacedBy(12)` se veían **20,2 dp**; bajando la constante a 4 → **12,2**;
 * subiendo la cápsula a 48 sin tocar la constante → **4,2**.
 *
 * ⛔ Esto NO prueba que la app la aplique — prueba que el sistema sabe la cuenta. Que un sitio
 * concreto la use se ve en la captura, y por eso las dos cosas van juntas en el ticket.
 */
class ControlGapLawTest {

    @Test
    fun should_chargeNoOverhang_when_theControlAlreadyIsTheTouchTarget() {
        // Y ésta es la razón por la que `button` 48 es el único peldaño que no miente.
        assertEquals(0.dp, PapControlHeight.overhang(PapControlHeight.button))
    }

    @Test
    fun should_chargeHalfTheDifference_perSide() {
        assertEquals(4.dp, PapControlHeight.overhang(PapControlHeight.pill))   // (48-40)/2
        assertEquals(7.dp, PapControlHeight.overhang(PapControlHeight.chip))   // (48-34)/2
    }

    @Test
    fun should_neverChargeNegativeOverhang_when_theControlIsTallerThanTheTarget() {
        // Un control por encima de 48 no encoge el hueco: el mínimo táctil es un SUELO.
        assertEquals(0.dp, PapControlHeight.overhang(64.dp))
    }

    @Test
    fun should_discountBothSides_when_twoControlsFaceEachOther() {
        // El caso medido: dos cápsulas apiladas que han de VERSE a 12 dp.
        assertEquals(
            4.dp,
            PapGap.betweenControls(visible = 12.dp, controlHeight = PapControlHeight.pill),
        )
    }

    @Test
    fun should_discountOneSide_when_onlyOneNeighbourIsAControl() {
        // Texto arriba, cápsula abajo: sólo un lado trae aire invisible.
        assertEquals(
            8.dp,
            PapGap.betweenControls(visible = 12.dp, controlHeight = PapControlHeight.pill, sides = 1),
        )
    }

    @Test
    fun should_notGoNegative_when_theOverhangAlreadyExceedsTheGapAsked() {
        // Dos chips (7 dp por lado) que quisieran verse a 8: el aire invisible ya pasa de 8, así que
        // lo máximo que se puede hacer es no añadir nada. Pedir un hueco negativo solaparía los
        // controles y, peor, sus áreas de toque.
        assertEquals(
            0.dp,
            PapGap.betweenControls(visible = 8.dp, controlHeight = PapControlHeight.chip),
        )
    }
}
