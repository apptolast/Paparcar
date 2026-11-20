package com.rndeveloper.paparcar.domain.entitlement

import com.rndeveloper.paparcar.domain.model.PlusGrant
import com.rndeveloper.paparcar.domain.model.PlusOrigin

/**
 * The one thing the app has to SAY about a Plus nobody paid for — or nothing.
 * [MONEY-FOUNDERS-WELCOME-001]
 *
 * ⛔ **One moment, not two booleans.** The two things worth saying ("this is a gift" / "the gift
 * ends on the 15th") can both be true at once, and two independent predicates would then put two
 * modals on screen — the one thing the project forbids outright. A sealed answer makes "both" not
 * representable, which is the same reason icon and sentence travel in one `ReasonLine` on the
 * paywall instead of in two parallel `when`s.
 *
 * Each case carries the DATE it needs, so the UI never goes back to the grant to re-derive it: a
 * second computation of the same fact is how two surfaces end up disagreeing about when the free
 * Plus ends.
 */
sealed interface PlusMoment {

    /**
     * «You are one of the first 2,000.» Shown once per account, as soon as a promotional Plus is
     * seen — which may be seconds after signing up or at the next cold start, because the grant
     * lands asynchronously.
     *
     * @param expiresAtMillis when it ends, or null for a promotional grant with no end date. The
     *   copy then simply does not promise a date rather than inventing one.
     */
    data class FoundersWelcome(val expiresAtMillis: Long?) : PlusMoment

    /**
     * «It ends on the 15th.» The quiet knock before the doors close, so the day the history
     * shrinks back to 7 days nobody reads it as the app breaking.
     *
     * ⛔ Carries the DATE and not a day count, and that is a copy decision with teeth: a count
     * needs singular and plural in nine languages, and Compose Resources has no plurals here — so
     * "1 days left" would be shipped by whichever locale forgot. The date is also the more useful
     * fact: it is what the user will act on.
     */
    data class PromoEnding(val expiresAtMillis: Long) : PlusMoment
}

/**
 * How many days before the end the reminder knocks. A week: long enough to decide without
 * hurrying, short enough that the date is still the one the user will act on.
 */
const val PLUS_PROMO_ENDING_WINDOW_DAYS = 7

private const val MS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * WHICH moment this user is owed, if any. Pure, and the single authority over both lanes.
 *
 * @param grant what the store says — null while free. A PURCHASED grant yields nothing at all:
 *   someone who paid knows they paid, and their plan renews, so there is neither a gift to
 *   announce nor an ending to warn about.
 * @param uid the signed-in account. Null yields nothing: "told once per account" is not a promise
 *   that can be kept about an account we cannot name.
 * @param welcomedUids accounts already congratulated.
 * @param endingNoticedUids accounts already warned.
 *
 * ⚠️ Inside the ending window the welcome is **skipped, not queued**: an install that happens five
 * days before the promo expires would otherwise be congratulated with "three months free", which
 * is a sentence we can measure as false. The reminder is the honest thing to say there, and it is
 * what gets said.
 */
fun plusMomentFor(
    grant: PlusGrant?,
    uid: String?,
    welcomedUids: Set<String>,
    endingNoticedUids: Set<String>,
    nowMs: Long,
): PlusMoment? {
    if (uid == null) return null
    if (grant == null || grant.origin != PlusOrigin.PROMOTIONAL) return null

    val expiresAt = grant.expiresAtMillis
    if (expiresAt != null) {
        val daysLeft = daysUntil(expiresAt, nowMs)
        // An expiry already in the past cannot be announced OR warned about: the grant is over,
        // and the store will stop reporting it as active on its next refresh anyway.
        if (daysLeft <= 0) return null
        if (daysLeft <= PLUS_PROMO_ENDING_WINDOW_DAYS) {
            // Already warned → nothing more to say, even if the welcome never happened: a
            // congratulation after its own warning would be the app talking to itself.
            return if (uid in endingNoticedUids) null else PlusMoment.PromoEnding(expiresAt)
        }
    }
    return if (uid in welcomedUids) null else PlusMoment.FoundersWelcome(expiresAt)
}

/** Whole days from [nowMs] to [instantMs], rounded UP: while access is still live this is never
 *  0, so the window never closes a day early on a plan that still works. */
private fun daysUntil(instantMs: Long, nowMs: Long): Int {
    val remaining = instantMs - nowMs
    if (remaining <= 0) return 0
    return ((remaining + MS_PER_DAY - 1) / MS_PER_DAY).toInt()
}
