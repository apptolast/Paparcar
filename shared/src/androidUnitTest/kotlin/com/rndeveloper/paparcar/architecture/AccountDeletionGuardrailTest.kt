package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Erasure is a LIST, and a list is the one thing nobody remembers to extend.
 * [PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001]
 *
 * `DeleteAccountUseCase` sweeps whatever `DomainModule` hands it. Implementing
 * [com.rndeveloper.paparcar.domain.repository.UserScopedRepository] is therefore only half the
 * contract — its own KDoc says so — and the other half is a line in a Koin module that compiles
 * exactly as well when it is missing. Twice now it was:
 *
 *  - `zoneAlertSubs`, which holds the zones you watch and your push tokens
 *  - `sharedCars/{carId}/members/{uid}`, which holds your membership and another copy of the tokens
 *
 * Both survived account deletion in production while the published privacy policy said the account
 * and its data were gone. Neither failed a build, a test or a lint: a write-only collection has
 * nobody to notice it is still there.
 *
 * So the rule is not "implement the interface" — that one is already enforced by the compiler. It
 * is **"every implementor of it is wired into the deletion chain"**, which is what this reads.
 *
 * The witness is [GuardrailScope.productionFilesMentioning]: rename the interface, move the
 * repositories, and the population collapses instead of quietly agreeing with everything.
 * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 */
class AccountDeletionGuardrailTest {

    @Test
    fun `every user-scoped repository is wired into the account deletion chain`() {
        val files = GuardrailScope.productionFilesMentioning(MARKER, MENTION_FLOOR)

        val implementors = files
            .flatMap { file -> IMPLEMENTS.findAll(file.text).map { it.groupValues[1] } }
            .distinct()
            .sorted()
        if (implementors.size < IMPLEMENTOR_FLOOR) {
            fail(
                "[empty population] found ${implementors.size} repositories extending $MARKER, " +
                    "expected at least $IMPLEMENTOR_FLOOR. The regex stopped matching — this rule " +
                    "is now enforcing nothing.",
            )
        }

        // A SECOND population, because the wiring file does not mention the marker at all: it
        // names the concrete repositories. Asking for it by the anchor rather than by the filename
        // is what keeps the witness — move the list to another module and this collapses to zero
        // instead of passing on an empty string.
        val wiring = GuardrailScope
            .productionFilesMentioning(LIST_ANCHOR, WIRING_FLOOR)
            .joinToString("\n") { file ->
                // Bounded by the line that CLOSES the list, not by the first ")" — which lives
                // inside `get<Repo>()` and would leave an empty window that accuses everything.
                file.text.substringAfter(LIST_ANCHOR, "")
                    .lineSequence()
                    .takeWhile { line -> !line.trim().startsWith(")") }
                    .joinToString("\n")
            }

        val unswept = implementors.filterNot { wiring.contains("get<$it>()") }
        assertTrue(
            unswept.isEmpty(),
            buildString {
                appendLine("These repositories hold user data that account deletion never reaches:")
                unswept.forEach { appendLine("  · $it") }
                appendLine()
                appendLine("Add them to `$LIST_ANCHOR` in DomainModule, minding the documented order")
                appendLine("(push tokens first, SharedCar before Vehicle, Diagnostics last), and check")
                appendLine("whether §6 of hosting/public/privacy-policy.html still tells the truth.")
            },
        )
    }

    private companion object {
        const val MARKER = "UserScopedRepository"
        val IMPLEMENTS = Regex("""interface\s+(\w+)\s*:\s*$MARKER""")
        const val LIST_ANCHOR = "userScopedRepos = listOf("

        /** Measured 10 files / 7 interfaces on 2026-09-16; floors sit at roughly half. */
        const val MENTION_FLOOR = 5
        const val IMPLEMENTOR_FLOOR = 4

        /**
         * Exactly one file wires the chain, so here the floor IS the census — and legitimately:
         * a second file holding a second deletion list would be the bug, not churn.
         */
        const val WIRING_FLOOR = 1
    }
}
