package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue

/**
 * A class registered with a constructor reference (`viewModelOf(::X)`, `singleOf`, `factoryOf`,
 * `scopedOf`) must not take a nullable constructor parameter.
 *
 * ## Why this exists
 *
 * Those DSLs resolve every parameter with `get()`, so a nullable parameter is not optional to
 * them: when nothing binds its type, the instance is not built with `null`, the resolution throws
 * `NoDefinitionFoundException`. `KoinModuleVerifyTest` cannot see it, because `verify()` DOES treat
 * a nullable parameter as optional; the runtime and the check disagree.
 *
 * That gap crashed Settings on iOS: `SettingsViewModel(widgetPinRequester: WidgetPinRequester? = null)`
 * was registered with `viewModelOf`, and only `:app`'s Android module binds a `WidgetPinRequester`.
 * Android worked, the verify test was green, and the first tap on the Settings tab on iOS aborted
 * the process. An optional dependency is written as `getOrNull()` in an explicit definition —
 * the pattern `HomeTripController` already uses. [IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001]
 *
 * Falsify with `--rerun-tasks` (see [ImportGuardrailTest]): a DI file edit does not always change
 * the bytecode this test task depends on.
 */
class KoinOptionalDependencyGuardrailTest {

    @Test
    fun `constructor-reference registrations take no nullable constructor parameter`() {
        val registrations = GuardrailScope.filesInPackage(DI_PACKAGE, floor = DI_FILES_FLOOR)
            .flatMap { file -> REFERENCE_REGISTRATION.findAll(file.text).map { file.name to it.groupValues[2] } }
        assertTrue(
            registrations.size >= REGISTRATIONS_FLOOR,
            "[empty population] found ${registrations.size} constructor-reference registration(s) in " +
                "$DI_PACKAGE, expected at least $REGISTRATIONS_FLOOR — the selector no longer matches " +
                "how modules are written, so this rule is enforcing nothing.",
        )

        val classesByName = GuardrailScope.scope.classes().groupBy { it.name }
        val unresolved = registrations.filter { (_, className) -> classesByName[className].isNullOrEmpty() }
        assertTrue(
            unresolved.isEmpty(),
            "[unresolved registration] no class declaration found for: " +
                unresolved.joinToString { (file, cls) -> "$cls ($file.kt)" },
        )

        val violations = registrations.flatMap { (file, className) ->
            classesByName.getValue(className)
                .flatMap { it.primaryConstructor?.parameters.orEmpty() }
                .filter { it.type.isNullable }
                .map { "$file.kt — ::$className(${it.name}: ${it.type.text})" }
        }
        assertTrue(
            violations.isEmpty(),
            "[nullable parameter behind a constructor reference] ${violations.size} violation(s):\n" +
                violations.joinToString("\n") { "  - $it" } + "\n" +
                "viewModelOf/singleOf/factoryOf/scopedOf resolve it with get() and crash where nothing " +
                "binds it. Register the class explicitly and pass the parameter with getOrNull().",
        )
    }

    private companion object {
        const val DI_PACKAGE = "com.rndeveloper.paparcar.di"

        /** 13 DI files today (both modules, all source sets); about half, per GuardrailScope. */
        const val DI_FILES_FLOOR = 6

        /** 7 constructor-reference registrations today; about half. */
        const val REGISTRATIONS_FLOOR = 3

        val REFERENCE_REGISTRATION = Regex("""\b(viewModelOf|singleOf|factoryOf|scopedOf)\(::(\w+)\)""")
    }
}
