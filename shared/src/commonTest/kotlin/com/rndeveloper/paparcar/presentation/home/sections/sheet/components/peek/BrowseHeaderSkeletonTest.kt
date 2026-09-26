package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cuándo puede la cabecera de browse dibujar un esqueleto.
 * [HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001]
 *
 * El testigo del defecto de campo es [should_speak_when_theAnswerCameBackWithoutAName]: sin él,
 * «no tengo nombre» bastaba para latir, y latía para siempre.
 */
class BrowseHeaderSkeletonTest {

    private val nowhere = AddressAndPlace(
        address = AddressInfo(street = null, city = null, region = null, country = null),
        placeInfo = null,
    )

    private val somewhere = AddressAndPlace(
        address = AddressInfo(street = "Calle Bahía de Cádiz", city = "El Puerto de Santa María", region = "Andalucía", country = "ES"),
        placeInfo = null,
    )

    /** Nadie ha contestado todavía y la pregunta está viva: el esqueleto ES la verdad. */
    @Test
    fun should_shimmer_when_nobodyHasAnsweredYetAndTheAskIsAlive() {
        assertTrue(browseHeaderIsStillAsking(cameraInfo = null, isCameraSettling = true))
    }

    /**
     * ⛔ El defecto: sin red, la fase 1 contesta una dirección VACÍA y nadie vuelve a preguntar.
     * La cabecera tiene que hablar —«Dirección desconocida»— en vez de latir hasta el reinicio.
     */
    @Test
    fun should_speak_when_theAnswerCameBackWithoutAName() {
        assertFalse(browseHeaderIsStillAsking(cameraInfo = nowhere, isCameraSettling = false))
    }

    /** Y tampoco late un `null` que ya no espera a nadie: sin pregunta viva no hay esqueleto. */
    @Test
    fun should_speak_when_thereIsNoAskInFlight() {
        assertFalse(browseHeaderIsStillAsking(cameraInfo = null, isCameraSettling = false))
    }

    /** Con nombre en la mano se enseña el nombre, aunque otra pregunta esté volando. */
    @Test
    fun should_keepTheName_when_aNewAskIsInFlight() {
        assertFalse(browseHeaderIsStillAsking(cameraInfo = somewhere, isCameraSettling = true))
        assertFalse(
            browseHeaderIsStillAsking(
                cameraInfo = nowhere.copy(placeInfo = PlaceInfo(name = "Poblado Naval 378AL", category = PlaceCategory.PARKING)),
                isCameraSettling = true,
            ),
        )
    }
}
