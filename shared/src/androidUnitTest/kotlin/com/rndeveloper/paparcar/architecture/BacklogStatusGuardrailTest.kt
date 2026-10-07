package com.rndeveloper.paparcar.architecture

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * `docs/backlog` is the project's memory between sessions, so its status lines have to be TRUE.
 * [DOCS-THE-BACKLOG-STATUS-LOOP-MUST-BE-A-GUARD-001]
 *
 * ## Why this exists
 *
 * `DOCS-BACKLOG-TRUTH-002` made the directory readable with a `grep` and wrote the classifying loop
 * into `docs/backlog/README.md`, including the good tell — `git show-ref` on the branch a doc cites.
 * **Nobody ran it for 22 days.** By 07-10 the index had drifted all the way back: 23 docs still said
 * *"En revision · rama X"* with X deleted and their commit in master, 4 marked closed with a green
 * circle instead of a tick (two of them outside any label the loop reads — one in the **title**),
 * 4 carried their status in words with no marker at all, and the README still announced
 * **544 ficheros** when there were 775.
 *
 * The lesson is not that the loop was wrong. It is that **a command in a README is not a tool**, the
 * same way a command in a README left Play's listing art showing the 2022 mark for eight days
 * ([PLAY-THE-LISTING-ART-STILL-SHOWS-THE-2022-MARK-001]). Everything else comparable in this repo
 * has a witness — locales, typography, colour, the website, the brand surfaces, the apostrophes.
 * The backlog did not.
 *
 * ## What it checks, and why these three
 *
 * 1. **Every doc is classifiable.** A status line whose first marker is in the declared vocabulary.
 *    That is what the marker-in-the-title and the status-in-words defects both broke.
 * 2. **No doc is binary.** One raw NUL byte and `grep` refuses to read the WHOLE file, so the loop
 *    returns nothing for it — indistinguishable from "has no status". That really happened, in
 *    `stats-a-period-is-a-series-not-a-number-001.md`, written into the very sentence where the doc
 *    reports catching that same mistake in the code and fixing it *"al escape"*.
 * 3. **The README's figures are the measured ones.** This is the check with teeth: closing a ticket
 *    now forces a re-count, which is exactly the discipline that lapsed. The failure message hands
 *    you the line to paste, so the cost is one edit, not an investigation.
 *
 * ## What it deliberately does NOT check, and why it CANNOT
 *
 * The branch tell — *a doc that says "En revision · rama X" when X is gone and its ticket has a
 * commit in master is lying* — needs the full ref list and the full history of `master`. CI checks
 * out with `actions/checkout@v4` at its default **`fetch-depth: 1`**: one commit, shallow, detached.
 * A guard asking git for that here would find an empty history, conclude "no implementation commit"
 * and go **green on every single doc** — the precise way a guard stops existing
 * ([CI-A-GUARD-THAT-NEVER-STARTS-IS-NOT-A-GUARD-001], [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]).
 *
 * So it lives OUTSIDE the suite, in `tools/backlog/branch_tell.py`, which refuses to run on a
 * shallow clone instead of lying about it. [verifyBranchTellToolIsPresent] keeps it from being
 * deleted and keeps this class naming it. That split is a measured limitation, not a preference.
 *
 * It also cannot tell whether the prose of an open doc is any good, or whether a new ticket got its
 * row in the index. Nothing can; that is what reading is for.
 *
 * ⛔ Reads files, touches no bytecode, so Gradle calls it UP-TO-DATE and never runs it. Falsifying
 * it needs `--rerun-tasks`, like every guard in this package.
 */
class BacklogStatusGuardrailTest {

    @Test
    fun `should give every backlog doc a status line the index loop can classify`() {
        val docs = backlogDocs()

        val unreadable = docs.filterNot { it.name in NOT_TICKETS }
            .mapNotNull { file ->
                val line = statusLine(file.readText())
                    ?: return@mapNotNull "${file.name} — no status line at all"
                if (firstMarker(line) == null) {
                    "${file.name} — status line carries no known marker: ${line.trim().take(90)}"
                } else {
                    null
                }
            }

        assertTrue(
            unreadable.isEmpty(),
            "These backlog docs cannot be classified by the loop in $README_RELATIVE, so the answer " +
                "to the question it exists to answer silently stops including them:\n" +
                unreadable.joinToString("\n") { "  - $it" } +
                "\n\nFix the DOC, not this test: give it an Estado line whose FIRST marker is one of " +
                VOCABULARY.joinToString(" ") +
                ". A marker in the title or buried in prose does not count — that is the defect this " +
                "guard was written for.",
        )
    }

