package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue

/**
 * Nothing that ships may see the mock graph: not the fakes, not the mock Koin modules, not the
 * mock entry points.
 *
 * ## Why this exists
 *
 * It is not a style rule. On 18-09-2026 the Release framework of TestFlight build 2 was inspected
 * with `nm`, and it exported **161 `Fake*` Objective-C classes** plus `MockScenario` — a fake auth
 * repository, a fake Firestore data source, a fake everything, inside the binary meant for the App
 * Store. The route in was `MockMainViewController` and `iosMockModule`, which lived in
 * `shared/src/iosMain`: Kotlin/Native exports the module's public API to `ComposeApp.h`, an
 * exported symbol is a dead-code-elimination ROOT, and that root held every fake alive through DCE
 * and LTO. No Swift ever called it. Dead code that shipped anyway.
 *
 * App Review reads that as **guideline 2.3.1, hidden functionality**, and has already rejected a
 * sibling app of this team for exactly it. The answer is not "gate it at runtime" — a runtime gate
 * leaves the code in the binary, which is the thing being judged. The answer is that production
 * cannot compile it, which is why the graph now lives in `:mockkit` and the dependency arrow points
 * `:mockkit → :shared` and never back.
 *
 * This guardrail is the witness that the arrow stays that way. A `mockImplementation` turned into
 * `implementation` by autocomplete, or one convenience import into `iosMain`, puts it all back.
 *
 * ## The population
 *
 * [GuardrailScope.shippedSourceFiles] — and it is the only rule here that reads `iosMain`, on
 * purpose: that is where the leak was. A rule phrased over commonMain + androidMain would have
 * watched this happen and stayed green, so the test asserts its own iOS coverage below rather than
 * trusting the population's floor to notice. [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 *
 * ## ⛔ Falsifying this needs `--rerun-tasks`
 *
 * Konsist reads the source tree at test time and those files are not declared inputs of the test
 * task, so moving an import changes no bytecode and Gradle skips the run. Same trap as every other
 * guardrail in this package.
 *
 * [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]
 */
class MockGraphGuardrailTest {

    @Test
    fun `no shipped source imports the fakes package`() {
        val offenders = GuardrailScope.shippedSourceFiles()
            .filter { file -> file.imports.any { it.name.startsWith(FAKES_PACKAGE) } }
            .map { it.path.substringAfterLast("/src/") }

        assertTrue(
            offenders.isEmpty(),
            "These files ship and import the fakes package ($FAKES_PACKAGE):\n" +
                offenders.joinToString("\n") { "  · $it" } +
                "\n\nThe fakes live in :mockkit so they cannot reach a store binary. If production " +
                "genuinely needs one of these types, it is not a fake — move it into :shared under " +
                "its real name. Do NOT add :mockkit to a production dependency: on iOS that alone " +
                "is enough to export it and pin it past DCE.",
        )
    }

    @Test
    fun `no shipped source names a mock graph entry point`() {
        val offenders = GuardrailScope.shippedSourceFiles()
            .map { file -> file to file.text.withoutComments() }
            .filter { (_, code) -> MOCK_ENTRY_POINTS.any { code.contains(it) } }
            .map { (file, code) ->
                val hit = MOCK_ENTRY_POINTS.first { code.contains(it) }
                "${file.path.substringAfterLast("/src/")} → $hit"
            }

        assertTrue(
            offenders.isEmpty(),
            "These files ship and name a mock entry point:\n" +
                offenders.joinToString("\n") { "  · $it" } +
                "\n\nThe iOS screenshot build reaches them through :mockkit and " +
                "-Ppaparcar.screenshots=true, never through a reference from shipped code.",
        )
    }

    /**
     * The witness. `iosMain` is the source set this rule exists for, so a population that stopped
     * selecting it would retire the rule silently — the two tests above would keep passing on a
     * list that simply no longer contains the place where the failure happened.
     */
    @Test
    fun `the population actually covers iosMain`() {
        val iosFiles = GuardrailScope.shippedSourceFiles()
            .filter { it.path.replace('\\', '/').contains("/src/iosMain/") }

        assertTrue(
            iosFiles.size >= IOS_MAIN_WITNESS_FLOOR,
            "[empty population] the shipped-sources population selected ${iosFiles.size} file(s) " +
                "from iosMain, expected at least $IOS_MAIN_WITNESS_FLOOR (measured 47).\n" +
                "iosMain is where MockMainViewController leaked into the App Store binary. If " +
                "Konsist has stopped scanning it, this guardrail is enforcing nothing on the exact " +
                "source set it was written for.",
        )
    }

    /**
     * Comments are stripped before the search, and the reason is a real first run: this rule went
     * red on `DetectionModule.kt`, whose KDoc explains that `domainModule` includes the detection
     * module rather than having each of the four Koin entry points name it — `MockMainViewController`
     * among them. That sentence is true, useful, and compiles to nothing. A guardrail that cannot
     * tell a reference from a mention teaches people to delete the explanation instead of the
     * dependency, which is the opposite of what this file is for.
     */
    private fun String.withoutComments(): String =
        replace(BLOCK_COMMENT, " ").replace(LINE_COMMENT, " ")

    private companion object {
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")

        const val FAKES_PACKAGE = "com.rndeveloper.paparcar.fakes"

        /** Named, not pattern-matched: a `Mock*` regex would also catch legitimate vocabulary. */
        val MOCK_ENTRY_POINTS = listOf("iosMockModule", "MockMainViewController", "mockModule")

        const val IOS_MAIN_WITNESS_FLOOR = 24 // measured 47
    }
}
