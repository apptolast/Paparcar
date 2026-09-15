package com.rndeveloper.paparcar.domain.stats.insights

/**
 * Something the app concluded about this period, with the evidence that backs it.
 * [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001]
 *
 * ⛔ **The evidence is part of the insight, not a footnote it can lose on the way to the screen.**
 * A sentence about someone's week is an assertion; without the sample behind it, it is a hunch
 * wearing a fact's clothes. Every reader shows [samples], the same way every measured sum shows its
 * coverage [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001].
 *
 * The copy is NOT in here. This type says what was concluded and how strongly; the words — and the
 * nine locales they live in — belong to the surface that renders them.
 */
data class StatsInsight(
    val kind: InsightKind,
    /** The figures the sentence needs, already in domain units (ms, meters, percent). */
    val values: InsightValues,
    /** How many parkings back this up. Never zero: below its threshold an insight does not exist. */
    val samples: Int,
    /**
     * How big the finding is, as a ratio against what it is being compared to (1.5 = half again
     * as much). Only used for RANKING — no surface prints it, because "1.5×" is mechanics.
     */
    val effect: Double,
) {
    /**
     * What decides which three of these a person reads. [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001]
     *
     * Effect × evidence, with the evidence capped: a finding twice as big as another deserves to
     * lead, but so does one backed by fourteen parkings over one backed by two — and past
     * [SAMPLE_WEIGHT_CAP] extra samples stop being reassurance and start being a ranking bug, where
     * a small finding about your daily commute buries a huge one about your weekends.
     */
    val score: Double get() = effect * minOf(samples, SAMPLE_WEIGHT_CAP)
}

enum class InsightKind {
    /** The day and time band where finding a spot costs the most. */
    WORST_TIME_TO_PARK,

    /** …and the one where it costs the least. Never the same slot as the worst. */
    EASIEST_TIME_TO_PARK,

    /** The place that charges the most time per visit, against the rest. */
    COSTLIEST_PLACE,

    /** How the hunt moved against the equivalent period before. */
    SEARCH_TIME_TREND,
}

/**
 * Everything the four sentences need, flat and nullable. One type instead of a sealed hierarchy of
 * four payloads: the surface reads the fields its own `when` branch knows about, and a sealed tree
 * would buy exhaustiveness the `when` over [InsightKind] already gives.
 */
data class InsightValues(
    /** Monday = 0. */
    val dayIndex: Int? = null,
    /** Start hour of the band, 0–23. */
    val bandStartHour: Int? = null,
    val placeName: String? = null,
    /** The figure the sentence leads with. */
    val valueMs: Long? = null,
    /** What it is compared against — the rest of the period, or the period before. */
    val baselineMs: Long? = null,
) {
    /** The gap the trend sentence states, always positive; its direction is [isUp]. */
    val deltaMs: Long? get() = if (valueMs != null && baselineMs != null) {
        kotlin.math.abs(valueMs - baselineMs)
    } else {
        null
    }

    val isUp: Boolean get() = (valueMs ?: 0) >= (baselineMs ?: 0)
}

/** Three is what a person reads before a list of conclusions becomes a wall of them. */
const val MAX_INSIGHTS = 3

/**
 * Past this many parkings, more evidence no longer changes which finding matters most — it only
 * lets a frequent, tiny pattern outrank a rare, enormous one.
 */
const val SAMPLE_WEIGHT_CAP = 12
