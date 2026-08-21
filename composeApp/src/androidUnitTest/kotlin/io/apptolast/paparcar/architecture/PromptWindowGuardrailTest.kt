package io.apptolast.paparcar.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Test
import kotlin.test.assertTrue

/**
 * [DET-ASK-STATE-001] Guardrail for the invariant the whole ticket rests on:
 *
 * > the "did you park?" question is OPEN if and only if the last operation on the confirmation
 * > notification channel was the prompt post.
 *
 * The window is a single persisted slot written and cleared by the notification adapter, which lets
 * fifteen unchanged call sites across the coordinator, the service and the revert use case converge
 * for free. That only holds while the adapter is the ONLY thing touching that channel, and while
 * every function that touches it also moves the slot. A new "post something else on 2002" — or a
 * `cancel()` that skips the port — would silently strand the row asking a question the tray no
 * longer shows, and nothing else in the suite would notice: the bug is the ABSENCE of a line.
 *
 * Not a unit test of the adapter (that would need Robolectric for `NotificationManager`); a test of
 * the shape that makes the adapter's three lines sufficient.
 */
class PromptWindowGuardrailTest {

    private val scope = Konsist.scopeFromProject()

    private val notificationAdapters by lazy {
        scope.files.filter { it.name == "AppNotificationManagerImpl" || it.name == "IosAppNotificationManagerImpl" }
    }

    @Test
    fun `every function that posts on the confirmation channel also moves the prompt window`() {
        val adapter = notificationAdapters.first { it.path.contains("androidMain") }
        // Text between one `override fun` and the next — enough to ask "does THIS function do both".
        val functions = adapter.text.split(Regex("""(?=\n {4}override fun )"""))
        val posting = functions.filter { CONFIRMATION_CHANNEL_POST.containsMatchIn(it) }
        // Anchors the guardrail against passing vacuously: the two known posts are the prompt itself
        // and the "parked + revert" card that morphs it. A third one is exactly what this test is
        // here to notice — and a regex that silently stopped matching would hide all of them.
        assertTrue(
            posting.size == EXPECTED_CHANNEL_POSTS,
            "expected $EXPECTED_CHANNEL_POSTS functions posting on the confirmation channel, found " +
                "${posting.size} — if that is a deliberate new post, open/close the durable question " +
                "in it and bump this number",
        )
        val violations = posting
            .filterNot { PROMPT_WINDOW_TOUCH.containsMatchIn(it) }
            .map { it.lineSequence().first { line -> line.contains("fun ") }.trim() }
        assertTrue(
            violations.isEmpty(),
            "[posts on PARKING_CONFIRMATION_NOTIFICATION_ID without opening or closing the durable " +
                "question — the Home row and the tray would disagree] ${violations.size} violation(s):\n" +
                violations.joinToString("\n") { "  - $it" },
        )
    }

    @Test
    fun `swiping the prompt away must not be wired to close the question`() {
        // Silencing the tray is not answering. There is deliberately NO setDeleteIntent on the
        // confirmation prompt: swiping it (and tapping it, via setAutoCancel) reaches no code, so
        // the durable question survives and the Home row keeps asking — which is the entire point
        // of DET-ASK-STATE-001, and the tap case is its main path (the user lands in Home with the
        // question still owed).
        //
        // A future delete-intent here would look like tidy-up and would delete the feature. If one
        // is ever genuinely needed (e.g. to log the dismissal), it must NOT clear the window.
        val adapter = notificationAdapters.first { it.path.contains("androidMain") }
        val prompt = adapter.text
            .substringAfter("override fun showParkingConfirmation")
            .substringBefore("\n    override fun ")
        assertTrue(
            !prompt.contains("setDeleteIntent"),
            "[showParkingConfirmation wires setDeleteIntent — a swipe would answer a question the " +
                "user never answered]",
        )
    }

    @Test
    fun `nothing outside the notification adapters cancels a notification directly`() {
        // Every close must go through AppNotificationManager.dismiss, which is where the window is
        // cleared. A stray notificationManager.cancel(...) elsewhere would close the tray card and
        // leave the question open in the app.
        val violations = scope.files
            .filterNot { it.name == "AppNotificationManagerImpl" || it.name == "IosAppNotificationManagerImpl" }
            .filterNot { it.path.contains("Test") }
            .filter { RAW_CANCEL_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            "[cancels a notification outside the adapter — use AppNotificationManager.dismiss] " +
                "${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it.kt" }}",
        )
    }

    private companion object {
        /** `showParkingConfirmation` (opens) + `showParkingSavedConfirm` (morphs → closes). */
        const val EXPECTED_CHANNEL_POSTS = 2
        val CONFIRMATION_CHANNEL_POST = Regex("""notify\(\s*\n?\s*AppNotificationManager\.PARKING_CONFIRMATION_NOTIFICATION_ID""")
        val PROMPT_WINDOW_TOUCH = Regex("""(set|clear)PendingPromptWindow""")
        // A cancel on the platform NotificationManager, not the domain port's own dismiss().
        val RAW_CANCEL_REGEX = Regex("""notificationManager\.cancel\s*\(""")
    }
}
