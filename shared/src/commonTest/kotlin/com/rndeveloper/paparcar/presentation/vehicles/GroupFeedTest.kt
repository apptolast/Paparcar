package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SharedCarSession
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compartir un coche no le da una vida nueva.
 * [VEH-SHARING-A-CAR-DOES-NOT-ERASE-ITS-PAST-001]
 *
 * El día que el dueño abría el grupo, su página se quedaba en blanco: el historial pasaba a leerse
 * del espejo, que nace vacío y jamás recibirá su pasado. Historial, gráfico de actividad y cifras
 * salen todos del MISMO agregador sobre estas filas [STATS-CORE-001], así que lo que estas pruebas
 * fijan sobre la lista vale igual para las otras dos.
 */
class GroupFeedTest {

    private companion object {
        const val OWNER = "uid_owner"
        const val OTHER = "uid_other"
        const val CAR = "vehicle_1"
    }

    private fun gps(atMs: Long) = GpsPoint(
        latitude = 40.4,
        longitude = -3.7,
        accuracy = 8f,
        timestamp = atMs,
        speed = 0f,
    )

    private fun mine(id: String, atMs: Long, route: String? = "polyline_local") = UserParking(
        id = id,
        userId = OWNER,
        vehicleId = CAR,
        location = gps(atMs),
        routePolyline = route,
    )

    private fun mirrored(
        id: String,
        author: String,
        atMs: Long,
        route: String? = null,
    ) = SharedCarSession(
        sessionId = id,
        authorUid = author,
        location = gps(atMs),
        parkedAtMs = atMs,
        routePolyline = route,
    )

    @Test
    fun should_keepEveryOwnSession_when_theMirrorIsStillEmpty() {
        // El caso que rompía: acabas de compartir, el espejo no tiene nada, y tu historial entero
        // se iba con él.
        val feed = GroupFeed(sessions = emptyList(), hiddenRouteAuthors = emptySet())
        val past = listOf(mine("s1", 3_000), mine("s2", 2_000), mine("s3", 1_000))

        val rows = feed.rowsWith(past, CAR)

        assertEquals(3, rows.size)
        assertContentEquals(listOf("s1", "s2", "s3"), rows.map { it.id })
    }

    @Test
    fun should_showBothOwnAndGroupSessions_when_somebodyElseHasParked() {
        val feed = GroupFeed(
            sessions = listOf(mirrored("g1", OTHER, 2_500)),
            hiddenRouteAuthors = emptySet(),
        )

        val rows = feed.rowsWith(listOf(mine("s1", 3_000), mine("s2", 1_000)), CAR)

        // Ordenadas por hora, no por procedencia: una lista de varios conductores es UNA lista.
        assertContentEquals(listOf("s1", "g1", "s2"), rows.map { it.id })
        assertEquals(OTHER, rows.single { it.id == "g1" }.userId)
    }

    @Test
    fun should_notDuplicateARow_when_anOwnSessionAlsoReachedTheMirror() {
        // Una sesión propia posterior a compartir vive en las DOS listas con el mismo id: el espejo
        // copia `UserParking.id` en `sessionId`.
        val feed = GroupFeed(
            sessions = listOf(mirrored("s1", OWNER, 3_000)),
            hiddenRouteAuthors = emptySet(),
        )

        val rows = feed.rowsWith(listOf(mine("s1", 3_000)), CAR)

        assertEquals(1, rows.size)
    }

    @Test
    fun should_preferTheLocalCopy_when_theSameSessionIsOnBothSides() {
        // La local es la rica: trae su ruta sin pasar por la lista blanca del espejo.
        val feed = GroupFeed(
            sessions = listOf(mirrored("s1", OWNER, 3_000, route = null)),
            hiddenRouteAuthors = emptySet(),
        )

        val rows = feed.rowsWith(listOf(mine("s1", 3_000, route = "polyline_local")), CAR)

        assertEquals("polyline_local", rows.single().routePolyline)
    }

    @Test
    fun should_notCommentMyOwnRowAsWithheld_when_myRouteSwitchIsOff() {
        // Mi fila viene de la copia local, que SÍ dibuja la ruta: comentarla como «ruta no
        // compartida» se contradiría a sí misma. Que las mías no viajan lo dice el interruptor.
        val feed = GroupFeed(
            sessions = listOf(mirrored("s1", OWNER, 3_000, route = "polyline_mirror")),
            hiddenRouteAuthors = setOf(OWNER),
        )

        assertTrue(feed.hiddenRouteSessionIds(selfUid = OWNER).isEmpty())
    }

    @Test
    fun should_stillCommentSomebodyElsesRow_when_theirRouteSwitchIsOff() {
        val feed = GroupFeed(
            sessions = listOf(mirrored("g1", OTHER, 3_000, route = "polyline_mirror")),
            hiddenRouteAuthors = setOf(OTHER),
        )

        assertContentEquals(listOf("g1"), feed.hiddenRouteSessionIds(selfUid = OWNER).toList())
    }

    @Test
    fun should_hideSomebodyElsesGeometryButKeepTheirRow_when_theirRouteSwitchIsOff() {
        // La geometría no llega a la UI; la fila y su distancia sí — las cifras son del coche.
        val feed = GroupFeed(
            sessions = listOf(mirrored("g1", OTHER, 2_000, route = "polyline_mirror")),
            hiddenRouteAuthors = setOf(OTHER),
        )

        val row = feed.rowsWith(listOf(mine("s1", 3_000)), CAR).single { it.id == "g1" }

        assertNotNull(row)
        assertEquals(null, row.routePolyline)
    }

    @Test
    fun should_neverFallToEmpty_when_theGroupIsBeingDissolved() {
        // El parpadeo que vio el user al pulsar «dejar de compartir» (16-09): el espejo se vacía un
        // instante ANTES de que Room propague `sharedCarId = null`, así que el coche sigue contando
        // como compartido con un feed ya a cero. Con el reemplazo eso resolvía una lista vacía y la
        // pantalla anunciaba «aún no has aparcado» —no el skeleton, que sería «todavía no lo sé»—
        // sobre un historial de 15 sesiones. Con la unión ese instante no existe.
        // [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001]
        val dissolving = GroupFeed(sessions = emptyList(), hiddenRouteAuthors = emptySet())
        val past = List(15) { i -> mine("s$i", 15_000L - i) }

        val rows = dissolving.rowsWith(past, CAR)

        assertEquals(15, rows.size)
    }

    @Test
    fun should_showOnlyTheGroup_when_aNewMemberHasNoSessionsOfTheirOwn() {
        // Quien se UNE ve poco al principio, y es correcto: no ha conducido ese coche todavía.
        val feed = GroupFeed(
            sessions = listOf(mirrored("g1", OTHER, 2_000)),
            hiddenRouteAuthors = emptySet(),
        )

        val rows = feed.rowsWith(mine = emptyList(), vehicleId = CAR)

        assertContentEquals(listOf("g1"), rows.map { it.id })
        assertFalse(rows.isEmpty())
    }
}