    @Test
    fun `should keep every backlog doc readable as text, with no control bytes`() {
        val offenders = backlogDocs().mapNotNull { file ->
            val found = file.readBytes()
                .map { it.toInt() and 0xFF }
                .filter { it < 0x09 || it == 0x0B || it == 0x0C || (it in 0x0E..0x1F) }
                .distinct()
                .sorted()
            if (found.isEmpty()) null else "${file.name} — ${found.joinToString { "0x%02X".format(it) }}"
        }

        assertTrue(
            offenders.isEmpty(),
            "A single control byte makes `grep` treat the ENTIRE file as binary, so the index loop " +
                "returns nothing for it — indistinguishable from a doc with no status:\n" +
                offenders.joinToString("\n") { "  - $it" } +
                "\n\nWrite the escape, never the byte. `file <doc>.md` says `data` instead of " +
                "`UTF-8 text` when this is wrong.",
        )
    }

    @Test
    fun `should make the index announce the counts the directory actually has`() {
        val docs = backlogDocs()
        val tickets = docs.filterNot { it.name in NOT_TICKETS }

        val markers = tickets.map { file ->
            val line = statusLine(file.readText())
                ?: fail("${file.name} has no status line — the first test in this class explains it")
            firstMarker(line) ?: fail("${file.name} has no known marker in its status line")
        }

        val closed = markers.count { it in CLOSED_MARKERS }
        val notTickets = NOT_TICKETS.size + markers.count { it == ANALYSIS_MARKER }
        val open = docs.size - closed - notTickets

        val measured = Figures(total = docs.size, closed = closed, open = open, notTickets = notTickets)

        assertEquals(
            measured,
            announcedFigures(),
            "$README_RELATIVE announces figures the directory does not have. It drifted 230 files " +
                "this way once, announcing 544 while there were 775, and that is what let 23 closed " +
                "tickets keep saying they were in review.\n\nReplace its headline with exactly:\n\n" +
                "> **${measured.total} ficheros: ${measured.closed} cerrados, ${measured.open} " +
                "abiertos, ${measured.notTickets} que no son tickets.**\n",
        )
    }

    @Test
    fun verifyBranchTellToolIsPresent() {
        val tool = File(repoRoot, BRANCH_TELL_TOOL)
        assertTrue(
            tool.isFile,
            "$BRANCH_TELL_TOOL is gone. It carries the one check this suite CANNOT: that no open doc " +
                "cites a branch which no longer exists while its ticket has a commit in master. It " +
                "lives outside the suite because CI clones shallow (fetch-depth 1) and a guard " +
                "asking git for history it does not have would go green on every doc.\n\n" +
                "If it was moved, update this constant. If it was deleted, the branch tell died with " +
                "it and nothing is watching the defect that opened " +
                "DOCS-A-CLOSED-TICKET-MUST-NOT-SAY-IT-IS-IN-REVIEW-001.",
        )
        assertTrue(
            tool.readText().contains(SHALLOW_REFUSAL),
            "$BRANCH_TELL_TOOL no longer refuses to run on a shallow clone (looked for " +
                "\"$SHALLOW_REFUSAL\"). A branch tell that cannot see history must SAY SO; one that " +
                "quietly reports that nothing is wrong is worse than not having it.",
        )
    }

    private fun backlogDocs(): List<File> {
        val dir = File(repoRoot, BACKLOG_RELATIVE)
        if (!dir.isDirectory) fail("not a directory from ${repoRoot.path}: $BACKLOG_RELATIVE")
        val docs = dir.listFiles { f: File -> f.isFile && f.name.endsWith(".md") }?.sortedBy { it.name }
            ?: fail("could not list $BACKLOG_RELATIVE")

        // Witness, per GuardrailScope's doctrine: a guard that selects nothing is satisfied by an
        // empty folder. 775 docs on 07-10; the floor sits near half, far under normal churn and far
        // over the zero a moved or renamed directory would produce.
        assertTrue(
            docs.size >= BACKLOG_FLOOR,
            "only ${docs.size} docs found in $BACKLOG_RELATIVE, below the floor of $BACKLOG_FLOOR. " +
                "Either the directory moved — and these checks have been inspecting nothing since — " +
                "or the backlog genuinely halved, which is a conversation, not a number to edit.",
        )
        for (name in NOT_TICKETS) {
            assertTrue(
                docs.any { it.name == name },
                "the allowlist names $name but $BACKLOG_RELATIVE has no such file. An allowlist " +
                    "entry over a file that does not exist is not an exception, it is a hole " +
                    "[UI-TYPE-SYSTEM-HYGIENE-001]. Remove it, or fix the name.",
            )
        }
        return docs
    }

