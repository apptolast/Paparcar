package com.rndeveloper.paparcar.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import kotlin.test.fail

/**
 * The populations the guardrails filter, declared once and **never allowed to come back empty**.
 *
 * ## Why this exists
 *
 * Every prohibition guardrail in this package has the same shape:
 *
 * ```kotlin
 * val violations = scope.files.filter { …the population… }.filter { …the offence… }
 * assertTrue(violations.isEmpty())
 * ```
 *
 * and therefore the same blind spot: *no violations* and *nowhere to look* produce the identical
 * green. Move a package, split a module, rename a folder, and the first filter selects nothing —
 * the second one has nothing to reject, the assert passes, and the rule is retired without anybody
 * deciding to retire it. The check does not fail; it stops existing.
 *
 * That is not hypothetical here. This tree has moved wholesale twice since these guardrails were
 * written (`ARCH-HEALTH-001` split `:app`/`:shared` and renamed the root package;
 * `DET-PACKAGE-CLUSTERS-001` reorganised the detection packages), and neither move would have
 * turned a single one of them red on its way through.
 *
 * It is also a lesson this project has already paid for three times:
 * `I18N-A-DEAD-KEY-PASSES-EVERY-PARITY-CHECK-001` (a key nobody used passed every parity check),
 * `DET-THREE-EDGE-MARKERS-CANNOT-GO-SILENT-001` ("`any { … }` is not a witness"), and
 * `UI-TYPE-SYSTEM-HYGIENE-001` (an allowlist covering a component that no screen rendered — "not an
 * exception, a hole"). `StagePurityGuardrailTest` even states it outright: *a guardrail that only
 * forbids is one refactor away from being satisfied by an empty package*.
 *
 * ## The rule
 *
 * A guardrail does not get to name its own population inline. It asks for one here, and asking is
 * what carries the witness: [population] refuses to hand back a selection smaller than the floor
 * the caller declared, naming the population and the two numbers. So the guardrail author cannot
 * forget the witness — there is no code path that skips it.
 *
 * ## On the floors
 *
 * A floor is not a census, and it must never be maintained as one: pinning today's exact count
 * turns every ordinary file deletion into a red build, which teaches people to edit the number
 * without reading it. The floors below sit at roughly **half** of the measured population, which is
 * far enough under normal churn to stay quiet and far enough over zero to catch the failure this
 * class exists for — a selector that stopped matching drops to 0 or near it, never to half.
 *
 * If a floor ever does trip on legitimate shrinkage, the fix is to look at why the population
 * halved and then move the number deliberately. That is the conversation the floor is for.
 *
 * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 */
object GuardrailScope {

    /**
     * One scan for the whole guardrail suite. `scopeFromProject()` reaches both modules — the
     * `:app` shell included, which `PromptWindowGuardrailTest` depends on to find
     * `AppNotificationManagerImpl`.
     */
    val scope: KoScope = Konsist.scopeFromProject()

    /**
     * Selects a population and refuses to return it if it came back below [floor].
     *
     * The failure message is written for the person who will meet it: it says which population
     * vanished and what the guardrail using it has silently stopped checking, because that is the
     * thing that is easy to miss when the build goes red for what looks like an unrelated rename.
     */
    fun population(
        name: String,
        floor: Int,
        select: (KoScope) -> List<KoFileDeclaration>,
    ): List<KoFileDeclaration> {
        requireRealFloor(name, floor)
        val files = select(scope)
        if (files.size < floor) {
            fail(
                "[empty population] '$name' selected ${files.size} file(s), expected at least $floor.\n" +
                    "This is not a violation — it is the guardrail losing sight of what it guards. " +
                    "Something the selector names (a package, a path, a source set) has been moved, " +
                    "renamed or split, so every rule built on this population is now passing on an " +
                    "empty list and enforcing nothing.\n" +
                    "Fix the selector in GuardrailScope so it follows the code. Do NOT lower the " +
                    "floor to make this green: the floor sits at about half the real population " +
                    "precisely so that ordinary churn never reaches it.",
            )
        }
        return files
    }

    /** Same shape as [population], for the guardrails that reason over classes instead of files. */
    fun classPopulation(
        name: String,
        floor: Int,
        select: (KoScope) -> List<KoClassDeclaration>,
    ): List<KoClassDeclaration> {
        requireRealFloor(name, floor)
        val classes = select(scope)
        if (classes.size < floor) {
            fail(
                "[empty population] '$name' selected ${classes.size} class(es), expected at least " +
                    "$floor. See GuardrailScope.population — the rule built on this is now enforcing " +
                    "nothing.",
            )
        }
        return classes
    }

    /**
     * A floor of zero passes for any selection, including the empty one, so declaring one is
     * exactly the mistake this class exists to prevent — with the extra cost of looking like it
     * had been prevented. It is refused at the door rather than trusted to reviewers.
     */
    private fun requireRealFloor(name: String, floor: Int) {
        if (floor < 1) {
            fail(
                "[floor of $floor for '$name'] a floor below 1 is satisfied by the empty selection, " +
                    "which is the exact failure GuardrailScope exists to catch. Declare the real " +
                    "minimum you expect this population to have.",
            )
        }
    }

    // ── The shared populations ────────────────────────────────────────────────────────────────

