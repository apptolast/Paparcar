package com.rndeveloper.paparcar.domain.entitlement

/**
 * WHICH limit (or entrance) opened the paywall. [FREE-LIMITS-001]
 *
 * Travels with the navigation, feeds the `paywall_shown` analytics event (the conversion funnel's
 * first column) and picks the one-line motive shown when the store is unavailable. [GENERAL] is
 * for entrances that are an OFFER rather than a gate (the Plus row in Settings) — stamping an
 * invented limit there would lie to analytics.
 */
enum class PaywallReason {
    HISTORY,
    VEHICLES,
    ZONES,
    STATS,
    ZONE_ALERTS,

    /** The fuel-price watch — a cheaper pump nearby, and what a followed station did overnight.
     *  Its own entry and not folded into [ZONE_ALERTS]: the funnel's whole job is telling WHICH
     *  vigilance someone wanted. [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] */
    FUEL_ALERTS,
    ROUTE_CLOUD,
    GENERAL,
    ;

    companion object {
        /** Route-arg tolerant parse: an unknown or absent name is a GENERAL open, never a crash. */
        fun ofName(name: String?): PaywallReason =
            entries.firstOrNull { it.name == name } ?: GENERAL
    }
}
