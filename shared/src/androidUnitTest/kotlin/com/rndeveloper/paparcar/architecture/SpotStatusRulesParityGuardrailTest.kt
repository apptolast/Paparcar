package com.rndeveloper.paparcar.architecture

import com.rndeveloper.paparcar.domain.model.SpotStatus
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Las dos listas blancas de `firestore.rules` son una COPIA de [SpotStatus], y aquí es donde se
 * atan. [SEC-A-RELEASING-SPOT-IS-DENIED-AT-THE-DOOR-001]
 *
 * ## Por qué existe
 *
 * `SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001` añadió `RELEASING` y no tocó las reglas. Como la
 * nominación es la PRIMERA escritura de su documento, entraba por `create` — y la regla
 * enumeraba `['CONFIRMED', 'PROVISIONAL']`. **No se publicó ni una plaza de embarque**, y no se
 * notó porque el denied es mudo: la escritura va por `ReportSpotWorker`, reintenta con backoff y
 * el propio worker la descarta al vencer su TTL en cola.
 *
 * No fue un descuido de enum: el test de reglas ya afirmaba que un `create` con `RETRACTED` falla
 * —la lista era deliberada—. Lo que faltaba era volver a preguntársela cuando apareciera un estado
 * que el cliente SÍ escribe. Un compilador no puede: son dos lenguajes y un literal de cadena.
 *
 * ## Qué comprueba, y qué no
 *
 * NO comprueba que la regla sea correcta ni que se aplique — eso lo EJECUTA
 * `firestore-rules-tests/` ([TEST-RULES-ARE-EXECUTED-NOT-READ-001]), y leer una regla en vez de
 * ejecutarla es justo el error que aquel ticket cerró. Esto comprueba lo único que se puede
 * comprobar desde Kotlin: que las dos copias del enum dicen lo mismo.
 *
 * Las dos puertas piden cosas distintas, y por eso son dos aserciones:
 *  - **create** — todos los estados MENOS [SpotStatus.RETRACTED]: una retractación sólo aterriza
 *    sobre un documento que ya existe, así que crear una es escribir una lápida.
 *  - **update** — los cuatro: por ahí pasan la promoción del embarque y el reintento del worker.
 *
 * Un quinto `SpotStatus` pone este test en rojo por las dos puertas hasta que su autor conteste.
 */
class SpotStatusRulesParityGuardrailTest {

    @Test
    fun `the spot rules enumerate exactly the statuses the client can write`() {
        val rules = File(repoRoot, RULES_PATH)
            .takeIf { it.isFile }
            ?.readText()
            ?: fail("Falta $RULES_PATH: sin él este guard no comprueba nada.")

        val spotsBlock = rules.substringAfter(SPOTS_MATCH, missingDelimiterValue = "")
            .substringBefore("\n    match /")
        assertTrue(
            spotsBlock.isNotBlank(),
            "[empty population] No se encontró el bloque `$SPOTS_MATCH` en $RULES_PATH. " +
                "Si el match cambió de forma, actualiza este selector: un guard que no encuentra " +
                "su población pasa siempre.",
        )

        assertEquals(
            SpotStatus.entries.map { it.name }.toSet() - SpotStatus.RETRACTED.name,
            spotsBlock.allowlistOf("create"),
            failure("create", "Una retractación NO se crea: sólo cae sobre un documento vivo."),
        )
        assertEquals(
            SpotStatus.entries.map { it.name }.toSet(),
            spotsBlock.allowlistOf("update"),
            failure("update", "Por aquí pasan la promoción del embarque y el reintento del worker."),
        )
    }

    /**
     * Los estados que la regla [door] deja escribir. Hay UNA lista por puerta y se busca dentro de
     * su `allow`, no en todo el bloque: dos listas idénticas por casualidad no pueden hacerse pasar
     * la una por la otra.
     */
    private fun String.allowlistOf(door: String): Set<String> {
        val clause = substringAfter("allow $door:", missingDelimiterValue = "")
            .substringBefore(';')
        val list = STATUS_LIST.find(clause)
            ?: fail(
                "[empty population] No hay lista de estados en `allow $door` de $RULES_PATH. " +
                    "Si la condición se movió, mueve este guard con ella.",
            )
        return QUOTED.findAll(list.groupValues[1]).map { it.groupValues[1] }.toSet()
    }

    private fun failure(door: String, why: String) = buildString {
        appendLine("La lista de `allow $door` de $RULES_PATH no coincide con SpotStatus.")
        appendLine(why)
        appendLine()
        appendLine("Un estado que falta aquí NO da error al usuario: la escritura se deniega, el")
        appendLine("worker reintenta y la plaza muere en la cola. Así se perdió RELEASING entera.")
        appendLine("Y al tocar la regla, añade su caso en firestore-rules-tests/test/spots.test.ts:")
        appendLine("esto sólo compara dos listas, no ejecuta ninguna.")
    }

    private companion object {
        const val RULES_PATH = "firestore.rules"
        const val SPOTS_MATCH = "match /spots/{spotId}"

        /** `status in ['A', 'B']` — el contenido entre corchetes. */
        val STATUS_LIST = Regex("""\.status\s+in\s+\[([^]]*)]""")
        val QUOTED = Regex("""'([A-Z_]+)'""")

        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root no encontrado desde ${File("").absolutePath}")
    }
}
