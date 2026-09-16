package com.rndeveloper.paparcar.architecture

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Las dos mitades de la política publicada dicen la MISMA promesa, o una de las dos miente.
 *
 * `hosting/public/privacy-policy.html` lleva el texto en inglés y en español en el mismo fichero,
 * `<section id="en">` y `<section id="es">`. Eso no es una traducción de marketing: es el documento
 * al que `SettingsViewModel.PRIVACY_POLICY_URL` manda al usuario y con el que el formulario de Data
 * Safety tiene que coincidir. Una mitad que se queda atrás no rompe nada — se lee perfectamente, y
 * lo que promete ya no es lo que hace la app.
 *
 * Y quedarse atrás es el caso NORMAL, no el raro: cada cambio hay que escribirlo dos veces a mano,
 * y en la tanda del 16-09 fueron nueve sitios (§2.5, §2.8, §2.9 nueva, la tabla de terceros, §5,
 * §6, §8…). La revisión que se hizo entonces fue mirar la paridad a ojo. Esto es esa mirada, pero
 * que no se olvida.
 *
 * ## Qué compara, y por qué no compara el texto
 *
 * No puede comparar contenido: son idiomas distintos. Compara el ESQUELETO, que es lo que se
 * desincroniza cuando alguien añade un párrafo en una mitad y no en la otra:
 *
 *  - el índice de epígrafes (`1`, `2`, `2.1`… `2.9`, `3`… `10`) y su orden
 *  - cuántos puntos de lista cuelgan de cada epígrafe
 *  - cuántas filas tiene la tabla de «con quién se comparten»
 *
 * Un cambio que sí sea solo de redacción pasa sin ruido. Uno que añada, quite o reordene, no.
 *
 * ⛔ Si algún día se decide publicar la política SOLO en inglés —defendible: el navegador traduce, y
 * hoy los otros siete idiomas de la app ya leen la inglesa— este test no se borra sin más: se
 * cambia por uno que compruebe que la mitad española **no existe**, porque media traducción
 * abandonada es peor que ninguna. [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 */
class PrivacyPolicyParityGuardrailTest {

    @Test
    fun `the published policy says the same thing in both halves`() {
        val html = policyFile.readText()

        val en = section(html, "en")
        val es = section(html, "es")

        val enOutline = outline(en)
        val esOutline = outline(es)

        // El testigo: si el marcado cambia y los selectores dejan de encontrar nada, esto cae en
        // vez de dar por buena una comparación entre dos listas vacías.
        assertTrue(
            enOutline.size >= HEADING_FLOOR,
            "[empty population] la mitad inglesa dio ${enOutline.size} epígrafes, esperaba al menos " +
                "$HEADING_FLOOR. El selector dejó de encontrarlos: esta regla ya no comprueba nada.",
        )

        assertEquals(
            enOutline.map { it.number },
            esOutline.map { it.number },
            "El índice de epígrafes no coincide entre las dos mitades. Falta (o sobra) una sección " +
                "en una de las dos, y el usuario que lea esa mitad no verá lo que promete la otra.",
        )

        val mismatched = enOutline.zip(esOutline)
            .filter { (a, b) -> a.bullets != b.bullets }
            .map { (a, b) -> "  §${a.number} — EN ${a.bullets} puntos, ES ${b.bullets}" }
        assertTrue(
            mismatched.isEmpty(),
            buildString {
                appendLine("Estas secciones tienen distinto número de puntos en cada idioma:")
                mismatched.forEach { appendLine(it) }
                appendLine()
                appendLine("Casi siempre significa que un cambio se escribió en una mitad y no en la")
                appendLine("otra. Si el desajuste es legítimo (una aclaración que solo aplica a un")
                appendLine("idioma), parte el punto en dos o únelos para que el esqueleto cuadre.")
            },
        )

        assertEquals(
            rows(en),
            rows(es),
            "La tabla de «con quién se comparten los datos» tiene distinto número de filas en cada " +
                "mitad. Un destinatario nombrado solo en un idioma es justo el fallo que esto busca.",
        )
    }

    private data class Heading(val number: String, val bullets: Int)

    private fun section(html: String, id: String): String =
        Regex("""<section id="$id"[^>]*>(.*?)</section>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)
            ?.groupValues
            ?.get(1)
            ?: fail(
                "No se encontró <section id=\"$id\"> en ${policyFile.path}. Si la política se " +
                    "reorganizó, actualiza este guard con ella; si se retiró un idioma, lee el " +
                    "⛔ del KDoc antes de borrar el test.",
            )

    /** Epígrafes en orden, cada uno con los puntos de lista que cuelgan de él hasta el siguiente. */
    private fun outline(section: String): List<Heading> {
        val heading = Regex("""<h([23])[^>]*>\s*(\d+(?:\.\d+)?)""")
        val marks = heading.findAll(section).toList()
        return marks.mapIndexed { i, m ->
            val to = marks.getOrNull(i + 1)?.range?.first ?: section.length
            Heading(
                number = m.groupValues[2],
                bullets = Regex("<li[ >]").findAll(section.substring(m.range.first, to)).count(),
            )
        }
    }

    private fun rows(section: String): Int = Regex("<tr[ >]").findAll(section).count()

    private companion object {
        /** Medido 21 epígrafes por idioma el 16-09-2026; el suelo vive a la mitad, como los demás. */
        const val HEADING_FLOOR = 10

        val policyFile: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?.let { File(it, "hosting/public/privacy-policy.html") }
            ?.takeIf { it.isFile }
            ?: error("privacy-policy.html no encontrada desde ${File("").absolutePath}")
    }
}
