package com.rndeveloper.paparcar.architecture

import com.rndeveloper.paparcar.domain.detection.DetectionPath
import org.junit.Test
import kotlin.test.assertTrue

/**
 * [DET-GUARDRAILS-KEEP-THE-DOCTRINE-001] Piece 7 of the detection redesign: **without this, the six
 * pieces before it last until the next fix in a hurry.**
 *
 * Every rule here is here because it caught something real during the 2026-08-30 session, and each
 * one names its case. That is the bar: a guardrail whose violation nobody has ever committed is a
 * rule nobody will understand when it fires.
 *
 * ⚠️ What this file deliberately does NOT claim. The redesign's Piece 7 asks for a test that
 * "enumerates every route ending in `ConfirmParkingUseCase` and asserts none is reachable with
 * `DrivingEvidence` other than `Measured`". That property is **false as stated** — `manual`,
 * `inherited_drive` and `verified_speed` may confirm in silence without this session measuring a
 * drive, each for a reason written down in `ArmEvidence`, and they are supposed to. The true rule is
 * *silent confirm requires `Measured` OR an arm that carries its own measurement*, which is a
 * property of one expression and is unit-tested where it lives, not a reachability claim a static
 * scan can make. Writing the false version as a green test would be worse than having none.
 */
class DetectionDoctrineGuardrailTest {

    /**
     * The populations come from [GuardrailScope], which refuses to return an empty one. All three
     * rules below are prohibitions over a filtered set, so all three read the same green whether
     * production is clean or the selector stopped matching — and this file landed after the tree had
     * already been renamed once. [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    private val sourceFiles get() = GuardrailScope.productionSourceFiles()

    private val domainFiles get() = GuardrailScope.domainProductionFiles()

    /**
     * Source with comments stripped. Every one of these rules documents the shape it forbids by
     * QUOTING it, so scanning raw text makes each rule flag its own explanation — the first run of
     * this file reported `ParkingDetectionSource` and `EvaluateParkingDecisionUseCase` for code that
     * lives only inside the comment saying why it was removed.
     */
    private fun String.withoutComments(): String =
        replace(BLOCK_COMMENT_REGEX, "").replace(LINE_COMMENT_REGEX, "")

    /**
     * **A path literal must be a path that exists.**
     *
     * Field evidence, all three from one day: `UserParking`'s KDoc, `FakeUserParkingRepository` and
     * the preview data all carried `"vehicle-exit"`, a provenance production has never once
     * written (it writes `"vehicleExit+window+egress"`); and the Dev Catalog's "Assisted" variant
     * was built from `"steps=3 kinematicFixes=7"`, diagnostic jargon that never reaches
     * `detectionPath` at all — so the mock gallery claimed one strategy and would have rendered
     * another. Nothing could catch any of it, because the set of paths was stated nowhere.
     */
    @Test
    fun `every detectionPath literal is a declared DetectionPath`() {
        val violations = sourceFiles
            .filter { it.name != "DetectionPath" }
            .flatMap { file ->
                PATH_ASSIGNMENT_REGEX.findAll(file.text.withoutComments())
                    .map { it.groupValues[2] }
                    .filter { DetectionPath.ofLabel(it) == null }
                    .map { "${file.name}.kt → \"$it\"" }
                    .toList()
            }
        assertTrue(
            violations.isEmpty(),
            "[a detectionPath/pathLabel literal that DetectionPath does not declare — the app can " +
                "never write it, so anything reading it will disagree with production] " +
                "${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it" }}",
        )
    }

    /**
     * **The strength of an arm is not re-derived from a hand-kept set of strings.**
     *
     * That shape fails OPEN: the set enumerates the arms someone was already burned by, so every new
     * arm is strong by default until the day it produces its own field false positive. Field
     * 2026-08-29 23:56 is the day it did — an `enter_at_car` arm that was simply not on the list
     * silently pinned "La Parafarmacia" at reliability 0.9 with the user on foot.
     * `ArmEvidence.confirmsSilentlyWithoutMeasuredDrive` replaced it with a `when` over the sealed
     * hierarchy, which a new arm cannot skip. [DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001]
     */
    @Test
    fun `no hand-kept set of arm labels decides anything`() {
        val violations = domainFiles
            .filter { it.name != "ArmEvidence" }
            .filter { LABEL_SET_REGEX.containsMatchIn(it.text.withoutComments()) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            "[a setOf(LABEL_…) deciding arm strength — it fails OPEN, so the newest arm is the " +
                "strongest until it burns us. Ask the sealed type instead] " +
                "${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it.kt" }}",
        )
    }

    /**
     * **A strategy is not decided by a string prefix.**
     *
     * `parkingDetectionSourceOf` used `detectionPath.startsWith("bt")`: two characters deciding
     * which of the two independent detection strategies the user is told placed a pin, in the very
     * field they open when a pin looks wrong. [DET-DETECTION-PATH-IS-A-TYPE-001]
     */
    @Test
    fun `no detection decision is made by string prefix on a path or an arm`() {
        val violations = domainFiles
            .flatMap { file ->
                PREFIX_DECISION_REGEX.findAll(file.text.withoutComments())
                    .map { "${file.name}.kt → ${it.value.trim()}" }
                    .toList()
            }
        assertTrue(
            violations.isEmpty(),
            "[a detectionPath/armEvidence classified by startsWith — declare the membership on the " +
                "type] ${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it" }}",
        )
    }

    private companion object {
        /**
         * `detectionPath = "…"` / `pathLabel = "…"`, the two names the persisted provenance uses.
         *
         * The negative lookahead skips a literal that is only the FIRST PIECE of a concatenation:
         * `RecordPromptShown` builds `"low_medium(" + … + ")"`, and that `pathLabel` is a diagnostic
         * note about why a prompt appeared, not a provenance that ever reaches `UserParking`. The
         * name is overloaded — the persisted one is what this rule is about.
         */
        val PATH_ASSIGNMENT_REGEX =
            Regex("""\b(detectionPath|pathLabel)\s*=\s*"([^"$]+)"(?!\s*\+)""")

        val BLOCK_COMMENT_REGEX = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT_REGEX = Regex("""//[^\n]*""")

        /** A `setOf(` whose contents mention an arm-evidence label constant. */
        val LABEL_SET_REGEX = Regex("""setOf\([^)]*LABEL_[A-Z_]+[^)]*\)""", RegexOption.DOT_MATCHES_ALL)

        /** `detectionPath.startsWith(` / `armEvidence.startsWith(` and their local aliases. */
        val PREFIX_DECISION_REGEX =
            Regex("""\b(detectionPath|armEvidence|pathLabel)\??\.startsWith\(""")
    }
}
