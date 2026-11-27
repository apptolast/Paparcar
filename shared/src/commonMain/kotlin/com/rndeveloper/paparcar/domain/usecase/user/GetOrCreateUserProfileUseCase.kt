package com.rndeveloper.paparcar.domain.usecase.user

import com.apptolast.baselogin.domain.AuthRepository
import com.apptolast.baselogin.domain.model.AuthResult
import com.apptolast.baselogin.domain.model.UserSession
import com.rndeveloper.paparcar.domain.model.UserProfile
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Returns the existing user profile from Firestore or creates one from the active
 * session. Pure: no Room sync, no side effects beyond the profile doc itself —
 * user-scoped table sync lives in [BootstrapUserDataUseCase].
 */
class GetOrCreateUserProfileUseCase(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
) {
    suspend operator fun invoke(): Result<UserProfile> = runCatching {
        val session = freshestSession() ?: error("no authenticated session")
        userProfileRepository.getOrCreateProfile(session).getOrThrow()
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }

    /**
     * The cached session comes from Firebase Auth's local cache, which only learns about identity
     * changes made at the provider (a new Google photo) on a reload — and
     * [AuthRepository.refreshSession] is the verb that reloads (BaseLogin ≥ 2.0.1). Best-effort
     * and bounded: offline, failing or slow, the cached session is still a valid identity, just
     * possibly stale — the reconcile downstream retries on the next start.
     * [PROFILE-PHOTO-STUCK-AND-STALE-001]
     */
    private suspend fun freshestSession(): UserSession? {
        val cached = authRepository.getCurrentSession()
        if (cached == null) {
            PaparcarLogger.w(AUTH_DIAG, "no cached session")
            return null
        }
        val refreshed = withTimeoutOrNull(REFRESH_SESSION_TIMEOUT_MS.milliseconds) {
            runCatching { authRepository.refreshSession() }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    PaparcarLogger.w(AUTH_DIAG, "refreshSession threw", e)
                }
                .getOrNull()
        }
        PaparcarLogger.i(AUTH_DIAG, "refreshSession result=${refreshed?.let { it::class.simpleName } ?: "timeout/error"}")
        return (refreshed as? AuthResult.Success)?.session ?: cached
    }

    private companion object {
        /** Same splash-blocking ceiling philosophy as the remote profile fetch. [AUDIT-DATA-001 M9] */
        const val REFRESH_SESSION_TIMEOUT_MS = 8_000L

        const val AUTH_DIAG = "AUTHDIAG/GetOrCreateUserProfile"
    }
}
