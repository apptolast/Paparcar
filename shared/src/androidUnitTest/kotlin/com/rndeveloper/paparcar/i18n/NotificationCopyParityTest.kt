package com.rndeveloper.paparcar.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [IOS-NOTIF-A-SURFACE-THAT-SPEAKS-ONE-LANGUAGE-001] The `notif_*` copy exists in TWO resource
 * systems on purpose: `app/src/main/res` is the LIVE Android source (field-tested, shipping) and
 * `shared/composeResources` is its mirror for iOS. A mirror nobody guards is a fork waiting to
 * happen — this test parses both file trees and fails on ANY divergence, per key, per locale.
 * The one legitimate difference is NORMALIZED away: Android res escapes apostrophes (`\'`),
 * Compose Resources must not. [COPY-APOSTROPHE-IS-NOT-ESCAPED-001]
 *
 * When Android migrates onto composeResources, this test retires WITH the duplication — never
 * separately.
 */
class NotificationCopyParityTest {

    private val locales = listOf(
        "values", "values-es", "values-it", "values-pt", "values-fr",
        "values-de", "values-nl", "values-pl", "values-ro",
    )

    private val stringTag = Regex("""<string name="(notif_[a-z_]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    private fun repoRoot(): File {
        var dir = File("").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("repo root not found")
        }
        return dir
    }

    private fun keysOf(base: File, locale: String): Map<String, String> {
        val file = File(base, "$locale/strings.xml")
        assertTrue(file.exists(), "missing $file")
        return stringTag.findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
    }

    @Test
    fun should_matchAndroidCopyExactly_when_theMirrorIsComparedPerKeyAndLocale() {
        val root = repoRoot()
        val androidRes = File(root, "app/src/main/res")
        val composeRes = File(root, "shared/src/commonMain/composeResources")
        locales.forEach { locale ->
            val mirrored = keysOf(composeRes, locale)
            val android = keysOf(androidRes, locale)
            mirrored.forEach { (key, value) ->
                assertEquals(
                    android[key],
                    value,
                    "notif copy drifted for '$key' in $locale — app/src/main/res is the live " +
                        "source; fix the mirror in shared/composeResources (raw apostrophes).",
                )
            }
        }
    }

    /** Population witness: a green over an empty mirror is not parity. The iOS surface uses 30
     *  keys today — the twenty of the detection surface plus the ten of the zone and shared-car
     *  push cards [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]; fewer means the mirror lost subjects,
     *  not that copy converged. */
    @Test
    fun should_containTheWholeMirroredSurface_when_countedPerLocale() {
        val composeRes = File(repoRoot(), "shared/src/commonMain/composeResources")
        locales.forEach { locale ->
            val count = keysOf(composeRes, locale).size
            assertTrue(count >= 30, "$locale mirrors only $count notif_* keys — expected >= 30")
        }
    }
}
