package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.permissions.OemBackgroundReliabilityManager

class FakeOemBackgroundReliabilityManager(
    override val requiresAutostartWhitelist: Boolean = false,
) : OemBackgroundReliabilityManager {

    var launchCount: Int = 0
        private set
    var launchResult: Boolean = true

    override suspend fun launchAutostartSettings(): Boolean {
        launchCount++
        return launchResult
    }
}
