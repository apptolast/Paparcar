package io.apptolast.paparcar.logging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.aakira.napier.LogLevel
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Witness for [FileAntilog]'s retention policy [DET-PARKDIAG-ROTATION-HAS-NO-WITNESS-001].
 *
 * The rotation went from one generation to five in [DET-PARKDIAG-KEEP-MORE-HISTORY-001] because
 * twice in one week the evidence for the incident under investigation had already fallen off the
 * end. That change shipped without a test: the generation shift is a loop over renames that can
 * lose its direction, drop a generation, or clobber one on top of another, and every filesystem
 * call inside `performLog` sits under a `runCatching` — so a broken rotation does not throw, it
 * just silently keeps less history than it promises. Which is the exact failure the ticket set out
 * to end, and the one no other test in the suite would notice.
 *
 * The tests drive [FileAntilog.log] (public on `Antilog`; `performLog` is protected) rather than
 * registering the sink with `Napier.base`, so nothing here depends on global logger state.
 * [FileAntilog.maxBytes] of 1 byte makes rotation exact instead of approximate: the size is checked
 * *before* appending, so the first entry lands in an empty file and every entry after it rotates
 * first. N entries therefore produce N-1 rotations with one entry per generation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileAntilogRotationTest {

    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = ApplicationProvider.getApplicationContext<Context>().filesDir
        filesDir.listFiles().orEmpty()
            .filter { it.name.startsWith(BASE_NAME) }
            .forEach { it.delete() }
    }

    @Test
    fun should_keepEverythingInTheActiveFile_when_itStaysBelowMaxBytes() {
        val antilog = FileAntilog(context(), maxBytes = 1024 * 1024L)

        antilog.write("entry-1")
        antilog.write("entry-2")
        antilog.write("entry-3")

        val active = activeFile().readText()
        assertTrue(active.contains("entry-1"), "the first entry must still be in the active file")
        assertTrue(active.contains("entry-2"), "the second entry must still be in the active file")
        assertTrue(active.contains("entry-3"), "the third entry must still be in the active file")
        assertFalse(generation(1).exists(), "nothing may rotate while the file is below maxBytes")
    }

    @Test
    fun should_moveTheActiveFileToTheFirstGeneration_when_itExceedsMaxBytes() {
        val antilog = FileAntilog(context(), maxBytes = 1L)

        antilog.write("entry-1")
        antilog.write("entry-2")

        assertTrue(generation(1).readText().contains("entry-1"), "the rotated entry goes to .1")
        val active = activeFile().readText()
        assertTrue(active.contains("entry-2"), "the new entry starts the fresh active file")
        assertFalse(active.contains("entry-1"), "the active file must start empty after a rotation")
    }

    @Test
    fun should_shiftEveryGenerationDown_when_rotatingRepeatedly() {
        val antilog = FileAntilog(context(), maxBytes = 1L, keptRotations = 5)

        antilog.write("entry-1")
        antilog.write("entry-2")
        antilog.write("entry-3")
        antilog.write("entry-4")

        // Newest first: the active file, then .1 .2 .3 walking back in time.
        assertTrue(activeFile().readText().contains("entry-4"), "the newest entry stays active")
        assertEntryInGeneration(1, "entry-3")
        assertEntryInGeneration(2, "entry-2")
        assertEntryInGeneration(3, "entry-1")
        assertFalse(generation(4).exists(), "three rotations must not reach into a fourth slot")
        assertFalse(generation(5).exists(), "three rotations must not reach into a fifth slot")
    }

    @Test
    fun should_discardOnlyTheOldestGeneration_when_rotatingMoreTimesThanKept() {
        val antilog = FileAntilog(context(), maxBytes = 1L, keptRotations = 3)

        (1..6).forEach { antilog.write("entry-$it") }

        // Five rotations into three slots: the window holds the newest three, nothing more.
        assertTrue(activeFile().readText().contains("entry-6"), "the newest entry stays active")
        assertEntryInGeneration(1, "entry-5")
        assertEntryInGeneration(2, "entry-4")
        assertEntryInGeneration(3, "entry-3")
        assertFalse(generation(4).exists(), "no generation may appear beyond keptRotations")
        val kept = (1..3).joinToString("") { generation(it).readText() }
        assertFalse(kept.contains("entry-1"), "the oldest entries are the ones that fall off")
        assertFalse(kept.contains("entry-2"), "the oldest entries are the ones that fall off")
    }

    @Test
    fun should_leaveTheLegacyRotationUntouched_when_rotating() {
        // A device upgraded in place still holds the single `parkdiag.log.old` written by builds
        // before DET-PARKDIAG-KEEP-MORE-HISTORY-001. Deleting it silently would be the same
        // mistake the ticket fixed, in miniature: it is pulled by hand, so it must survive.
        val legacy = File(filesDir, "$BASE_NAME.old").apply { writeText("legacy-window\n") }
        val antilog = FileAntilog(context(), maxBytes = 1L)

        (1..8).forEach { antilog.write("entry-$it") }

        assertTrue(legacy.exists(), "the legacy rotation must survive every new rotation")
        assertEquals("legacy-window\n", legacy.readText(), "and must not be written into either")
    }

    @Test
    fun should_beReadableImmediately_when_anEntryIsWritten() {
        // [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001] The sink keeps its append stream OPEN across
        // lines (an open+close per line was a syscall storm once this ran in release too). Every
        // entry is still flushed on the spot, because the whole value of this file is being
        // readable after the process is killed — a buffered tail would lose exactly the lines
        // before an OEM kill, which are the ones a field diagnosis needs most.
        val antilog = FileAntilog(context(), maxBytes = 1024 * 1024L)

        antilog.write("entry-1")

        assertTrue(activeFile().readText().contains("entry-1"), "the entry must be on disk at once")
    }

    @Test
    fun should_continueTheActiveGeneration_when_theProcessRestarts() {
        // A restart must not reset the rotation clock: the byte counter is seeded from the file
        // that is already there, so a long-lived generation still rotates when it should.
        FileAntilog(context(), maxBytes = 1L).write("entry-1")

        FileAntilog(context(), maxBytes = 1L).write("entry-2")

        assertTrue(generation(1).readText().contains("entry-1"), "the pre-restart entry rotates out")
        assertTrue(activeFile().readText().contains("entry-2"), "the new process starts a fresh file")
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun activeFile(): File = File(filesDir, BASE_NAME)

    private fun generation(index: Int): File = File(filesDir, "$BASE_NAME.$index")

    private fun assertEntryInGeneration(index: Int, entry: String) {
        val file = generation(index)
        assertTrue(file.exists(), "generation .$index must exist and hold $entry")
        assertTrue(file.readText().contains(entry), "generation .$index must hold $entry")
    }

    private fun FileAntilog.write(message: String) = log(LogLevel.DEBUG, TAG, null, message)

    private companion object {
        const val BASE_NAME = "parkdiag.log"

        /** FileAntilog only persists lines whose tag starts with its `PARKDIAG` prefix. */
        const val TAG = "PARKDIAG/RotationTest"
    }
}
