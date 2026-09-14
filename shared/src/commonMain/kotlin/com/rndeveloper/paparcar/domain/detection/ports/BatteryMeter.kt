package com.rndeveloper.paparcar.domain.detection.ports

/**
 * The measuring instrument behind [com.rndeveloper.paparcar.domain.detection.TripBatteryGauge]:
 * the platform's cumulative charge counter and whether the device is on power RIGHT NOW.
 * [BATT-A-COST-IS-MEASURED-NOT-DECLARED-001]
 *
 * Deliberately a sibling of `ChargingStateSource`, not the same port: that one is the sampling
 * POLICY's input (a `fun interface` the adaptive tiers consult per fix), this one is the
 * measurement instrument (consulted twice per job). Sharing a port would couple two consumers
 * with different contracts.
 *
 * Android reads both from BatteryService property lookups (no receiver, no I/O). A platform
 * without an implementation simply has no gauge — cost figures are then absent, never invented.
 */
interface BatteryMeter {
    /** The fuel gauge's cumulative charge counter in µAh, or null when the device has none. */
    fun chargeCounterMicroAh(): Long?

    fun isCharging(): Boolean
}
