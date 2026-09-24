package com.rndeveloper.paparcar.presentation.paywall

import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A gate that charges must be sold.** [PAYWALL-A-GATE-THAT-CHARGES-MUST-BE-SOLD-001]
 *
 * Every [PaywallReason] is a place in the app where a free user is asked for money. The pitch —
 * `PLUS_BENEFITS`, the list under *what Plus adds* — is where we say what the money buys. Those
 * two lists must agree, and until this test they agreed only by memory: `ROUTE_CLOUD` shipped
 * with its gate, its Settings switch and its line on paparcar.com, and no row here named it. No
 * conflict, no warning, no failing test — the ticket that added it never opened this file.
 *
 * The check is not "the list is long enough". It is a mapping: each gate has exactly one row that
 * owns it, so the question *where do we sell this?* always has one answer.
 */
class PaywallBenefitsCoverageTest {

    /** [PaywallReason.GENERAL] is an entrance, not a limit — there is nothing to sell for it. */
    private val gates = PaywallReason.entries - PaywallReason.GENERAL

    @Test
    fun should_sell_every_gate_when_the_paywall_lists_what_plus_adds() {
        val sold = PLUS_BENEFITS.flatMap { it.sells }.toSet()
        val unsold = gates.filterNot { it in sold }

        assertTrue(
            unsold.isEmpty(),
            "These paywall reasons charge for something the pitch never names: $unsold. " +
                "Add a PlusBenefit row that sells them (title + note in the 9 locales), or fold " +
                "them into the row whose promise already covers them.",
        )
    }

    @Test
    fun should_own_each_gate_once_when_two_rows_could_claim_it() {
        val owners = gates.associateWith { reason -> PLUS_BENEFITS.count { reason in it.sells } }
        val duplicated = owners.filterValues { it > 1 }

        assertTrue(
            duplicated.isEmpty(),
            "Two rows sell the same reason: ${duplicated.keys}. The pitch would say it twice, " +
                "and the row that owns a gate is the one whose copy has to keep up with it.",
        )
    }

    @Test
    fun should_not_sell_a_general_entrance_when_it_is_an_offer_and_not_a_limit() {
        val sellingGeneral = PLUS_BENEFITS.count { PaywallReason.GENERAL in it.sells }

        assertEquals(
            0,
            sellingGeneral,
            "GENERAL is the Settings row: an offer, not a gate. Claiming it would let a real " +
                "limit hide behind a row that sells nothing in particular.",
        )
    }
}