    /**
     * **Feature UI**: the shared runtime surface that the colour, type and divider doctrines govern
     * — `presentation.*` plus `ui.components.*`, in `commonMain` only (androidMain `@Preview`
     * exploration files are dev tooling, not shipped UI).
     *
     * Three guardrails used to carry their own copy of this filter, one of them repeated four times
     * inside a single file. Three definitions of one population is how a population quietly stops
     * meaning the same thing in the three places that claim to share it.
     */
    fun featureFiles(): List<KoFileDeclaration> = population(
        name = "feature UI files (commonMain, presentation.* + ui.components.*)",
        floor = FEATURE_FILES_FLOOR,
    ) { scope ->
        scope.files
            .filter { it.path.contains("commonMain") }
            .filter { file ->
                val pkg = file.packagee?.name ?: ""
                pkg.startsWith(PRESENTATION) || pkg.startsWith(UI_COMPONENTS)
            }
    }

    /** The `presentation.*` half of [featureFiles] on its own — colour literals are banned there. */
    fun presentationFeatureFiles(): List<KoFileDeclaration> = population(
        name = "presentation files (commonMain, presentation.*)",
        floor = PRESENTATION_FEATURE_FILES_FLOOR,
    ) { scope ->
        scope.files
            .filter { it.path.contains("commonMain") }
            .filter { (it.packagee?.name ?: "").startsWith(PRESENTATION) }
    }

    /** Every file whose package starts with [pkg], across all source sets. */
    fun filesInPackage(pkg: String, floor: Int): List<KoFileDeclaration> = population(
        name = "package $pkg",
        floor = floor,
    ) { scope ->
        scope.files.filter { (it.packagee?.name ?: "").startsWith(pkg) }
    }

    /**
     * [filesInPackage] narrowed to `commonMain`: the shared runtime surface only.
     *
     * The distinction is load-bearing, not cosmetic. `androidMain` holds the `@Preview` exploration
     * files, which legitimately build a full state object to derive the slice they render — so a
     * UI rule phrased over the whole package accuses dev tooling of the thing it is meant to stop
     * in shipped code.
     */
    fun commonMainFilesInPackage(pkg: String, floor: Int): List<KoFileDeclaration> = population(
        name = "package $pkg (commonMain)",
        floor = floor,
    ) { scope ->
        scope.files
            .filter { it.path.contains("commonMain") }
            .filter { (it.packagee?.name ?: "").startsWith(pkg) }
    }

    /**
     * Every file whose path matches [pathFragment] — for populations defined by where they sit on
     * disk rather than by package. Paths are normalised to `/` so the fragment holds on Windows.
     */
    fun filesUnderPath(name: String, pathFragment: Regex, floor: Int): List<KoFileDeclaration> =
        population(name = name, floor = floor) { scope ->
            scope.files.filter { pathFragment.containsMatchIn(it.path.replace('\\', '/')) }
        }

    /** `commonMain` production sources — no test source set, no test-named file. */
    fun commonMainProductionFiles(): List<KoFileDeclaration> = population(
        name = "commonMain production files",
        floor = COMMON_MAIN_PRODUCTION_FLOOR,
    ) { scope ->
        scope.files.filter { file ->
            val path = file.path.replace('\\', '/')
            path.contains("commonMain") && !path.contains("Test")
        }
    }

    /**
     * Everything that SHIPS: `commonMain`, `androidMain` and the mock flavour, both modules. The
     * population for rules about what production code is allowed to say, as opposed to rules about
     * one layer.
     */
    fun productionSourceFiles(): List<KoFileDeclaration> = population(
        name = "production sources (commonMain + androidMain + mock)",
        floor = PRODUCTION_SOURCES_FLOOR,
    ) { scope ->
        scope.files.filter { file ->
            val path = file.path.replace('\\', '/')
            path.contains("commonMain") || path.contains("androidMain") || path.contains("mock")
        }
    }

    /** [productionSourceFiles] narrowed to the domain layer. */
    fun domainProductionFiles(): List<KoFileDeclaration> = population(
        name = "domain production sources",
        floor = DOMAIN_PRODUCTION_FLOOR,
    ) { _ ->
        productionSourceFiles().filter { (it.packagee?.name ?: "").startsWith(DOMAIN) }
    }

    /** Every declared class in the project, for rules about where a KIND of class may live. */
    fun allClasses(): List<KoClassDeclaration> = classPopulation(
        name = "all project classes",
        floor = ALL_CLASSES_FLOOR,
    ) { scope -> scope.classes() }

    const val PRESENTATION = "com.rndeveloper.paparcar.presentation"
    const val UI_COMPONENTS = "com.rndeveloper.paparcar.ui.components"
    const val DATA = "com.rndeveloper.paparcar.data"
    const val DOMAIN = "com.rndeveloper.paparcar.domain"

    // Floors ≈ half the population measured on master at the time of writing (the count in the
    // comment). See the class KDoc for why half and not the exact figure.
    const val FEATURE_FILES_FLOOR = 80              // measured 164
    const val PRESENTATION_FEATURE_FILES_FLOOR = 55 // measured 115
    const val PRESENTATION_PACKAGE_FLOOR = 80       // measured 163 (all source sets)
    const val DATA_PACKAGE_FLOOR = 30               // measured  63
    const val DOMAIN_PACKAGE_FLOOR = 160            // measured 335
    const val HOME_SECTIONS_FLOOR = 15              // measured  34
    const val DETECTION_STAGES_FLOOR = 7            // measured  14
    const val COMMON_MAIN_PRODUCTION_FLOOR = 220    // measured 463
    const val PRODUCTION_SOURCES_FLOOR = 270        // measured 562
    const val DOMAIN_PRODUCTION_FLOOR = 100         // measured 210
    const val ALL_CLASSES_FLOOR = 300
}
