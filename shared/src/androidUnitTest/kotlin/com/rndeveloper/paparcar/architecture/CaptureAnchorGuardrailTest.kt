package com.rndeveloper.paparcar.architecture

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The texts `shoot.sh` hunts for on screen are a CONTRACT with the mock world, and it broke in
 * silence.
 *
 * The capture session finds its targets by their on-screen label — the parked car's address, a
 * forecourt name, a car-park row — because a fixed tap coordinate is a coin flip that fails without
 * saying so. That makes those literals a dependency on `:mockkit`'s fixtures, and nothing enforced
 * it: when [MOCK-THE-DEMO-WORLD-MOVES-TO-SPAIN-001] moved the demo world to Madrid, **two of the
 * three anchors kept pointing at Dublin**. `"Camden Street, 21"` and `"Northgate Fuel"` no longer
 * existed anywhere, and the only consequence was a `WARN` on a script nobody had run since. It was
 * found a month later, by reading the script while planning a re-shoot.
 *
 * A missing anchor used to print a warning and let the session continue with a wrong crop.
 * The capture and render scripts now fail on a missing anchor; this guard also catches a renamed
 * fixture before a session is started.
 *
 * What this checks is the only half that is mechanically knowable: **every literal the script
 * searches for still exists in the fixtures**. It cannot check that the row is on screen, or that
 * it has a row below it for the two-row crop — that is what running the session is for.
 *
 * ⛔ Konsist-style guards go UP-TO-DATE when no bytecode changed, and none of this is bytecode.
 * Falsify it with
 * `./gradlew :shared:testDebugUnitTest --tests '*CaptureAnchor*' --rerun-tasks`.
 *
 * [PLAY-SHOTS-THE-CAPTURE-ANCHORS-DIED-IN-THE-MOVE-001]
 */
class CaptureAnchorGuardrailTest {

    @Test
    fun `every on-screen text the capture session hunts for exists in the mock world`() {
        val script = read(SHOOT)
        val anchors = anchorsOf(script)

        assertTrue(
            anchors.size >= EXPECTED_AT_LEAST,
            "only ${anchors.size} anchor(s) parsed out of $SHOOT — expected at least " +
                "$EXPECTED_AT_LEAST. The script was reorganised and this guard went blind; fix " +
                "the parser rather than lowering the number.",
        )

        val fixtures = fixtureText()
        val dead = anchors.filterNot { fixtures.contains(it) }

        assertTrue(
            dead.isEmpty(),
            buildString {
                appendLine("$SHOOT hunts for text that no fixture in :mockkit provides:")
                dead.forEach { appendLine("  \"$it\"") }
                appendLine()
                appendLine("A missing anchor will stop the session instead of producing a wrong crop.")
                appendLine("Update the fixture and the capture script together before shooting.")
                appendLine("Either the fixture was renamed and the script was not, or the other")
                appendLine("way round. [PLAY-SHOTS-THE-CAPTURE-ANCHORS-DIED-IN-THE-MOVE-001]")
            },
        )
    }

    // ── plumbing ──────────────────────────────────────────────────────────────

    /**
     * The quoted literals passed to `anchor` and `tap_text`.
     *
     * Skips what is not fixture text: a `$variable`, bare numerals, and `@` selectors. The latter
     * are resolved by uiquery from localized Compose resources rather than the mock fixtures.
     *
     * ⛔ NOT anchored to the start of a line, and that is not laziness: the car-park anchor is
     * written `select_lane "4" && tap_text "…"`, mid-line. The first draft of this parser required
     * the command to open its line, found 2 of 3, and the count check above is what caught it —
     * which is the whole reason that check exists.
     */
    private fun anchorsOf(script: String): List<String> =
        Regex("""(?:anchor\s+\w+|tap_text)\s+"([^"]+)"""")
            .findAll(script)
            .map { it.groupValues[1] }
            .filterNot { it.startsWith("$") || it.startsWith("@") || it.all(Char::isDigit) }
            .distinct()
            .toList()

    /** Every fixture source of the mock world, concatenated. */
    private fun fixtureText(): String {
        val root = repoFile(FIXTURES)
        if (!root.isDirectory) fail("not found from ${repoRoot.path}: $FIXTURES")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
    }

    private fun read(relative: String): String =
        repoFile(relative).takeIf { it.isFile }?.readText()
            ?: fail("not found from ${repoRoot.path}: $relative")

    private fun repoFile(relative: String) = File(repoRoot, relative)

    private companion object {
        const val SHOOT = "docs/release/play-listing/tools/shoot.sh"
        const val FIXTURES = "mockkit/src/commonMain/kotlin/com/rndeveloper/paparcar/fakes"

        /**
         * The three that exist today: the parked address, a forecourt and a car park. A number,
         * not an exact count, so adding a fourth anchor does not fail the build — but deleting the
         * parser's grip on the script does.
         */
        const val EXPECTED_AT_LEAST = 3

        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root not found from ${File("").absolutePath}")
    }
}
