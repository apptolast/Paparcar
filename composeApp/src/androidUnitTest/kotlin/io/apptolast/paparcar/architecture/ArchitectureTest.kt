package io.apptolast.paparcar.architecture

import com.lemonappdev.konsist.api.Konsist
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
 */
class ArchitectureTest {

    private val scope = Konsist.scopeFromProject()

    @Test
    fun `presentation layer should not import from data layer`() {
        val violations = scope
            .files
            .filter { file ->
                file.packagee?.name?.startsWith("io.apptolast.paparcar.presentation") == true
            }
            .flatMap { file ->
                file.imports
                    .filter { it.name.startsWith("io.apptolast.paparcar.data.") }
                    .map { "${file.name}.kt → ${it.name}" }
            }
        assertTrue(violations.isEmpty(), buildViolationMessage("presentation → data", violations))
    }

    @Test
    fun `data layer should not import from presentation layer`() {
        val violations = scope
            .files
            .filter { file ->
                file.packagee?.name?.startsWith("io.apptolast.paparcar.data") == true
            }
            .flatMap { file ->
                file.imports
                    .filter { it.name.startsWith("io.apptolast.paparcar.presentation.") }
                    .map { "${file.name}.kt → ${it.name}" }
            }
        assertTrue(violations.isEmpty(), buildViolationMessage("data → presentation", violations))
    }

    @Test
    fun `domain layer should not import from data or presentation layers`() {
        val violations = scope
            .files
            .filter { file ->
                file.packagee?.name?.startsWith("io.apptolast.paparcar.domain") == true
            }
            .flatMap { file ->
                file.imports
                    .filter { imp ->
                        imp.name.startsWith("io.apptolast.paparcar.data.") ||
                            imp.name.startsWith("io.apptolast.paparcar.presentation.")
                    }
                    .map { "${file.name}.kt → ${it.name}" }
            }
        assertTrue(violations.isEmpty(), buildViolationMessage("domain → data/presentation", violations))
    }

    @Test
    fun `runBlocking should not be used in commonMain production sources`() {
        val violations = scope
            .files
            .filter { file ->
                val path = file.path
                path.contains("commonMain") &&
                    !path.contains("Test") &&
                    !path.contains("commonTest")
            }
            .filter { file ->
                file.imports.any { it.name == "kotlinx.coroutines.runBlocking" }
            }
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
    fun `UseCase classes should live inside the domain layer`() {
        val violations = scope
            .classes()
            .filter { it.name.endsWith("UseCase") }
            .filter { cls ->
                cls.packagee?.name?.startsWith("io.apptolast.paparcar.domain") == false
            }
            .map { "${it.name} in ${it.packagee?.name}" }
        assertTrue(violations.isEmpty(), buildViolationMessage("UseCase outside domain", violations))
    }

    private fun buildViolationMessage(rule: String, violations: List<String>): String =
        "[$rule] ${violations.size} violation(s):\n${violations.joinToString("\n")}"
}
