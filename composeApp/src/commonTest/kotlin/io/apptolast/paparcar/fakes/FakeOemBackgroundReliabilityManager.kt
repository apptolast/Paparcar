package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.permissions.OemBackgroundReliabilityManager

class FakeOemBackgroundReliabilityManager(
    override val requiresAutostartWhitelist: Boolean = false,
    override val requiresOemBatterySettings: Boolean = false,
) : OemBackgroundReliabilityManager {

    var launchCount: Int = 0
        private set
    var launchResult: Boolean = true
    var batteryLaunchCount: Int = 0
        private set

    override suspend fun launchAutostartSettings(): Boolean {
        launchCount++
        return launchResult
    }

    override suspend fun launchOemBatterySettings(): Boolean {
        batteryLaunchCount++
        return launchResult
    }
}
