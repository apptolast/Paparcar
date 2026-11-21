package com.rndeveloper.paparcar.domain.entitlement

import com.rndeveloper.paparcar.domain.model.PlusGrant
import com.rndeveloper.paparcar.domain.model.PlusOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The verdict behind the two things the app says about a gifted Plus.
 * [MONEY-FOUNDERS-WELCOME-001]
 *
 * Every rule of both lanes lives in `plusMomentFor`, so this is where they are falsified: the
 * ViewModel only delivers and persists.
 */
class PlusMomentTest {

    private val now = 1_800_000_000_000L
    private val uid = "uid-1"

    private fun promo(daysLeft: Int?) = PlusGrant(
        origin = PlusOrigin.PROMOTIONAL,
        expiresAtMillis = daysLeft?.let { now + it * DAY_MS },
    )

    private fun moment(
        grant: PlusGrant?,
        uid: String? = this.uid,
        welcomed: Set<String> = emptySet(),
        noticed: Set<String> = emptySet(),
        // Por defecto ES fundador: así cada caso mide lo que dice su nombre, y los dos que miden
        // justo lo contrario pasan `founders = emptySet()` a la vista. [MONEY-FOUNDERS-PLUS-PROMO-001]
        founders: Set<String> = setOfNotNull(uid),
    ) = plusMomentFor(
        grant = grant,
        uid = uid,
        welcomedUids = welcomed,
        endingNoticedUids = noticed,
        foundersUids = founders,
        nowMs = now,
    )

    // ── La bienvenida afirma un HECHO, y el hecho necesita prueba ─────────────

    /**
     * ⛔ El agujero del panel. [MONEY-FOUNDERS-PLUS-PROMO-001]
     *
     * Un Plus concedido a mano en RevenueCat —compensación de soporte, un amigo, un tester— llega
     * como `PROMOTIONAL` igual que el de la campaña, y hasta aquí leía «eres uno de los 2.000
     * primeros». No es un caso raro: es el botón fácil del panel, y no caducaba con la promo.
     */
    @Test
    fun should_say_nothing_when_the_promotional_plus_did_not_come_from_the_campaign() {
        assertNull(moment(promo(daysLeft = 90), founders = emptySet()))
    }

    /** Y la prueba es POR CUENTA: la del vecino no vale. */
    @Test
    fun should_say_nothing_when_another_account_was_the_founder() {
        assertNull(moment(promo(daysLeft = 90), founders = setOf("someone-else")))
    }

    /**
     * ⚠️ La asimetría deliberada: el AVISO DE FIN no exige ser fundador.
     *
     * «Tu Plus de regalo se acaba el 15» es verdad para cualquier regalo con fecha, venga de donde
     * venga. Callárselo a quien recibió uno del panel sería dejarle sin aviso justo antes de que
     * se le recorte el historial, que es el defecto que el aviso vino a arreglar.
     */
    @Test
    fun should_still_warn_about_the_ending_when_the_gift_did_not_come_from_the_campaign() {
        val endsAt = now + 3 * DAY_MS
        assertEquals(
            PlusMoment.PromoEnding(endsAt),
            moment(PlusGrant(PlusOrigin.PROMOTIONAL, endsAt), founders = emptySet()),
        )
    }

    // ── The welcome ──────────────────────────────────────────────────────────

    @Test
    fun should_welcome_the_founder_when_a_promotional_plus_is_seen_for_the_first_time() {
        assertEquals(
            PlusMoment.FoundersWelcome(expiresAtMillis = now + 90 * DAY_MS),
            moment(promo(daysLeft = 90)),
        )
    }

    @Test
    fun should_welcome_without_a_date_when_the_promotional_grant_has_no_expiry() {
        // RevenueCat can grant a promotional entitlement with no end date. The copy then simply
        // does not promise a day, instead of inventing one.
        assertEquals(PlusMoment.FoundersWelcome(expiresAtMillis = null), moment(promo(daysLeft = null)))
    }

