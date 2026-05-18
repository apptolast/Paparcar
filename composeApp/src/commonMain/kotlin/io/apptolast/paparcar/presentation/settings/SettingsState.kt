package io.apptolast.paparcar.presentation.settings

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.model.UserProfile

data class SettingsState(
    val userProfile: UserProfile? = null,
    val autoDetectParking: Boolean = true,
    val notifyParkingDetected: Boolean = true,
    val notifySpotFreed: Boolean = true,
    val appVersion: String = "1.0.0",
    val mapType: MapType = MapType.NORMAL,
    val showDeleteAccountConfirmation: Boolean = false,
    val isDeletingAccount: Boolean = false,
    /**
     * Aggregated stats across every vehicle the user owns. `null` while
     * the underlying flow is still loading or when the user has no
     * parking sessions yet (in which case the stats row is hidden).
     */
    val profileStats: ProfileStats? = null,
)

/**
 * Compact stats triplet shown in the Settings profile card.
 *
 * @property totalSessions every parking session across every vehicle
 * @property thisMonthSessions sessions whose timestamp falls in the current
 *                              calendar month (system time zone)
 * @property avgReliabilityPct 0-100 average of `detectionReliability` over
 *                              ended sessions; `null` when no session has a
 *                              reliability value (e.g. all manual).
 */
data class ProfileStats(
    val totalSessions: Int,
    val thisMonthSessions: Int,
    val avgReliabilityPct: Int?,
)
