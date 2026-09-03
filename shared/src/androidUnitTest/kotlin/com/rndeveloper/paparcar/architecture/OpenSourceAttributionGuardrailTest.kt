package com.rndeveloper.paparcar.architecture

import com.mikepenz.aboutlibraries.Libs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertTrue

/**
 * Guardrail for the attribution the app ships. [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001]
 *
 * The licence screen used to be a row that opened a 404 on our own domain. Replacing it with a
 * generated list moves the failure mode rather than removing it: the list can now be *present and
 * wrong* — an export that silently produced nothing, a licence with no text. An incomplete
 * attribution is still a lie, and unlike the 404 it looks fine.
 *
 * Two tests, not five, and the cuts are deliberate: "every library declares a licence" is already
 * enforced **at build time** by `aboutLibraries { library { requireLicense = true } }`, which fails
 * the compilation instead of the test run, and a library pointing at an undeclared licence cannot
 * survive a parser that resolves those references while building the model. A second owner for an
 * invariant that already has one is ceremony, not safety.
 *
 * It reads the very bytes that go into the APK — the merged Compose Resources tree, not the source
 * of the exporter — and parses them with the same parser the app uses.
 *
 * Robolectric, and not by preference: the Android build of that parser is written on
 * `org.json.JSONObject` (`AndroidParser.kt`), which is a stub that throws under a plain JVM test.
 * Worse for us, it swallows every failure — `catch (t: Throwable) { Log.e(...) }` and it returns an
 * EMPTY result. A corrupt file therefore reaches the screen as "no libraries", never as an error,
 * which is exactly why the population witness below is a test and not a comment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenSourceAttributionGuardrailTest {

    @Test
    fun `the attribution ships, parses, and is not a token list`() {
        assertTrue(
            attributionFile.isFile,
            "[the generated attribution is missing] expected it at $attributionFile — the build " +
                "produces it from the Gradle dependency graph (`exportLibraryDefinitions` → " +
                "`mergeComposeResourcesWithLicenses`). Without the file the licence screen ships empty.",
        )
        // The witness: a sweep that found nothing must not pass for a sweep that looked. This module
        // alone pulls Compose, AndroidX, Firebase, Room, Koin and Ktor — a handful of entries means
        // the exporter resolved almost nothing, which reads exactly like success.
        assertTrue(
            libs.libraries.size >= MINIMUM_CREDIBLE_LIBRARIES,
            "[the attribution is implausibly short] ${libs.libraries.size} libraries — under " +
                "$MINIMUM_CREDIBLE_LIBRARIES means the export resolved almost no configuration.",
        )
    }

    @Test
    fun `every licence is readable — its text, or a link we reviewed once`() {
        val violations = libs.licenses
            .filter { it.licenseContent.isNullOrBlank() }
            .mapNotNull { license ->
                when {
                    license.name !in LINK_ONLY_TERMS ->
                        "  - ${license.name} (${license.hash}) — no text, not in the link-only list"
                    // A link-only entry with no link is the dead end the list was meant to prevent.
                    license.url.isNullOrBlank() ->
                        "  - ${license.name} (${license.hash}) — link-only, and there is no link"
                    else -> null
                }
            }

        assertTrue(
            violations.isEmpty(),
            "[licence the screen cannot show] ${violations.size}:\n" + violations.joinToString("\n") +
                "\nApache-2.0 §4 requires shipping a copy of the licence, so a name and a link do " +
                "not discharge it. A new entry here means a new dependency arrived under a licence " +
                "the exporter could not resolve to a text: either it maps to SPDX and the text " +
                "should come through, or it is proprietary terms — add it to LINK_ONLY_TERMS " +
                "**with the reason**, which is the review this test exists to force.",
        )
    }

    private companion object {
        /**
         * Terms of service that are NOT redistributable, so the app links them instead of copying
         * them — the same thing Google's own OSS-licence screens do. Measured on 2026-09-03: these
         * four are the only text-less entries in the whole graph, all of them Google's, and all of
         * them proprietary terms rather than open source licences.
         */
        val LINK_ONLY_TERMS = setOf(
            "Android Software Development Kit License",
            "Play Core Software Development Kit Terms of Service",
            "Play Integrity API Terms of Service",
            "Go License",
        )

        /** 282 on the day this was written; the floor only has to be too high for a broken export. */
        const val MINIMUM_CREDIBLE_LIBRARIES = 100

        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root not found from ${File("").absolutePath}")

        /** The merged tree — what Compose Resources packages, not what the exporter emitted. */
        val attributionFile = File(
            repoRoot,
            "shared/build/generated/composeResourcesWithLicenses/commonMain/composeResources/" +
                "files/aboutlibraries.json",
        )

        /** Parsed with the app's own parser, so a schema change fails here and not on a device. */
        val libs: Libs by lazy {
            Libs.Builder().withJson(attributionFile.takeIf { it.isFile }?.readText() ?: EMPTY_JSON).build()
        }

        const val EMPTY_JSON = """{"libraries":[],"licenses":{}}"""
    }
}
