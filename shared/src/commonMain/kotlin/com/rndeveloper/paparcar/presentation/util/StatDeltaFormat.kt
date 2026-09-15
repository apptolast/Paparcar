package com.rndeveloper.paparcar.presentation.util

import com.rndeveloper.paparcar.ui.components.PapStatDelta
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How a figure moved against the same figure last period — the ONE implementation.
 * [STATS-A-DELTA-THAT-READS-AS-A-FAULT-001]
 *
 * It used to be written twice, character for character: `percentDelta` in the figures grid since
 * [STATS-UI-001] and `recapDelta` in the closed-month card, which copied it. Two copies of a rule
 * are a rule that has already started to diverge.
 *
 * Three refusals, all older than this file and all kept:
 * - nothing to compare against;
 * - a previous ZERO, because any change from zero is "infinitely more", which is not a statement;
 * - a move that rounds to nothing — a "▲ 0%" is noise wearing the clothes of a finding.
 *
 * ⛔ **And a big rise is said in TIMES, not in per cent.** "▲ 2200 %" is arithmetically right and
 * reads as a broken app: nobody says two thousand two hundred per cent, they say twenty-three times.
 * Measured on the Redmi with a real recap (2 min → 46 min). Only upwards: a fall never passes
 * −100 %, so "96 %" already reads fine and needs no second form.
 */
fun statDelta(current: Double, previous: Double?): PapStatDelta? {
    if (previous == null || previous == 0.0) return null
    val changePercent = ((current - previous) / previous * PERCENT).roundToInt()
    if (changePercent == 0) return null
    val ratio = current / previous
    val amount = if (ratio >= MULTIPLIER_FROM) formatMultiplier(ratio) else "${abs(changePercent)}%"
    return PapStatDelta(amountText = amount, isUp = changePercent > 0)
}

/**
 * Half a time matters when it is two; it does not when it is twenty-three.
 *
 * ⛔ And no trailing ".0": "×3.0" is the same broken-looking number this ticket exists to remove,
 * just smaller. Same rule the litres figure already lives by. (Caught by its own test.)
 */
private fun formatMultiplier(ratio: Double): String {
    if (ratio >= MULTIPLIER_WHOLE_FROM) return "$MULTIPLY${ratio.roundToInt()}"
    val tenths = (ratio * TENTHS).roundToInt()
    val whole = tenths / TENTHS.toInt()
    val decimal = tenths % TENTHS.toInt()
    return if (decimal == 0) "$MULTIPLY$whole" else "$MULTIPLY$whole.$decimal"
}

/** Three times over — below that, a percentage still reads like a percentage. */
private const val MULTIPLIER_FROM = 3.0
private const val MULTIPLIER_WHOLE_FROM = 10.0
private const val MULTIPLY = "×"
private const val PERCENT = 100
private const val TENTHS = 10.0
