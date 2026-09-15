package com.rndeveloper.paparcar.domain.stats

import com.rndeveloper.paparcar.domain.model.Money

/**
 * Everything a period of parking history can honestly say about itself — the ONE aggregate the
 * stats surfaces (panel, teaser, history block) derive from. [STATS-CORE-001]
 *
 * Every nullable field is null BELOW ITS SIGNIFICANCE THRESHOLD (the `MIN_SESSIONS_FOR_*` pattern)
 * or when the data to compute it does not exist — a consumer renders nothing there, never a "0"
 * or "—" posing as a figure. Everything derived from distance (litres, kWh, cost, CO₂) is computed
 * ONLY over sessions with a measured route; [distance] carries that coverage so no caller can
 * print a subtotal as a total [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001].
 *
 * ⛔ **Every measured SUM here is a [ScopedMeasure], and that is not decoration**
 * [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]: the paragraph above was written when this aggregate
 * was born and was true of exactly one field, while five tiles printed distance-derived subtotals
 * as period totals and the search time arrived as a bare `Long`. A figure that cannot say what it
 * covers cannot be read.
 */
data class PeriodStats(
    /** Sessions in the period, active included — "parked N times". */
    val parkings: Int,
    /** Completed sessions — what duration/consumption/provenance are computed over. */
    val endedParkings: Int,
    /** Measured driving distance (meters) + its coverage; null when no session carries a route. */
    val distance: ScopedMeasure<Float>?,
    /** Litres burned over the MEASURED distance (override or FuelDefaults per session's vehicle). */
    val fuelLiters: Double?,
    /** kWh drawn over the measured distance (EV primary + PHEV secondary). */
    val energyKwh: Double?,
    /** Estimated spend — null until a price exists (FUEL-PRICES-001) or when any consumed energy
     *  lacks its price: a partial cost is not a cost. */
    val costEstimate: Money?,
    /** Estimated emissions over the measured distance. */
    val co2Kg: Double?,
    /** Mean parked time of ended sessions; null below [ComputePeriodStatsUseCase.MIN_SESSIONS_FOR_DURATION]. */
    val avgParkedDurationMs: Long?,
    /** Hour of day (0–23) parks concentrate in; null below the peak threshold. */
    val peakHourBucket: Int?,
    /** isoDayNumber 1=Mon..7=Sun; null below the peak threshold. */
    val mostActiveDayOfWeek: Int?,
    /** Street that repeats (≥ street threshold); a 1-1 tie is nobody's favourite. */
    val favoriteStreet: String?,
    /** Closes that gave the community a spot — 0 IS information. */
    val spotsReleased: Int,
    /** Auto-detected among provenance-KNOWN ended sessions; both 0 below the share threshold. */
    val autoDetectedCount: Int,
    val detectionKnownCount: Int,
    /**
     * Time lost hunting a spot (ms) + its coverage — a SUM, because it is what the period cost.
     * Null when no session in the window carries the measure.
     *
     * The unit left the NAME when the coverage joined the value: `searchTimeMs` was a bare `Long`
     * that reached the screen as a period total computed over whichever sessions happened to have a
     * measured route. [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001]
     * [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]
     */
    val searchTime: ScopedMeasure<Long>? = null,
    /** The equivalent previous period, for ▲/▼ comparisons. Null when not requested or empty. */
    val previous: PeriodStats? = null,
)
