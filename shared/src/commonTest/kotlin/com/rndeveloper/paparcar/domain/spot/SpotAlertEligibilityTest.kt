package com.rndeveloper.paparcar.domain.spot

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * El invariante que comparten los dos carriles del aviso: de qué plazas se puede hablar.
 * [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001]
 *
 * ⛔ **Enumera los estados con un `when` EXHAUSTIVO, y eso es el testigo.** El KDoc de
 * [isSpotWorthAnAlert] avisaba de que un estado nuevo se volvería alertable con sólo no decir nada —
 * y un aviso en un comentario no se ejecuta. Aquí, añadir un `SpotStatus` **no compila** hasta que
 * alguien decida qué hace el aviso con él.
 */
class SpotAlertEligibilityTest {

    /**
     * La decisión, estado por estado. Cambiar una de estas líneas es cambiar el producto, no el test.
     *
     * ⚠️ Tiene que seguir cuadrando con `functions/src/fanout.ts`, y de eso se encarga
     * `AlertStatusParityGuardrailTest`: aquí se declara, allí se compara con el otro lenguaje.
     */
    private fun mayWeInterrupt(status: SpotStatus): Boolean = when (status) {
        // Salida presenciada: la plaza es real.
        SpotStatus.CONFIRMED -> true
        // Salida DEDUCIDA: la más fresca que existe. Se avisa, con otras palabras.
        SpotStatus.PROVISIONAL -> true
        // El coche sigue dentro. Su noticia es la promoción, no ésta.
        SpotStatus.RELEASING -> false
        // La app dejó de creérsela.
        SpotStatus.RETRACTED -> false
    }

    @Test
    fun should_decideEveryStatusOnPurpose_when_askedAboutEachOne() {
        for (status in SpotStatus.entries) {
            assertEquals(
                mayWeInterrupt(status),
                isSpotWorthAnAlert(spot(status), ownUserId = ME, nowMs = NOW),
                "el aviso decidió lo contrario para $status",
            )
        }
    }

    // ── Los vetos que NO dependen del estado, y que un `-> true` mal puesto se salta ──────────────

    @Test
    fun should_neverAnnounceMyOwnSpot_whateverItsStatus() {
        // ⛔ Regresión con nombre propio: la primera versión de este ticket puso una rama
        // `PROVISIONAL -> true` ANTES del veto de plaza propia, y un `-> true` en una cadena de
        // guardas cortocircuita todo lo que va detrás — una plaza deducida TUYA se te habría
        // anunciado. Lo cazó el guard de paridad; esto lo fija.
        for (status in SpotStatus.entries) {
            assertFalse(
                isSpotWorthAnAlert(spot(status, reportedBy = ME), ownUserId = ME, nowMs = NOW),
                "$status: una plaza propia no es un aviso, en ningún estado",
            )
        }
    }

    @Test
    fun should_neverAnnounceAnExpiredSpot_whateverItsStatus() {
        for (status in SpotStatus.entries) {
            assertFalse(
                isSpotWorthAnAlert(spot(status, expiresAt = NOW - 1), ownUserId = ME, nowMs = NOW),
                "$status: una plaza ya caducada no es un aviso",
            )
        }
    }

    @Test
    fun should_treatOnlyTheDeducedOneAsUnconfirmed() {
        // La otra mitad de la decisión: quién habla (arriba) y quién tiene que matizar (aquí).
        assertEquals(
            setOf(SpotStatus.PROVISIONAL),
            SpotStatus.entries.filter { it.isUnconfirmedOffer }.toSet(),
            "⛔ RELEASING no es «sin confirmar»: no hay duda ninguna, el coche sigue dentro",
        )
    }

    private fun spot(
        status: SpotStatus,
        reportedBy: String = OTHER,
        expiresAt: Long = NOW + 60_000L,
    ) = Spot(
        id = "s-${status.name}",
        location = GpsPoint(40.0, -3.0, accuracy = 5f, timestamp = NOW - 1_000L, speed = 0f),
        reportedBy = reportedBy,
        status = status,
        expiresAt = expiresAt,
    )

    private companion object {
        const val ME = "me"
        const val OTHER = "someone-else"
        const val NOW = 1_700_000_000_000L
    }
}
