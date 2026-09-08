package com.rndeveloper.paparcar.domain.entitlement

/**
 * Whether the user may CREATE another zone, decided in ONE place. [FREE-LIMITS-001]
 *
 * Free keeps TWO zones; Plus has no cap. Only CREATION is gated: editing or deleting an existing
 * zone is always free, and zones already above the limit (created under Plus, or before the limit
 * existed) are conserved untouched — the downgrade rule is "gate the NEW, keep the created".
 */
fun canCreateZone(zoneCount: Int, isPlus: Boolean): Boolean =
    isPlus || zoneCount < FREE_ZONE_LIMIT

const val FREE_ZONE_LIMIT = 2
