package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.presentation.home.model.FirstStepGuide
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El realce de un paso se apaga cuando el usuario USA el control al que señala — los cuatro
 * destinos con la misma regla. [ONB-A-GUIDE-MUST-NOT-OUTLIVE-THE-STEP-THAT-ASKED-FOR-IT-001]
 *
 * ⛔ **Este test existe porque la regla anterior se escribió dentro del único carril que la
 * probaba.** La condición decía `firstStepGuide != ADD_ZONE_CHIP || current == PRIVATE_ZONE`: para
 * los otros tres destinos el primer operando ya era `true`, el `||` la hacía cierta para siempre, y
 * el realce no se apagaba JAMÁS. Nadie lo vio porque la zona —el único caso que se probó— era
 * justamente el único sin el bug.
 *
 * Así que lo que se comprueba aquí no es «¿funciona para el caso de uso?», sino **«¿responde la
 * regla para los CUATRO?»** — recorriendo el enum entero, de forma que un destino nuevo que nadie
 * conecte salga en rojo en vez de heredar un «sí» silencioso.
 */
class FirstStepGuideTest {

    @Test
    fun should_dieWhenItsOwnControlIsUsed_forEveryDestination() {
        // La prueba recorre el enum: si mañana nace un quinto destino y nadie le da su evento de
        // uso, esta lista deja de cubrirlo y el `assertEquals` del final lo canta.
        val usedBy: Map<FirstStepGuide, () -> Boolean> = mapOf(
            FirstStepGuide.SPOTS_LANE to {
                FirstStepGuide.SPOTS_LANE.isUsedBy(HomeIntent.SetNearbyLens(HomeNearbyLens.SPOTS))
            },
            FirstStepGuide.NEARBY_COMPLEMENT_TABS to {
                FirstStepGuide.NEARBY_COMPLEMENT_TABS
                    .isUsedBy(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
            },
            FirstStepGuide.ADD_ZONE_CHIP to {
                FirstStepGuide.ADD_ZONE_CHIP.isUsedBy(HomeIntent.EnterAddZoneMode(lat = 36.6, lon = -6.2))
            },
            FirstStepGuide.REPORT_SPOT_CARD to {
                FirstStepGuide.REPORT_SPOT_CARD.isUsedBy(HomeSheetAction.RequestReportMode)
            },
        )

        assertEquals(
            FirstStepGuide.entries.toSet(),
            usedBy.keys,
            "un destino de FirstStepGuide sin evento de uso declarado: su realce no se apagaria " +
                "nunca, que es exactamente el bug que cerro este ticket",
        )
        for ((guide, used) in usedBy) {
            assertTrue(used(), "$guide no se apaga al usar su propio control")
        }
    }

    @Test
    fun should_surviveAnActionOnAnotherControl_when_theGuidePointsElsewhere() {
        // Tocar el carril de parkings no puede apagar el realce que señala al de plazas: el realce
        // SEÑALA, y apagarlo con un toque ajeno lo volveria aleatorio.
        assertFalse(FirstStepGuide.SPOTS_LANE.isUsedBy(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL)))
        assertFalse(
            FirstStepGuide.NEARBY_COMPLEMENT_TABS
                .isUsedBy(HomeIntent.SetNearbyLens(HomeNearbyLens.SPOTS)),
        )
        assertFalse(FirstStepGuide.ADD_ZONE_CHIP.isUsedBy(HomeSheetAction.RequestReportMode))
        assertFalse(FirstStepGuide.REPORT_SPOT_CARD.isUsedBy(HomeIntent.EnterAddZoneMode(null, null)))
    }

    @Test
    fun should_countOpeningASpot_as_usingTheSpotsLane() {
        // El carril ES la pestaña Y la lista debajo: quien abre una plaza ha encontrado el carril.
        assertTrue(
            FirstStepGuide.SPOTS_LANE.isUsedBy(HomeIntent.SelectItem(HomeSelection.Spot("spot-1"))),
        )
        // Pero abrir OTRA cosa no: seleccionar un parking publico no es usar el carril de plazas.
        assertFalse(
            FirstStepGuide.SPOTS_LANE.isUsedBy(HomeIntent.SelectItem(HomeSelection.PublicParking("p-1"))),
        )
    }

    @Test
    fun should_onlyLetTheZoneGuideOutliveItsTap() {
        // ⛔ El filtro que impide el arreglo "obvio": generalizar «apagalo cuando el paso deje de
        // ser el actual» a los cuatro mataria el realce en el MISMO fotograma para FIND_SPOT y
        // EXPLORE_NEARBY, que se cierran en el toque que lo encendio.
        assertTrue(FirstStepGuide.ADD_ZONE_CHIP.stepOutlivesTheTap)
        for (guide in FirstStepGuide.entries - FirstStepGuide.ADD_ZONE_CHIP) {
            assertFalse(
                guide.stepOutlivesTheTap,
                "$guide se cierra en el toque que enciende el realce: si su paso 'sobrevive', el " +
                    "interruptor del paso lo apagaria antes de que se vea",
            )
        }
    }

    @Test
    fun should_pointAtTheStepThatAsked() {
        assertEquals(FirstStep.FIND_SPOT, FirstStepGuide.SPOTS_LANE.step)
        assertEquals(FirstStep.FIND_SPOT, FirstStepGuide.REPORT_SPOT_CARD.step)
        assertEquals(FirstStep.EXPLORE_NEARBY, FirstStepGuide.NEARBY_COMPLEMENT_TABS.step)
        assertEquals(FirstStep.PRIVATE_ZONE, FirstStepGuide.ADD_ZONE_CHIP.step)
    }
}
