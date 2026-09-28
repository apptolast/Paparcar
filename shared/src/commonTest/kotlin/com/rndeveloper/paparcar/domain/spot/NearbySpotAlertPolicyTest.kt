package com.rndeveloper.paparcar.domain.spot

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El carril de «una plaza acaba de quedar libre donde estás».
 * [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
 */
class NearbySpotAlertPolicyTest {

    private val me = "me"
    private val anchor = GpsPoint(40.0, -3.0, accuracy = 5f, timestamp = 1_000L, speed = 0f)
    private val now = 10_000L

    /** [metresEast] convierte metros a grados de longitud a esta latitud, para colocar plazas a mano. */
    private fun spot(
        id: String,
        metresEast: Double,
        reportedBy: String = "someone-else",
        status: SpotStatus = SpotStatus.CONFIRMED,
        expiresAt: Long = 0L,
    ) = Spot(
        id = id,
        location = GpsPoint(
            latitude = 40.0,
            longitude = -3.0 + metresEast / 85_000.0,
            accuracy = 5f,
            timestamp = 1_000L,
            speed = 0f,
        ),
        reportedBy = reportedBy,
        status = status,
        expiresAt = expiresAt,
    )

    private fun look(
        spots: List<Spot>,
        told: Set<String> = emptySet(),
        hasBaseline: Boolean = true,
        lastAlertAtMs: Long? = null,
        nowMs: Long = now,
    ) = nearbySpotsWorthTelling(
        anchor = anchor,
        spots = spots,
        ownUserId = me,
        alreadyTold = told,
        hasBaseline = hasBaseline,
        lastAlertAtMs = lastAlertAtMs,
        nowMs = nowMs,
    )

    // ── El invariante que pidió el user, y el que más obviamente mordería aquí ────────────────────

    @Test
    fun should_neverAnnounce_when_theSpotIsTheOneTheUserJustLeft() {
        // Regla del user (27-09): una plaza que deja un usuario NO se le anuncia a ese usuario.
        // En este carril es el caso MÁS probable de todos: la plaza que acabas de liberar está a
        // metros de ti, así que sin este veto sería el primer aviso que vería cualquiera, y falso.
        val look = look(listOf(spot("mine", metresEast = 5.0, reportedBy = me)))

        assertNull(look.alert)
        assertTrue(look.toRecord.isEmpty(), "ni se anuncia ni se apunta: no es asunto de este carril")
    }

    @Test
    fun should_announceTheOthersSpot_when_itIsInsideTheCircle() {
        val look = look(listOf(spot("theirs", metresEast = 100.0)))

        assertEquals("theirs", look.alert?.id)
        assertEquals(listOf("theirs"), look.toRecord.map { it.id })
    }

    // ── El ancla es tu cuerpo, y el círculo es un círculo ────────────────────────────────────────

    @Test
    fun should_staySilent_when_theSpotIsFartherThanTheWalk() {
        assertNull(look(listOf(spot("far", metresEast = 800.0))).alert)
    }

    @Test
    fun should_notRecordTheFarSpot_when_itIsOutsideTheCircle() {
        // ⛔ Ésta es la regla que hace el carril de PROXIMIDAD y no de novedad: la plaza lejana no se
        // apunta, así que sigue siendo noticia cuando el usuario llegue a menos de 500 m. Apuntarla
        // habría sido más limpio y habría matado en silencio el caso para el que existe el carril:
        // dar vueltas buscando sitio.
        val far = spot("far", metresEast = 800.0)
        assertTrue(look(listOf(far)).toRecord.isEmpty())

        // Y al acercarse, la misma plaza sí habla.
        val near = spot("far", metresEast = 300.0)
        assertEquals("far", look(listOf(near)).alert?.id)
    }

    // ── La primera mirada mide el suelo, no lo cuenta ─────────────────────────────────────────────

    @Test
    fun should_recordWithoutAnnouncing_when_itIsTheFirstLook() {
        val look = look(
            spots = listOf(spot("a", metresEast = 100.0), spot("b", metresEast = 900.0)),
            hasBaseline = false,
        )

        assertNull(look.alert, "avisar de lo que ya está en el mapa enseña a descartar el banner")
        assertTrue(look.takingBaseline)
        assertEquals(
            listOf("a"),
            look.toRecord.map { it.id },
            "el suelo medido es el del CÍRCULO: la de 900 m no se apunta, o no podría avisar al acercarse",
        )
    }

    @Test
    fun should_notRepeat_when_theSpotWasAlreadyTold() {
        assertNull(look(listOf(spot("a", metresEast = 100.0)), told = setOf("a")).alert)
    }

    // ── Una sola plaza por aviso, y la más cercana ────────────────────────────────────────────────

    @Test
    fun should_announceTheNearest_when_severalAreFresh() {
        val look = look(
            listOf(
                spot("mid", metresEast = 300.0),
                spot("near", metresEast = 40.0),
                spot("edge", metresEast = 490.0),
            ),
        )

        assertEquals("near", look.alert?.id)
        assertEquals(
            listOf("near"),
            look.toRecord.map { it.id },
            "sólo se apunta la anunciada: las otras dos siguen siendo noticia en la siguiente mirada",
        )
    }

    // ── La pauta ORDENA, no se come los avisos ────────────────────────────────────────────────────

    @Test
    fun should_hold_when_itJustInterrupted() {
        val look = look(
            listOf(spot("a", metresEast = 100.0)),
            lastAlertAtMs = now - NEARBY_SPOT_ALERT_MIN_GAP_MS + 1,
        )

        assertNull(look.alert)
        assertTrue(
            look.toRecord.isEmpty(),
            "⛔ callada por la pauta NO es contada: si se apuntara, la pauta se comería la plaza en vez de encolarla",
        )
    }

    @Test
    fun should_announceTheHeldSpot_when_theGapHasPassed() {
        val look = look(
            listOf(spot("a", metresEast = 100.0)),
            lastAlertAtMs = now - NEARBY_SPOT_ALERT_MIN_GAP_MS,
        )

        assertEquals("a", look.alert?.id)
    }

    // ── Los estados que no son una plaza libre ────────────────────────────────────────────────────

    @Test
    fun should_staySilent_when_theSpaceIsNotFreeYetOrNotFreeAnyMore() {
        // ⛔ PROVISIONAL salió de esta lista el 28-09 y no por capricho: el servidor YA la anunciaba
        // y el puerto de notificación ya llevaba el flag para matizar la frase, así que vetarla aquí era
        // la contradicción. [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001]
        val states = listOf(SpotStatus.RELEASING, SpotStatus.RETRACTED)
        for (status in states) {
            assertNull(
                look(listOf(spot("s", metresEast = 50.0, status = status))).alert,
                "$status no es una plaza libre de la que avisar",
            )
        }
    }

    @Test
    fun should_announceTheDeducedSpot_when_nobodyMeasuredTheDeparture() {
        // Una plaza deducida SÍ interrumpe — es la más fresca que existe. Lo que cambia son las
        // palabras, no el silencio: la duda la lleva `SpotStatus.isUnconfirmedOffer` hasta el copy.
        // [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001]
        val provisional = spot("s-deduced", metresEast = 80.0, status = SpotStatus.PROVISIONAL)

        assertEquals("s-deduced", look(listOf(provisional)).alert?.id)
        assertTrue(
            provisional.status.isUnconfirmedOffer,
            "y quien la entregue tiene que poder saber que hay que decir que no está confirmada",
        )
    }

    @Test
    fun should_staySilent_when_theSpotExpiredBeforeWeLooked() {
        assertNull(look(listOf(spot("old", metresEast = 50.0, expiresAt = now - 1))).alert)
    }

    @Test
    fun should_staySilent_when_thereIsNothingAround() {
        val look = look(emptyList())
        assertNull(look.alert)
        assertTrue(look.toRecord.isEmpty())
    }
}
