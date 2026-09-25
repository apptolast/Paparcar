package com.rndeveloper.paparcar.presentation.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [MAP-A-CAMERA-MOVE-MUST-NOT-COST-A-PINCH-001] Mover la cámara cambia DÓNDE miras, no cuánto te
 * acercas — salvo que el destino tenga tamaño y haya que verlo entero.
 */
class CameraFramingTest {

    @Test
    fun should_not_zoom_out_when_going_to_a_place() {
        // La queja de la issue #31: mirando de cerca, tocas una zona y te aleja a 15.
        assertEquals(17f, settleZoom(current = 17f, wanted = 15f, intent = ZoomIntent.AtLeast))
        assertEquals(18.5f, settleZoom(current = 18.5f, wanted = 16f, intent = ZoomIntent.AtLeast))
    }

    @Test
    fun should_zoom_in_when_the_place_needs_more_than_the_current_view() {
        assertEquals(15f, settleZoom(current = 12f, wanted = 15f, intent = ZoomIntent.AtLeast))
    }

    @Test
    fun should_obey_the_frame_when_the_thing_has_a_size() {
        // Dimensionar una zona SÍ puede exigir alejar: hay que ver el círculo entero.
        assertEquals(13f, settleZoom(current = 18f, wanted = 13f, intent = ZoomIntent.Fit))
    }

    @Test
    fun should_take_the_wanted_zoom_when_the_camera_has_not_landed_yet() {
        assertEquals(15f, settleZoom(current = null, wanted = 15f, intent = ZoomIntent.AtLeast))
    }

    @Test
    fun should_frame_a_zone_by_its_radius_not_by_a_fixed_level() {
        // El 14 fijo de antes enseñaba ~4 km para dimensionar un garaje de 40 m.
        assertEquals(17f, zoomForZoneRadius(40f), "un garaje se dimensiona a nivel calle")
        assertEquals(16f, zoomForZoneRadius(150f))
        assertEquals(15f, zoomForZoneRadius(Zone_DEFAULT_RADIUS))
        assertEquals(14f, zoomForZoneRadius(700f))
        assertEquals(13f, zoomForZoneRadius(1_500f), "un barrio entero sí necesita alejarse")
        assertEquals(12f, zoomForZoneRadius(5_000f))
    }

    @Test
    fun should_widen_monotonically_as_the_radius_grows() {
        // Un radio mayor nunca puede pedir MÁS zoom que uno menor: la escalera no puede cruzarse.
        val radii = listOf(20f, 80f, 200f, 400f, 800f, 1_600f, 4_000f)
        val zooms = radii.map { zoomForZoneRadius(it) }
        assertEquals(zooms.sortedDescending(), zooms, "la escalera debe ser monótona")
    }

    // ── [HOME-A-FOLLOWED-TRIP-MUST-STILL-OBEY-ITS-ZOOM-001] ──────────────────────────────────
    // Seguir al conductor manda DÓNDE mira la cámara; el zoom sigue siendo del host. El mapa se
    // saltaba el destino ENTERO mientras seguía —posición y zoom—, así que abrir la app en mitad
    // de un viaje se quedaba en el zoom de arranque: el mundo entero, con el coche centrado.

    @Test
    fun should_zoom_in_on_a_followed_trip_when_the_camera_is_still_at_the_unplaced_world_view() {
        // La issue #66 medida: cámara sin posar (~2.5), el viaje pide nivel conducción.
        assertEquals(
            15f,
            followLockZoom(current = 2.5f, target = CameraTarget(0.0, 0.0, zoom = 15f), following = true),
            "un viaje que se sigue desde la vista de mundo tiene que acercarse",
        )
    }

    @Test
    fun should_leave_the_zoom_alone_while_following_when_the_driver_already_looks_closer() {
        // Sigue siendo un SUELO: media hora de viaje no puede deshacer el pellizco del usuario.
        assertNull(
            followLockZoom(current = 17f, target = CameraTarget(0.0, 0.0, zoom = 15f), following = true),
        )
    }

    @Test
    fun should_ask_for_nothing_while_following_when_the_target_carries_no_zoom() {
        // Cada fix del puck reescribe el destino sin zoom: ninguno de ellos debe tocar la cámara.
        assertNull(followLockZoom(current = 15f, target = CameraTarget(0.0, 0.0), following = true))
    }

    @Test
    fun should_ask_for_nothing_when_no_trip_is_being_followed() {
        // Sin enganche manda el tween de siempre; éste no debe duplicarlo.
        assertNull(
            followLockZoom(current = 2.5f, target = CameraTarget(0.0, 0.0, zoom = 15f), following = false),
        )
    }

    private companion object {
        /** `Zone.DEFAULT_RADIUS_METERS`, escrito aquí para no arrastrar el modelo a este test. */
        const val Zone_DEFAULT_RADIUS = 250f
    }
}