    @Test
    fun should_say_nothing_when_this_account_was_already_welcomed() {
        assertNull(moment(promo(daysLeft = 90), welcomed = setOf(uid)))
    }

    /** The half of "once per account" a single boolean would have broken: two accounts on one
     *  phone, which is this project's own field-test setup. */
    @Test
    fun should_welcome_the_second_account_when_only_the_first_was_welcomed() {
        assertEquals(
            PlusMoment.FoundersWelcome(expiresAtMillis = now + 90 * DAY_MS),
            moment(promo(daysLeft = 90), uid = "uid-2", welcomed = setOf(uid)),
        )
    }

    // ── The reminder ─────────────────────────────────────────────────────────

    @Test
    fun should_warn_about_the_ending_when_the_promo_enters_its_last_week() {
        assertEquals(
            PlusMoment.PromoEnding(expiresAtMillis = now + PLUS_PROMO_ENDING_WINDOW_DAYS * DAY_MS),
            moment(promo(daysLeft = PLUS_PROMO_ENDING_WINDOW_DAYS), welcomed = setOf(uid)),
        )
    }

    @Test
    fun should_say_nothing_when_the_promo_is_still_outside_the_warning_window() {
        assertNull(moment(promo(daysLeft = PLUS_PROMO_ENDING_WINDOW_DAYS + 1), welcomed = setOf(uid)))
    }

    @Test
    fun should_say_nothing_when_this_account_was_already_warned() {
        assertNull(moment(promo(daysLeft = 3), welcomed = setOf(uid), noticed = setOf(uid)))
    }

    /**
     * ⚠️ The asymmetry worth having a test for: installing five days before the promo expires.
     * Congratulating someone on months of free Plus they do not have is a measurably false
     * sentence, so the welcome is SKIPPED — not queued — and the honest thing gets said instead.
     */
    @Test
    fun should_warn_instead_of_welcoming_when_the_promo_ends_before_anyone_was_congratulated() {
        assertEquals(
            PlusMoment.PromoEnding(expiresAtMillis = now + 5 * DAY_MS),
            moment(promo(daysLeft = 5), welcomed = emptySet()),
        )
    }

    /** And once that warning is read, the skipped welcome does not come back to life. */
    @Test
    fun should_say_nothing_when_the_warning_replaced_a_welcome_that_was_never_shown() {
        assertNull(moment(promo(daysLeft = 5), welcomed = emptySet(), noticed = setOf(uid)))
    }

    /** Rounded UP: while access is still live the window never closes a day early. */
    @Test
    fun should_still_warn_when_only_minutes_of_the_promo_are_left() {
        val endsInAnHour = now + 60 * 60 * 1000L
        assertEquals(
            PlusMoment.PromoEnding(expiresAtMillis = endsInAnHour),
            plusMomentFor(
                grant = PlusGrant(PlusOrigin.PROMOTIONAL, endsInAnHour),
                uid = uid,
                welcomedUids = setOf(uid),
                endingNoticedUids = emptySet(),
                foundersUids = setOf(uid),
                nowMs = now,
            ),
        )
    }

    // ── What is NOT a moment ─────────────────────────────────────────────────

    @Test
    fun should_say_nothing_when_the_user_is_free() {
        assertNull(moment(grant = null))
    }

    /** Somebody who PAID knows they paid, and their plan renews: nothing to announce, nothing to
     *  warn about. Including a trial, which is a paid plan in its first period. */
    @Test
    fun should_say_nothing_when_the_plus_was_bought() {
        assertNull(moment(PlusGrant(PlusOrigin.PURCHASED, expiresAtMillis = now + 3 * DAY_MS)))
    }

    /** "Told once per account" cannot be promised about an account we cannot name — and the only
     *  wrong answer here is one that would be re-shown on every launch for ever. */
    @Test
    fun should_say_nothing_when_there_is_no_signed_in_account() {
        assertNull(moment(promo(daysLeft = 90), uid = null))
    }

    @Test
    fun should_say_nothing_when_the_expiry_has_already_passed() {
        assertNull(moment(promo(daysLeft = -1)))
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