    private data class Figures(val total: Int, val closed: Int, val open: Int, val notTickets: Int)

    private fun announcedFigures(): Figures {
        val readme = File(repoRoot, README_RELATIVE).takeIf { it.isFile }?.readText()
            ?: fail("not found from ${repoRoot.path}: $README_RELATIVE")
        val match = FIGURES_RE.find(readme)
            ?: fail(
                "$README_RELATIVE has no headline figures line. It must carry one, in this exact " +
                    "shape, so this guard can compare it:\n" +
                    "> **<n> ficheros: <n> cerrados, <n> abiertos, <n> que no son tickets.**",
            )
        return Figures(
            total = match.groupValues[1].toInt(),
            closed = match.groupValues[2].toInt(),
            open = match.groupValues[3].toInt(),
            notTickets = match.groupValues[4].toInt(),
        )
    }

    /**
     * Estado/Status decides; Fecha is only a fallback.
     *
     * The order is measured, not aesthetic: five docs open with a `**Fecha:**` line and carry their
     * `**Estado:**` on the NEXT line, while `ios-f0-001.md` carries both on the SAME line. Reading
     * Fecha first classifies those five by a date instead of a status; never reading it loses
     * `ios-f0-001`. Status first, date as backup, is the only order that reads all six right.
     */
    private fun statusLine(text: String): String? {
        val lines = text.lines()
        return lines.firstOrNull { STATUS_RE.containsMatchIn(it) }
            ?: lines.firstOrNull { DATE_RE.containsMatchIn(it) }
    }

    /** The EARLIEST marker in the line — the only one that does not lie. */
    private fun firstMarker(line: String): String? =
        VOCABULARY.mapNotNull { marker -> line.indexOf(marker).takeIf { it >= 0 }?.let { it to marker } }
            .minByOrNull { it.first }
            ?.second

    private companion object {
        const val BACKLOG_RELATIVE = "docs/backlog"
        const val README_RELATIVE = "$BACKLOG_RELATIVE/README.md"
        const val BRANCH_TELL_TOOL = "tools/backlog/branch_tell.py"

        /** The phrase the tool must keep, so it can never go quiet on a clone without history. */
        const val SHALLOW_REFUSAL = "shallow clone"

        /** 775 docs measured on 07-10-2026; the floor sits near half. */
        const val BACKLOG_FLOOR = 380

        /**
         * Declared, never deduced — a guard must not infer its population from a filename pattern
         * [TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001]. These four are the index itself and the
         * three PROMPT session briefs: they are not tickets and must not carry a status. A new one
         * of either kind is a deliberate edit here, which is the point.
         */
        val NOT_TICKETS = setOf(
            "README.md",
            "map-a-remembered-pin-looks-like-a-live-one-PROMPT.md",
            "onb-the-map-answers-more-than-spots-PROMPT.md",
            "ui-a-neutral-tile-must-not-mute-an-action-PROMPT.md",
        )

        /**
         * Closed is a tick, a cross or a no-entry sign — **never** the green circle, which is why
         * the green circle is absent from [VOCABULARY] altogether.
         */
        val CLOSED_MARKERS = setOf("✅", "❌", "🚫")

        /** A field analysis carries a status per item inside: counted as "not a ticket". */
        const val ANALYSIS_MARKER = "📚"

        /**
         * Every marker a status line may start with.
         *
         * ⛔ The green circle is NOT here on purpose: four docs used it to mean closed, and the rule
         * since 03-09 is that closed is a tick. Leaving it out of the vocabulary is what turns that
         * mistake into a red build instead of a doc the loop quietly files as open.
         */
        val VOCABULARY = listOf(
            "✅", // done
            "❌", // rejected
            "🚫", // won't do
            "🔵", // in progress / in review
            "🟡", // partially landed
            "📋", // open, not started
            "⚪", // open, no branch
            "🧊", // deferred on purpose
            "🔴", // open and urgent
            "🟠", // open, decision taken, no code
            ANALYSIS_MARKER, // field analysis, not a ticket
        )

        val STATUS_RE = Regex("""^[ \t]*>?[ \t]*\*{0,2}(Estado|Status)""")
        val DATE_RE = Regex("""^[ \t]*>?[ \t]*\*{0,2}Fecha""")
        val FIGURES_RE = Regex(
            """\*\*(\d+) ficheros: (\d+) cerrados, (\d+) abiertos, (\d+) que no son tickets\.\*\*""",
        )

        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root not found from ${File("").absolutePath}")
    }
}
