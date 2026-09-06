package com.rndeveloper.paparcar.domain.entitlement

import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * What the free tier may see of a parking history, decided in ONE place. [MONEY-001]
 *
 * The product rule: free sees the last [FREE_HISTORY_WINDOW_DAYS] days; Plus sees everything.
 * The data is NEVER deleted or mutated — losing Plus only narrows this window, and regaining
 * it widens the window back over the same rows. Both history surfaces (the per-vehicle list
 * and the map detail's prev/next stepper) must call this same function: a stepper that walks
 * where the list cannot see would be a paywall with a hole in it.
 */
data class GatedHistory(
    val visible: List<UserParking>,
    /** How many sessions the window hid — what the "Ver todo con Plus" footer counts. */
    val hiddenOlderCount: Int,
)

fun gateHistoryToEntitlement(
    sessions: List<UserParking>,
    isPlus: Boolean,
    nowMs: Long,
): GatedHistory {
    if (isPlus) return GatedHistory(visible = sessions, hiddenOlderCount = 0)
    val windowStartMs = nowMs - FREE_HISTORY_WINDOW_DAYS * MS_PER_DAY
    // An ACTIVE session is the user's car parked right now — that is "the present", not
    // history, and hiding someone's own parked car behind a paywall is never on the table,
    // however long ago they parked it.
    val visible = sessions.filter { it.isActive || it.location.timestamp >= windowStartMs }
    return GatedHistory(
        visible = visible,
        hiddenOlderCount = sessions.size - visible.size,
    )
}

const val FREE_HISTORY_WINDOW_DAYS = 30
private const val MS_PER_DAY = 86_400_000L
