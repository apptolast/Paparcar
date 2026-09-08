package com.rndeveloper.paparcar.domain.model

/**
 * One buyable Paparcar Plus plan, as the STORE currently describes it. [MONEY-001]
 *
 * ⛔ Every field here is read live from the store on each paywall open — nothing is hardcoded and
 * nothing is cached across sessions. That is not fussiness: the price a user is shown must be the
 * price they will be charged, and it changes with their country, their currency, an ongoing promo
 * and any price edit made in Play. A literal "1,99 €" in the codebase would be right in Spain on
 * the day it was typed and wrong everywhere else forever.
 *
 * This is the same doctrine that keeps the entitlement itself out of our database, applied one
 * level down: RevenueCat owns what Plus COSTS exactly as it owns who HAS it.
 */
data class PlusOffer(
    val plan: PlusPlan,
    /** Store-formatted and already localised — currency symbol, separators and placement included.
     *  Render it as-is; never re-format it, and never parse it back into a number. */
    val price: String,
    /** The same price as an integer in millionths, which is the only form the two plans can be
     *  compared in. Display code must not touch this — it exists so [annualSavingsPercent] can do
     *  arithmetic that string prices make impossible. */
    val amountMicros: Long,
    /** What the yearly plan works out to per month, store-formatted. Null for the monthly plan,
     *  where it would just repeat [price]. */
    val pricePerMonth: String? = null,
    /** Length of the introductory free trial the store is currently offering, in days; null when
     *  there is none. Read from the product's introductory offer rather than assumed, because a
     *  trial that lapsed in the dashboard must stop being promised in the app the same day. */
    val freeTrialDays: Int? = null,
)
