package com.rndeveloper.paparcar.architecture

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.spot.isSpotWorthAnAlert
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * «¿De qué plazas se avisa?» se decide en DOS lenguajes, y tienen que decir lo mismo.
 * [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001]
 *
 * El aviso de plaza sale por dos carriles con dos implementaciones que no pueden compartir código:
 *
 * - **El cliente**, en Kotlin: [isSpotWorthAnAlert], que sirve al stream vivo y al carril de cerca de ti.
 * - **El servidor**, en TypeScript: `functions/src/fanout.ts`, que decide a quién empujar.
 *
 * `fanout.ts` afirma la paridad creyéndola: *«Mismo veredicto que `ZoneSpotAlertPolicy` toma en el
 * cliente: son las dos mitades de la misma promesa y no pueden discrepar»*. **Y discrepaban.** Del
 * 23-09 al 28-09 el servidor anunciaba las `PROVISIONAL` (con `isProvisional: true` para que el copy
 * las matizara) y el cliente las vetaba, así que una plaza deducida te llegaba con la app CERRADA y se
 * perdía mientras mirabas el mapa: ganaba el canal peor. Nada lo comprobaba, porque un comentario que
 * promete paridad no es una comprobación.
 *
 * Esta clase es esa comprobación. No puede ejecutar el TypeScript, así que **lee sus vetos** —igual
 * que [WebSiteParityGuardrailTest] lee la web construida— y compara la lista con la que sale de
 * preguntarle al Kotlin por cada estado que existe.
 *
 * ⚠️ Lo que NO comprueba: que las PALABRAS coincidan. Que una plaza deducida se anuncie diciendo que
 * no está confirmada es cosa de `SpotStatus.isUnconfirmedOffer` y del copy, y eso lo cubren sus
 * propios tests. Aquí sólo se vigila el **silencio**: quién habla y quién no.
 *
 * ⛔ Lee ficheros y no toca bytecode, así que Gradle lo da por UP-TO-DATE: falsificarlo exige
 * `--rerun-tasks`, como todos los de este paquete.
 */
class AlertStatusParityGuardrailTest {

    @Test
    fun `should announce exactly the spot statuses the server announces`() {
        val serverVetoes = serverVetoedStatuses()

        assertTrue(
            serverVetoes.size >= SERVER_VETO_FLOOR,
            "sólo se encontraron ${serverVetoes.size} vetos en $FANOUT (${serverVetoes.joinToString()}), " +
                "y había al menos $SERVER_VETO_FLOOR. El parser dejó de reconocerlos: eso es esta clase " +
                "quedándose ciega, no el servidor volviéndose permisivo",
        )

        val serverAnnounces = SpotStatus.entries.toSet() - serverVetoes
        // ⚠️ `ownUserId = ME`, distinto del `reportedBy` de la plaza: la primera versión pasó `OTHER`
        // en los dos, así que TODA plaza parecía propia y el veto de plaza propia contestaba por el
        // estado. El fallo destapó un bug real de todas formas, pero por el motivo equivocado.
        val clientAnnounces = SpotStatus.entries.filter { isSpotWorthAnAlert(freshSpot(it), ME, NOW) }.toSet()

        assertEquals(
            serverAnnounces,
            clientAnnounces,
            "el cliente y el servidor no avisan de los mismos estados. Servidor: " +
                "${serverAnnounces.sorted()} · cliente: ${clientAnnounces.sorted()}. Uno de los dos tiene " +
                "que cambiar, y la decisión es de producto: gana el canal que llegue, no el que quede",
        )
    }

    /**
     * Los estados que el servidor se niega a anunciar, leídos de sus propias guardas.
     *
     * Reconoce la forma `if (spot.status === 'X') return { ok: false` — que es como está escrito cada
     * veto en `offer()`. Un veto escrito de otra forma NO se ve, y para eso está el suelo de arriba.
     */
    private fun serverVetoedStatuses(): Set<SpotStatus> =
        Regex("""spot\.status\s*===\s*'([A-Z_]+)'\s*\)\s*return\s*\{\s*ok:\s*false""")
            .findAll(read(FANOUT))
            .map { match ->
                val name = match.groupValues[1]
                SpotStatus.entries.firstOrNull { it.name == name }
                    ?: fail("$FANOUT veta '$name', que no es ningún SpotStatus: uno de los dos se renombró")
            }
            .toSet()

    /** Una plaza de otra persona, viva y en rango: lo único que debe decidir es su estado. */
    private fun freshSpot(status: SpotStatus) = Spot(
        id = "s-${status.name}",
        location = GpsPoint(40.0, -3.0, accuracy = 5f, timestamp = NOW - 1_000L, speed = 0f),
        reportedBy = OTHER,
        status = status,
        expiresAt = NOW + 60_000L,
    )

    private fun read(relative: String): String =
        File(repoRoot, relative).takeIf { it.isFile }?.readText()
            ?: fail("no encontrado desde ${repoRoot.path}: $relative")

    private companion object {
        const val FANOUT = "functions/src/fanout.ts"
        const val OTHER = "someone-else"
        const val ME = "me"
        const val NOW = 1_700_000_000_000L

        /** Medidos dos el 28-09 (`RETRACTED` y `RELEASING`); el suelo es ése, no una estimación. */
        const val SERVER_VETO_FLOOR = 2

        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root no encontrado desde ${File("").absolutePath}")
    }
}
