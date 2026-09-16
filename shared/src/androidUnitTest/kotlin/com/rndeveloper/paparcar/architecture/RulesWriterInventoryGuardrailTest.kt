package com.rndeveloper.paparcar.architecture

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Cada escritura a Firestore está inventariada junto al test de reglas que la cubre.
 * [TEST-RULES-ARE-EXECUTED-NOT-READ-001]
 *
 * ## Por qué existe
 *
 * En septiembre hubo **tres** cambios de `firestore.rules` verificados leyendo el código contra la
 * regla, y **dos trajeron regresiones** que solo aparecieron en la revisión siguiente: disolver un
 * grupo dejó de poder borrar `state/current` —la última posición del coche quedaba huérfana— y
 * expulsar a alguien dejó de quitarle el nombre de la tarjeta, justo lo que el commit anterior
 * acababa de arreglar. Las dos fallaban **en silencio**, porque las dos escrituras van envueltas en
 * `runCatching`.
 *
 * Ahora las reglas se ejecutan (`firestore-rules-tests/`). Lo que este guard añade es lo otro que
 * faltaba: que **nadie añada una escritura nueva sin decir quién la prueba**. Una suite de reglas
 * que no crece con el código es la misma trampa un año después.
 *
 * ## Qué comprueba, y qué no
 *
 * No comprueba que el test exista ni que pase — eso lo hace `npm --prefix firestore-rules-tests
 * test`. Comprueba que la escritura **está nombrada** en `WRITERS.md`, que es la lista que un
 * humano tiene que mirar al cambiar una regla. Es deliberadamente tonto: su valor está en que
 * salta al añadir la línea, no en juzgar si el test es bueno.
 */
class RulesWriterInventoryGuardrailTest {

    @Test
    fun `every firestore writer is named in the rules-test inventory`() {
        val inventory = File(repoRoot, "firestore-rules-tests/WRITERS.md")
            .takeIf { it.isFile }
            ?.readText()
            ?: fail("Falta firestore-rules-tests/WRITERS.md: es la lista que sostiene esta regla.")

        val missing = WRITER_FILES.flatMap { relative ->
            val file = File(repoRoot, relative)
            if (!file.isFile) {
                fail(
                    "[empty population] $relative no existe. Si el fichero se movió, mueve también " +
                        "esta lista: un guard que apunta a la nada no comprueba nada.",
                )
            }
            val writes = WRITE_CALL.findAll(file.readText()).count()
            assertTrue(
                writes >= MIN_WRITES_PER_FILE,
                "[empty population] $relative tiene $writes escrituras, esperaba al menos " +
                    "$MIN_WRITES_PER_FILE. El selector dejó de encontrarlas.",
            )
            // El inventario se organiza por FICHERO: basta con que la sección exista y cite el
            // nombre. Exigir línea a línea convertiría cada refactor en una edición de tabla.
            if (inventory.contains(file.name)) emptyList() else listOf(relative)
        }

        assertTrue(
            missing.isEmpty(),
            buildString {
                appendLine("Estos ficheros escriben en Firestore y no aparecen en WRITERS.md:")
                missing.forEach { appendLine("  · $it") }
                appendLine()
                appendLine("Añádelos con el test de `firestore-rules-tests/` que cubre cada escritura.")
                appendLine("Si una escritura nueva NO tiene test, escríbelo: esa es la deuda que este")
                appendLine("guard existe para impedir que se acumule otra vez.")
            },
        )
    }

    private companion object {
        val WRITER_FILES = listOf(
            "shared/src/commonMain/kotlin/com/rndeveloper/paparcar/data/datasource/remote/RemoteSharedCarDataSourceImpl.kt",
            "shared/src/commonMain/kotlin/com/rndeveloper/paparcar/data/datasource/remote/FirebaseDataSourceImpl.kt",
            "shared/src/commonMain/kotlin/com/rndeveloper/paparcar/data/repository/FirestoreZoneAlertSubscriptionRepository.kt",
        )

        val WRITE_CALL = Regex("""\.(set|update|delete)\(""")

        /** Medido el 16-09-2026: 23 · 5 · 2. El suelo vive muy por debajo, como el resto. */
        const val MIN_WRITES_PER_FILE = 2

        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root no encontrado desde ${File("").absolutePath}")
    }
}
