package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue

/**
 * Layering rules enforced at compile time via Konsist.
 *
 * Rules (per ARCH-002 recommendation):
 *  1. presentation → data imports are forbidden.
 *  2. data → presentation imports are forbidden.
 *  3. domain → data or presentation imports are forbidden.
 *  4. runBlocking is banned in commonMain non-test sources (use suspend instead).
 *  5. UseCase classes must live in the domain layer.
 *  6. [AUDIT-ARCH-001 M12] domain must be PLATFORM-PURE: no android/androidx/java/javax imports,
 *     so it compiles unchanged on iOS. (Napier — io.github.aakira.napier — is KMP and allowed.)
 *
 * Every rule here reads its population from [GuardrailScope], which refuses to hand back an empty
 * one. Six rules of the form "there are no violations in package X" are six rules that a rename of
 * X would satisfy for free — and this file was written before the package was renamed and the
 * modules were split. [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 */
class ArchitectureTest {

    @Test
    fun `presentation layer should not import from data layer`() {
        val violations = GuardrailScope
            .filesInPackage(GuardrailScope.PRESENTATION, GuardrailScope.PRESENTATION_PACKAGE_FLOOR)
            .flatMap { file ->
                file.imports
                    .filter { it.name.startsWith("com.rndeveloper.paparcar.data.") }
                    .map { "${file.name}.kt → ${it.name}" }
            }
        assertTrue(violations.isEmpty(), buildViolationMessage("presentation → data", violations))
    }

    @Test
    fun `data layer should not import from presentation layer`() {
        val violations = GuardrailScope
            .filesInPackage(GuardrailScope.DATA, GuardrailScope.DATA_PACKAGE_FLOOR)
            .flatMap { file ->
                file.imports
                    .filter { it.name.startsWith("com.rndeveloper.paparcar.presentation.") }
                    .map { "${file.name}.kt → ${it.name}" }
            }
        assertTrue(violations.isEmpty(), buildViolationMessage("data → presentation", violations))
    }

    @Test
    fun `domain layer should not import from data or presentation layers`() {
        val violations = GuardrailScope
            .filesInPackage(GuardrailScope.DOMAIN, GuardrailScope.DOMAIN_PACKAGE_FLOOR)
            .flatMap { file ->
                file.imports
                    .filter { imp ->
                        imp.name.startsWith("com.rndeveloper.paparcar.data.") ||
                            imp.name.startsWith("com.rndeveloper.paparcar.presentation.")
                    }
                    .map { "${file.name}.kt → ${it.name}" }
            }
        assertTrue(violations.isEmpty(), buildViolationMessage("domain → data/presentation", violations))
    }

    @Test
    fun `runBlocking should not be used in commonMain production sources`() {
        val violations = GuardrailScope
            .commonMainProductionFiles()
            .filter { file -> file.imports.any { it.name == "kotlinx.coroutines.runBlocking" } }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            buildViolationMessage(
                "runBlocking in commonMain (use suspend instead)",
                violations,
            ),
        )
    }

    @Test
    fun `domain layer should be platform-pure (no android or jvm imports)`() {
        val forbiddenPrefixes = listOf("android.", "androidx.", "java.", "javax.")
        val violations = GuardrailScope
            .filesInPackage(GuardrailScope.DOMAIN, GuardrailScope.DOMAIN_PACKAGE_FLOOR)
            .flatMap { file ->
                file.imports
                    .filter { imp -> forbiddenPrefixes.any { imp.name.startsWith(it) } }
                    .map { "${file.name}.kt → ${it.name}" }
            }
        assertTrue(violations.isEmpty(), buildViolationMessage("domain platform-purity (android/jvm import in domain)", violations))
    }

    /**
     * The witness here is doubled on purpose. `allClasses()` proves the scan saw the project, and
     * the second assert proves the project still HAS use cases: a rule reading "no UseCase lives
     * outside domain" is equally satisfied by perfect discipline and by there being no use case
     * left to misplace, and only one of those is worth a green test.
     */
    @Test
    fun `UseCase classes should live inside the domain layer`() {
        val useCases = GuardrailScope.allClasses().filter { it.name.endsWith("UseCase") }
        assertTrue(
            useCases.size >= MIN_USE_CASES,
            "[empty population] only ${useCases.size} *UseCase class(es) found, expected at least " +
                "$MIN_USE_CASES — the naming convention this rule keys on has changed, so the rule " +
                "is no longer looking at anything.",
        )
        val violations = useCases
            .filter { cls -> cls.packagee?.name?.startsWith(GuardrailScope.DOMAIN) == false }
            .map { "${it.name} in ${it.packagee?.name}" }
        assertTrue(violations.isEmpty(), buildViolationMessage("UseCase outside domain", violations))
    }

    private fun buildViolationMessage(rule: String, violations: List<String>): String =
        "[$rule] ${violations.size} violation(s):\n${violations.joinToString("\n")}"

    private companion object {
        /** Measured 46 on master; half, per the floor policy in [GuardrailScope]. */
        const val MIN_USE_CASES = 23
    }
}
