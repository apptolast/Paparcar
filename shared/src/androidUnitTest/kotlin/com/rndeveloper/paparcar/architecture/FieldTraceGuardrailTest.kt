package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue

/**
 * [TEST-AN-ORPHANED-FIELD-TRACE-STILL-LOOKS-LIKE-COVERAGE-001] **A recorded field trace that no test
 * replays is a file nobody reads, and it is worse than absent because it counts.**
 *
 * The traces under `domain/detection/coordinator/replay/` are not tests, they are EVIDENCE: each one
 * is a stream a real device recorded on a real night, transcribed once from a `parkdiag` on a cable
 * and kept forever. The doctrine that made them says so — *every field bug becomes a permanent
 * fixture: record the trace, replay it, assert the corrected outcome, and the regression can never
 * silently return.*
 *
 * The word doing the work in that sentence is **replay**. A trace nobody replays asserts nothing. It
 * still sits in the folder, it still shows up in `ls`, and the corpus is habitually quoted as a
 * COUNT — "the 16 field replays", "including the 18 replays" — in commit messages, in
 * `PARKING-DETECTION.md`, in memory. So an orphan does not merely stop protecting: it keeps being
 * counted as protection. That is the same shape as
 * `UI-TYPE-SYSTEM-HYGIENE-001` (an allowlist covering a component no screen rendered — "not an
 * exception, a hole") and `I18N-A-DEAD-KEY-PASSES-EVERY-PARITY-CHECK-001` (a key nobody used passing
 * every parity check). Deleting the test that reads a 1 100-line fixture is a one-line diff, and
 * nothing in this repository noticed it before this rule.
 *
 * ## The unit is the STREAM, not the file
 *
 * 18 files hold **21** `List<TraceEvent>` values, because several carry more than one and the extra
 * ones are exactly the interesting halves: `TraceEnamorados001.eventsWithoutRecovery` is the
 * worst-case variant of its own trace, `TraceSupermarket001.wander` is the leg where the anchor must
 * not drift, `TRACE_CASA_GAP_ANCHOR_3008_QUIET_TAIL` is what lets a 900 s timeout run at all. A rule
 * phrased over files would let any of those die inside a file that still looks used.
 *
 * ## What counts as being read
 *
 * A reference from a **test source file other than the one declaring it**, in code rather than in
 * prose. Comments are stripped before the search, and that is not pedantry: a trace cited only by a
 * KDoc link is precisely an orphan with a good alibi, and two constants in this very corpus are
 * cited nowhere but their own KDoc.
 *
 * ⚠️ **Where this rule stops, said rather than implied.** It checks that a test source file mentions
 * the stream; it does not follow the chain to an `@Test`. A fixture helper that reads a trace and is
 * itself no longer called by anything would satisfy it. That hop is not free to verify and the case
 * has never occurred here — if it ever does, this is the file to extend, not to work around.
 */
class FieldTraceGuardrailTest {

    @Test
    fun `every recorded field trace is replayed by a test`() {
        val traces = declaredTraces()
        val corpus = GuardrailScope.testSourceFiles()
            .associate { it.path.replace('\\', '/') to stripComments(it.text) }

        val orphans = traces.filter { trace ->
            corpus.none { (path, text) ->
                path != trace.declaredIn && text.contains(trace.reference)
            }
        }

        assertTrue(
            orphans.isEmpty(),
            "[a field trace nobody replays] each of these is a stream a device recorded once and " +
                "cannot record again, kept so its regression can never silently return — and each " +
                "is now read by no test, while still being counted in the corpus. Either restore " +
                "the test that replayed it, or delete the fixture deliberately and say so in the " +
                "commit. ${orphans.size} violation(s):\n" +
                orphans.joinToString("\n") { "  - ${it.reference}  (${it.fileName})" },
        )
    }

    /**
     * The half that keeps the rule above honest about its own subject.
     *
     * `fieldTraceFiles()` already refuses to come back below its floor, so a moved package cannot
     * make this pass on an empty list. This adds the other thing that can quietly go to zero without
     * moving a single file: the PARSE. The declarations are found by matching
     * `val <name>: List<TraceEvent>`, and a fixture written any other way — an inferred type, a
     * function returning the list, a different element type — is invisible to the matcher while
     * looking perfectly normal in the folder. Then the rule reports zero orphans out of zero traces.
     */
    @Test
    fun `the trace parser still finds a stream in every trace file`() {
        val files = GuardrailScope.fieldTraceFiles()
        val traces = declaredTraces()
        val silent = files.map { it.path.replace('\\', '/') }
            .filter { path -> traces.none { it.declaredIn == path } }

        assertTrue(
            silent.isEmpty(),
            "[a trace file the parser cannot read] the rule finds streams by matching " +
                "`val <name>: List<TraceEvent>`, so a fixture declared any other way is skipped in " +
                "silence and never checked for readers. Give it the explicit type, or teach the " +
                "matcher. ${silent.size} file(s):\n" +
                silent.joinToString("\n") { "  - ${it.substringAfterLast('/')}" },
        )
        assertTrue(
            traces.size >= files.size,
            "[fewer streams than trace files] ${traces.size} stream(s) across ${files.size} file(s) " +
                "— every trace file declares at least one, so this means the matcher went blind",
        )
    }

    // ── Parsing ───────────────────────────────────────────────────────────────────────────────

    /**
     * How a stream is referred to from outside the file that declares it: the bare name when it is
     * top-level, `Object.name` when it is a member.
     *
     * The qualification is load-bearing rather than tidy. Members are called `events`, `park`,
     * `wander` — search the corpus for `events` on its own and it matches 60-odd files, so an
     * unqualified rule would report every member trace as read no matter what happened to it.
     */
    private data class Trace(val reference: String, val declaredIn: String, val fileName: String)

    private fun declaredTraces(): List<Trace> =
        GuardrailScope.fieldTraceFiles().flatMap { file ->
            val text = file.text
            val path = file.path.replace('\\', '/')
            val owner = OBJECT_REGEX.find(text)?.groupValues?.get(1)
            STREAM_REGEX.findAll(text).map { match ->
                val indented = match.groupValues[1].isNotEmpty()
                val name = match.groupValues[2]
                Trace(
                    reference = if (indented && owner != null) "$owner.$name" else name,
                    declaredIn = path,
                    fileName = path.substringAfterLast('/'),
                )
            }
        }

    /**
     * Comments removed — block, whole-line and trailing — so prose cannot vouch for a fixture.
     *
     * The trailing case is not an afterthought; leaving it in defeated the rule the first time this
     * was falsified. Replacing a trace with another one and leaving `// was TRACE_CALLE_GAVIA_001`
     * on the same line is the most natural thing a person does while editing, and with only
     * whole-line comments stripped the orphan went unreported.
     *
     * Deliberately not a Kotlin lexer: `//` is cut wherever it appears unless it follows a colon,
     * which is there so a `https://` in a string survives. Cutting cannot hide a real use — a use
     * sits BEFORE the `//` on its line, and one that sits after it is commented out by definition.
     */
    private fun stripComments(text: String): String =
        LINE_COMMENT.replace(BLOCK_COMMENT.replace(text, " "), "")

    private companion object {
        val OBJECT_REGEX = Regex("""^object (\w+)""", RegexOption.MULTILINE)
        val STREAM_REGEX =
            Regex("""^([ \t]*)(?:internal )?val (\w+)\s*:\s*List<TraceEvent>""", RegexOption.MULTILINE)
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""(?<!:)//[^\n]*""")
    }
}
