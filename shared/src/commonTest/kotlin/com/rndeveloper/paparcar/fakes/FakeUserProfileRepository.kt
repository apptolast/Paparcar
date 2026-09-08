package com.rndeveloper.paparcar.fakes

import com.apptolast.baselogin.domain.model.UserSession
import com.rndeveloper.paparcar.domain.model.UserProfile
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeUserProfileRepository : UserProfileRepository {

    private val profiles = mutableMapOf<String, UserProfile>()
    private val _profileFlow = MutableStateFlow<Map<String, UserProfile>>(emptyMap())

    var getOrCreateCallCount = 0
        private set
    var getOrCreateResult: Result<UserProfile> = Result.success(defaultProfile())
    var observedUserId: String? = null

    /** The session the last [getOrCreateProfile] call actually received — what the freshness
     *  tests assert against. [PROFILE-PHOTO-STUCK-AND-STALE-001] */
    var lastSession: UserSession? = null
        private set

    /**
     * Optional latch held INSIDE [getOrCreateProfile], after the call is counted and before it
     * returns. Null (the default) leaves the fake synchronous, which is what every other test wants.
     *
     * It exists because some states only exist mid-bootstrap. Under an unconfined dispatcher the
     * whole chain — profile, user data, route — runs to completion inside the `emitState` call, so
     * "authenticated but not yet routed" is a window no test can stand in. Holding the first step
     * open is what makes that window observable.
     * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    var profileGate: CompletableDeferred<Unit>? = null

    override suspend fun getOrCreateProfile(session: UserSession): Result<UserProfile> {
        getOrCreateCallCount++
        lastSession = session
        profileGate?.await()
        getOrCreateResult.getOrNull()?.let {
            profiles[session.userId] = it
            _profileFlow.value = profiles.toMap()
        }
        return getOrCreateResult
    }

    override fun observeProfile(userId: String): Flow<UserProfile?> {
        observedUserId = userId
        return _profileFlow.map { it[userId] }
    }

    /** Every value handed to [setFirstStepsDismissedAt], newest last — a null being "the tutorial
     *  goes back on offer". [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001] */
    val firstStepsDismissedWrites = mutableListOf<Long?>()
    var firstStepsDismissedResult: Result<Unit> = Result.success(Unit)

    override suspend fun setFirstStepsDismissedAt(userId: String, dismissedAtMs: Long?): Result<Unit> {
        firstStepsDismissedWrites += dismissedAtMs
        return firstStepsDismissedResult
    }

    var deleteAllDataCallCount = 0
        private set
    var deleteAllDataResult: Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(userId: String): Result<Unit> {
        deleteAllDataCallCount++
        if (deleteAllDataResult.isSuccess) {
            profiles.clear()
            _profileFlow.value = emptyMap()
        }
        return deleteAllDataResult
    }

    companion object {
        fun defaultProfile(userId: String = "user-123") = UserProfile(
            userId = userId,
            email = "test@paparcar.io",
            displayName = "Test User",
            photoUrl = null,
            createdAt = 0L,
            updatedAt = 0L,
        )
    }
}
