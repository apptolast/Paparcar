package com.rndeveloper.paparcar.domain.entitlement

import com.rndeveloper.paparcar.domain.model.PlusOffer
import com.rndeveloper.paparcar.domain.model.PlusPlan
import kotlin.math.roundToInt

/** Months in a year — the factor that turns a monthly price into its yearly equivalent. */
private const val MONTHS_PER_YEAR = 12

/** Below this the badge is noise: "save 2%" invites arithmetic instead of a decision. */
private const val MIN_MEANINGFUL_SAVING_PERCENT = 5

/**
 * How much cheaper the yearly plan is than paying monthly for a year, as a whole percentage.
 * Null when the comparison cannot honestly be made or is not worth making. [MONEY-001]
 *
 * A pure function rather than a use case, by the doctrine in CLAUDE.md: its result is not a
 * verdict anyone can cite in a diagnostic, it is a predicate feeding the one screen that shows a
 * savings badge. It lives here, next to the other entitlement rules, so it stays directly
 * testable without the ceremony of an injected class.
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * ⛔ The arithmetic runs on [PlusOffer.amountMicros], never on the formatted strings. Parsing
 * "1,99 €" back into a number is a bug waiting for the first locale that writes its decimals with
 * a different separator — which is most of the nine this app ships in.
 *
 * Returns null when there is nothing trustworthy to claim: a missing plan, a non-positive price
 * (a fully discounted product), no actual saving, or a saving too small to be worth a badge. The
 * caller shows no badge at all in that case rather than a "0 %" one — a discount claim that
 * turns out to be zero costs more trust than it buys.
 */
fun annualSavingsPercent(offers: List<PlusOffer>): Int? {
    val monthly = offers.firstOrNull { it.plan == PlusPlan.MONTHLY } ?: return null
    val yearly = offers.firstOrNull { it.plan == PlusPlan.YEARLY } ?: return null
    if (monthly.amountMicros <= 0L || yearly.amountMicros <= 0L) return null

    val payingMonthlyForAYear = monthly.amountMicros * MONTHS_PER_YEAR
    if (yearly.amountMicros >= payingMonthlyForAYear) return null

    val saved = payingMonthlyForAYear - yearly.amountMicros
    val percent = (saved.toDouble() / payingMonthlyForAYear * 100).roundToInt()
    return percent.takeIf { it >= MIN_MEANINGFUL_SAVING_PERCENT }
}
