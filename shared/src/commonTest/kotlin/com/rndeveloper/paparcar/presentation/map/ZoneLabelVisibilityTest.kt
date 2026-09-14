package com.rndeveloper.paparcar.presentation.map

import com.rndeveloper.paparcar.presentation.util.ZONE_LABEL_MIN_RING_DP
import com.rndeveloper.paparcar.presentation.util.zoneLabelShowsName
import com.rndeveloper.paparcar.presentation.util.zoneRingRadiusDp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El umbral del rótulo de zona [UI-A-ZONE-IS-AN-AREA-NOT-A-PIN-001].
 *
 * Lo que estos tests vigilan no es la fórmula de Mercator —esa se copia de un libro— sino el
 * CONTRATO: que el nombre desaparezca al alejarse, que vuelva al acercarse, y que ningún dato raro
 * (radio 0, latitud imposible) devuelva un tamaño que haga aparecer un rótulo donde no hay anillo.
 */
class ZoneLabelVisibilityTest {

    private val madridLat = 40.4168
    private val typicalZoneRadiusMeters = 80f

    @Test
    fun should_showName_when_ringIsBigEnoughOnScreen() {
        // Zoom de calle: el anillo de una zona típica ocupa buena parte de la pantalla.
        assertTrue(zoneLabelShowsName(typicalZoneRadiusMeters, madridLat, zoom = 17f))
    }

    @Test
    fun should_hideName_when_ringIsSmallerThanThreshold() {
        // Zoom de ciudad: el mismo anillo es un punto, y un nombre ahí no titula nada.
        assertFalse(zoneLabelShowsName(typicalZoneRadiusMeters, madridLat, zoom = 13f))
    }

    @Test
    fun should_flipBackToName_when_zoomingInAgain() {
        // El umbral no es de una sola dirección: es la MISMA cuenta, así que volver a acercarse
        // tiene que devolver el nombre. Un rótulo que se va y no vuelve es el bug que más se nota.
        val far = zoneLabelShowsName(typicalZoneRadiusMeters, madridLat, zoom = 12f)
        val near = zoneLabelShowsName(typicalZoneRadiusMeters, madridLat, zoom = 18f)
        assertFalse(far)
        assertTrue(near)
    }

    @Test
    fun should_growTheRing_when_zoomIncreasesByOne() {
        // Cada nivel de zoom DUPLICA el tamaño en pantalla. Si esto se rompe, el umbral deja de
        // significar lo que dice significar aunque los dos tests de arriba sigan pasando.
        val atFifteen = zoneRingRadiusDp(typicalZoneRadiusMeters, madridLat, zoom = 15f)
        val atSixteen = zoneRingRadiusDp(typicalZoneRadiusMeters, madridLat, zoom = 16f)
        assertEquals(2f, atSixteen / atFifteen, absoluteTolerance = 0.001f)
    }

    @Test
    fun should_returnZero_when_zoneHasNoRadius() {
        assertEquals(0f, zoneRingRadiusDp(radiusMeters = 0f, latitude = madridLat, zoom = 17f))
        assertEquals(0f, zoneRingRadiusDp(radiusMeters = -10f, latitude = madridLat, zoom = 17f))
        assertFalse(zoneLabelShowsName(radiusMeters = 0f, latitude = madridLat, zoom = 21f))
    }

    @Test
    fun should_stayFinite_when_latitudeIsBeyondMercator() {
        // Mercator no está definido en los polos: una latitud corrupta no puede devolver un anillo
        // infinito, que en pantalla sería un rótulo permanente sobre cualquier zoom.
        val atPole = zoneRingRadiusDp(typicalZoneRadiusMeters, latitude = 90.0, zoom = 17f)
        assertTrue(atPole.isFinite())
        assertTrue(atPole > 0f)
    }

    @Test
    fun should_needLessZoom_when_zoneIsBigger() {
        // Una zona grande se llama antes que una pequeña, porque su anillo llega antes al umbral.
        val bigZoneAtCityZoom = zoneRingRadiusDp(radiusMeters = 1_000f, latitude = madridLat, zoom = 13f)
        assertTrue(bigZoneAtCityZoom >= ZONE_LABEL_MIN_RING_DP)
        assertTrue(zoneLabelShowsName(radiusMeters = 1_000f, latitude = madridLat, zoom = 13f))
    }
}
