package com.rndeveloper.paparcar.domain.model

/**
 * HOW the active Plus arrived and until WHEN it lasts. [MONEY-FOUNDERS-WELCOME-001]
 *
 * A different question from [Entitlement], which answers *what can this user do* — the only thing
 * the six gates ask, and the reason they are untouched by this type. This one answers *where did
 * this come from*, and it has exactly two consumers: the welcome that celebrates a gift nobody
 * asked for, and the reminder that says when the gift ends.
 *
 * ⛔ Still mapped from RevenueCat and nowhere else: the origin and the expiry are read off the
 * `CustomerInfo` on every emission, never mirrored into Firestore or Room. The rule of [MONEY-001]
 * is not weakened by asking the source of truth a second question. What IS persisted locally is
 * only *"this account has already been told"*, which is not subscription state.
 *
 * @param expiresAtMillis when the access ends, or null for access with no known end (a lifetime
 *   grant). Null is NOT "we could not read it": a promotional grant without an end date genuinely
 *   has nothing to count down to, and the reminder lane treats it as such.
 */
data class PlusGrant(
    val origin: PlusOrigin,
    val expiresAtMillis: Long?,
)

/**
 * Who gave this user their Plus.
 *
 * ⛔ **Two values, not three, and the missing one is `TRIAL`.** [PURCHASED] covers everything a
 * store sells, the 7-day introductory trial included — a trial is a paid plan in its first period,
 * and the person who started it chose it. [PROMOTIONAL] covers what nobody paid for. The question
 * both consumers ask is *did WE give them this?*, and to that question a store trial answers no.
 * A third value with no consumer is how an enum starts drifting from the decision it encodes.
 *
 * ⛔⛔ **A PLAY PROMO CODE is [PURCHASED], and that is a product requirement, not a technicality.**
 * Plus given away to friends goes out as Google Play promo codes — deliberately, so those people
 * are NOT told they are one of the first 2,000 founders. A redeemed code buys a real Play
 * subscription, so the store reports `PLAY_STORE` and it lands here, silent. [PROMOTIONAL] means
 * only what was granted INSIDE RevenueCat. The line is drawn once, in
 * `RevenueCatEntitlementRepositoryImpl.toGrant`, and it has a witness there:
 * `RevenueCatEntitlementRepositoryImplTest`. Below the data layer the two gifts are
 * indistinguishable, which is why no domain test could ever catch this one.
 */
enum class PlusOrigin {
    /** Bought from a store (Play, App Store, Stripe…) — trial period and redeemed promo codes
     *  included. Nothing is said to this user about where their Plus came from. */
    PURCHASED,

    /** Granted by us in RevenueCat — the «Fundadores» promo for the first 2,000 accounts. */
    PROMOTIONAL,
}
