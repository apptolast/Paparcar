package com.rndeveloper.paparcar.domain.stats

/**
 * What this period is heading for, if the rest of it looks like the part that has happened.
 * [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001]
 *
 * Pure and clockless: the elapsed and total spans arrive from the one place that decides windows
 * (`VehicleHistoryCalculator`), so a projection can never span a period the figures did not.
 *
 * ⛔ **A projection is not a figure, and this type is not [ScopedMeasure].** It says what MIGHT
 * happen; every reader of it has to speak in a different tense and sit somewhere else on the screen.
 * Folding it into the measured aggregate would let a caller print "you spent" over a number nobody
 * has spent yet.
 */
data class PeriodProjection(
    /** The projected total at the end of the period, in the measure's own units. */
    val value: Double,
    /** How much of the period had happened when this was computed, 0..1 — the honesty of the basis. */
    val elapsedFraction: Double,
)

/**
 * The projection, or null when projecting would be dishonest.
 *
 * Two gates, and both exist because the arithmetic is trivial while the CLAIM is not:
 *
 * - Under [MIN_ELAPSED_FRACTION] there is no basis. Two days of a month multiplied by fifteen is not
 *   a forecast, it is a multiplication with a forecast's face on it.
 * - Over [MAX_ELAPSED_FRACTION] the projection IS the figure — within a few percent of what the
 *   card above already shows — so saying it twice only adds a number that can disagree with itself.
 */
fun projectPeriod(value: Double, elapsedMs: Long, windowMs: Long): PeriodProjection? {
    if (windowMs <= 0L || elapsedMs <= 0L) return null
    val fraction = elapsedMs.toDouble() / windowMs
    if (fraction < MIN_ELAPSED_FRACTION || fraction > MAX_ELAPSED_FRACTION) return null
    return PeriodProjection(value = value / fraction, elapsedFraction = fraction)
}

/**
 * What a year would cost at this period's pace — and ONLY from a period long enough to set a pace.
 *
 * ⛔ Not from a week. Seven days of someone's life are a sample of their week, not of their year:
 * one holiday, one week working from home, and the figure moves by a third. A month averages over
 * enough different weeks to be worth multiplying, and even then the sentence says "at this pace",
 * never "you will spend".
 */
fun projectYear(value: Double, elapsedMs: Long, minElapsedMs: Long = MIN_ELAPSED_FOR_YEAR_MS): Double? {
    if (elapsedMs < minElapsedMs || value <= 0.0) return null
    return value / elapsedMs * YEAR_MS
}

/** A quarter of the period: below that, the rest of it is guesswork wearing arithmetic. */
const val MIN_ELAPSED_FRACTION = 0.25

/** Past nine tenths, the projection and the measured figure are the same number. */
const val MAX_ELAPSED_FRACTION = 0.90

/** Three weeks — enough different weeks for a pace, which one week is not. */
const val MIN_ELAPSED_FOR_YEAR_MS = 21L * 24 * 60 * 60 * 1000

private const val YEAR_MS = 365.0 * 24 * 60 * 60 * 1000
