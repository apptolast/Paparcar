@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import com.rndeveloper.paparcar.fakes.FakePermissionManager
import com.rndeveloper.paparcar.fakes.FakeSpotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [HomeSpotsController] — the nearby-spots subscription + query centre, exposed as the
 * cold [HomeSpotsController.updates] flow. Covers the permission gate, the error path, and the
 * pan/recenter + resubscribe-threshold logic.
 *
 * Uses the eager [UnconfinedTestDispatcher] (no debounce/delay in this pipeline) so permission +
 * centre emissions propagate synchronously. Note the pipeline emits an initial `Data(empty)` as soon
 * as it is collected (permission denied masks the centre to null) — assertions therefore count deltas
 * or look at the latest emission rather than absolute emission counts.
 */
class HomeSpotsControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var scope: CoroutineScope

    private lateinit var permissions: FakePermissionManager
    private lateinit var spotRepo: FakeSpotRepository

    /** Every emission of the collected updates flow, in order. */
    private val received = mutableListOf<SpotsUpdate>()

    private val center = GpsPoint(40.0, -3.0, 0f, 0L, 0f)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scope = CoroutineScope(testDispatcher)
        permissions = FakePermissionManager()
        spotRepo = FakeSpotRepository()
        received.clear()
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun startController(): HomeSpotsController {
        val controller = HomeSpotsController(
            permissionManager = permissions,
            observeNearbySpots = ObserveNearbySpotsUseCase(spotRepo),
        )
        scope.launch { controller.updates.collect { received.add(it) } }
        return controller
    }

    private fun spot(id: String) = Spot(id = id, location = center, reportedBy = "u1", address = null, placeInfo = null)

    private fun lastSpots(): List<Spot>? =
        (received.lastOrNull { it is SpotsUpdate.Data } as? SpotsUpdate.Data)?.spots

    private fun dataCount(): Int = received.count { it is SpotsUpdate.Data }

    // ── Permission gate + fetch ─────────────────────────────────────────────────

    @Test
    fun `should_emit_nearby_spots_without_the_location_permission_when_a_centre_is_set`() = runTest {
        spotRepo.spots = listOf(spot("s1"))
        val controller = startController()

        controller.updateQueryCenter(center)

        // ⛔ Esto devolvía lista VACÍA: el permiso enmascaraba el centro a null. El efecto en mano
        // era que sin permiso el carril decía «no hay plazas cerca» sin haber preguntado nunca,
        // panearas donde panearas. El centro de consulta no es la posición del usuario — es el sitio
        // del mapa por el que pregunta. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
        assertEquals(1, lastSpots()?.size)
    }

    @Test
    fun `should_say_unasked_when_there_is_no_centre_at_all`() = runTest {
        spotRepo.spots = listOf(spot("s1"))
        startController()

        // Sin centro no se ha preguntado, y eso NO es una lista vacía: «no hay plazas» sería un
        // veredicto sobre un sitio que nadie ha mirado.
        assertTrue(received.lastOrNull() is SpotsUpdate.Unasked)
    }

    @Test
    fun `should_emit_nearby_spots_when_permission_granted_and_centre_set`() = runTest {
        spotRepo.spots = listOf(spot("s1"), spot("s2"))
        val controller = startController()

        permissions.emit(FakePermissionManager.allGranted())
        controller.updateQueryCenter(center)

        assertEquals(2, lastSpots()?.size)
    }

    @Test
    fun `should_emit_error_then_empty_spots_when_the_stream_fails`() = runTest {
        spotRepo.observeError = RuntimeException("boom")
        val controller = startController()

        permissions.emit(FakePermissionManager.allGranted())
        controller.updateQueryCenter(center)

        assertEquals("boom", (received.lastOrNull { it is SpotsUpdate.Error } as? SpotsUpdate.Error)?.message)
        // The stream recovers to an empty list down the same pipe.
        assertEquals(emptyList(), lastSpots())
    }

    // ── Pan / recenter threshold ────────────────────────────────────────────────

    @Test
    fun `should_seed_the_centre_on_pan_when_there_is_none`() = runTest {
        spotRepo.spots = listOf(spot("s1"))
        val controller = startController()

        // ⛔ Antes salía con `false` mientras el centro fuese nulo: sin fix, la consulta no arrancaba
        // JAMÁS por mucho que el usuario llevase el mapa a su calle. Buscar una dirección o arrastrar
        // el mapa dicen de qué sitio hablas igual que un fix. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
        assertTrue(controller.maybeRecenterOnPan(41.0, -4.0))
        assertEquals(1, lastSpots()?.size)
    }

    @Test
    fun `should_not_seed_the_centre_at_the_gulf_of_guinea`() = runTest {
        val controller = startController()

        // (0, 0) no es un sitio: es lo que un mapa reporta antes de tener cámara. Sembrar ahí
        // pediría las plazas del Atlántico. Mismo filtro que `viewportCentreOrGps`.
        assertFalse(controller.maybeRecenterOnPan(0.0, 0.0))
        assertTrue(received.lastOrNull() is SpotsUpdate.Unasked)
    }

    @Test
    fun `should_not_recenter_on_a_small_pan_within_the_threshold`() = runTest {
        val controller = startController()
        controller.recenter(center)

        // ~50 m north of the centre — under the 300 m pan threshold.
        assertFalse(controller.maybeRecenterOnPan(40.00045, -3.0))
    }

    @Test
    fun `should_recenter_on_a_large_pan_beyond_the_threshold`() = runTest {
        val controller = startController()
        controller.recenter(center)

        // ~1.1 km north — well beyond the 300 m pan threshold.
        assertTrue(controller.maybeRecenterOnPan(40.01, -3.0))
    }

    // ── Resubscribe threshold (distinctUntilChanged / closeEnoughTo) ────────────

    @Test
    fun `should_not_resubscribe_on_gps_drift_within_the_threshold_but_resubscribe_on_a_jump`() = runTest {
        spotRepo.spots = listOf(spot("s1"))
        val controller = startController()
        permissions.emit(FakePermissionManager.allGranted())

        controller.updateQueryCenter(center)
        val countAfterFirst = dataCount()

        // ~50 m drift — under SPOT_RESUBSCRIBE_THRESHOLD_METERS: deduped, no new emission.
        controller.updateQueryCenter(GpsPoint(40.00045, -3.0, 0f, 0L, 0f))
        assertEquals(countAfterFirst, dataCount())

        // ~1.1 km jump — beyond the threshold: resubscribes → one new emission.
        controller.updateQueryCenter(GpsPoint(40.01, -3.0, 0f, 0L, 0f))
        assertEquals(countAfterFirst + 1, dataCount())
    }
}
