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

    /** Deletes all Firestore and local data for [userId]. Called during account deletion. */
    override suspend fun deleteAllData(userId: String): Result<Unit>
}
