package io.apptolast.paparcar.permissions

import io.apptolast.paparcar.domain.permissions.OemBackgroundReliabilityManager

/**
 * iOS has no equivalent to MIUI/ColorOS "Autostart" or "Background activity"
 * whitelists — the OS-level background-execution model (BGTask + significant-location
 * changes) is uniform across all Apple devices. So this implementation always reports
 * that the whitelist is not required and never launches any settings screen.
 */
class IosOemBackgroundReliabilityManagerImpl : OemBackgroundReliabilityManager {

    override val requiresAutostartWhitelist: Boolean = false

    override suspend fun launchAutostartSettings(): Boolean = false

    override val requiresOemBatterySettings: Boolean = false

    override suspend fun launchOemBatterySettings(): Boolean = false
}
