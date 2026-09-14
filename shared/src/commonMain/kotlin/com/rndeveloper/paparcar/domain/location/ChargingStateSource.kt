package com.rndeveloper.paparcar.domain.location

/**
 * Whether the device is charging RIGHT NOW — the one power fact the location sampling policy
 * reads: while charging, the battery cost of dense sampling is irrelevant, so
 * [com.rndeveloper.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase] pins its
 * densest tier. [DET-ADAPTIVE-SAMPLING-EARNS-ITS-FIXES-001]
 *
 * Optional by design: a platform without a wired implementation (iOS today — UIDevice battery
 * monitoring has its own ceremony) simply gets no boost, never a failure. Synchronous because the
 * read is a cached system value (a sticky broadcast on Android), consulted per fix tick.
 */
fun interface ChargingStateSource {
    fun isCharging(): Boolean
}
