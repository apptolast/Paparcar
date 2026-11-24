package com.rndeveloper.paparcar.domain.repository

import com.apptolast.baselogin.domain.model.UserSession
import com.rndeveloper.paparcar.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository : UserScopedRepository {
    /** Returns the cached profile or creates a new one in Firestore + Room on first login. */
    suspend fun getOrCreateProfile(session: UserSession): Result<UserProfile>

    /** Observe the locally cached profile for the given user. */
    fun observeProfile(userId: String): Flow<UserProfile?>

    /**
     * Records on the ACCOUNT when the user closed the guided checklist — null puts it back on
     * offer. [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
     *
     * Remote-only by design: this is the half of the tutorial's state that has to outlive the
     * install, so caching it locally would be caching the very thing that kept getting lost.
     */
    suspend fun setFirstStepsDismissedAt(userId: String, dismissedAtMs: Long?): Result<Unit>

    /**
     * Publishes whether automatic detection can run for this account.
     * [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001]
     *
     * Remote-only, like [setFirstStepsDismissedAt], but for the opposite reason: this is not state
     * the app ever reads back — it is a report ABOUT the device, and the device already knows. The
     * local half that matters (what was last published, so the next observation can stay silent)
     * lives in `AppPreferences`, not here.
     *
     * Best-effort by contract: a failure is swallowed by the caller. Losing a snapshot offline is
     * acceptable — the next real change republishes, and the field is a photo of now, not a log.
     */
    suspend fun publishDetectionState(
        userId: String,
        autoDetectParking: Boolean,
        detectionBlockers: List<String>,
        batteryOptimizationExempt: Boolean,
        platform: String,
        appVersion: String,
        updatedAtMs: Long,
    ): Result<Unit>

    /**
     * Publishes where this installation came from. [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
     *
     * Remote-only, like [publishDetectionState] and for the same reason: this is a report ABOUT
     * the install, never read back by the domain. Whether it has already run once lives in
     * `AppPreferences`, not here — the Play referrer can only be read once per installation.
     */
    suspend fun publishInstallAttribution(
        userId: String,
        channel: String,
        source: String?,
        medium: String?,
        campaign: String?,
        metaCampaignId: String?,
        metaAdsetId: String?,
        metaAdId: String?,
        rawReferrer: String?,
        resolvedAtMs: Long,
    ): Result<Unit>

    /** Deletes all Firestore and local data for [userId]. Called during account deletion. */
    override suspend fun deleteAllData(userId: String): Result<Unit>
}
