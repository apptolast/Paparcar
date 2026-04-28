package io.apptolast.paparcar.fakes

import com.apptolast.customlogin.domain.AuthRepository
import com.apptolast.customlogin.domain.model.AuthResult
import com.apptolast.customlogin.domain.model.AuthState
import com.apptolast.customlogin.domain.model.Credentials
import com.apptolast.customlogin.domain.model.AuthError
import com.apptolast.customlogin.domain.model.IdentityProvider
import com.apptolast.customlogin.domain.model.PasswordResetData
import com.apptolast.customlogin.domain.model.PhoneAuthResult
import com.apptolast.customlogin.domain.model.SignUpData
import com.apptolast.customlogin.domain.model.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository(
    initialSession: UserSession? = null,
    initialState: AuthState = if (initialSession != null) AuthState.Authenticated(initialSession) else AuthState.Unauthenticated,
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(initialState)
    private var _currentSession: UserSession? = (initialState as? AuthState.Authenticated)?.session ?: initialSession

    var signOutCount = 0
        private set
    var deleteAccountCallCount = 0
        private set
    var deleteAccountResult: Result<Unit> = Result.success(Unit)

    override val currentProviderId: String = "fake"

    override fun observeAuthState(): Flow<AuthState> = _authState.asStateFlow()

    override suspend fun getCurrentSession(): UserSession? = _currentSession

    override suspend fun signOut(): Result<Unit> {
        signOutCount++
        _currentSession = null
        _authState.value = AuthState.Unauthenticated
        return Result.success(Unit)
    }

    override suspend fun isSignedIn(): Boolean = _currentSession != null

    override suspend fun getIdToken(forceRefresh: Boolean): String? = null

    // ── Stubs — not used in tests ─────────────────────────────────────────────

    override suspend fun signIn(credentials: Credentials): AuthResult =
        AuthResult.Failure(AuthError.Unknown("stub"))

    override suspend fun signUp(data: SignUpData): AuthResult =
        AuthResult.Failure(AuthError.Unknown("stub"))

    override suspend fun sendPasswordResetEmail(email: String): AuthResult =
        AuthResult.PasswordResetSent

    override suspend fun confirmPasswordReset(data: PasswordResetData): AuthResult =
        AuthResult.PasswordResetSuccess

    override suspend fun refreshSession(): AuthResult =
        AuthResult.Failure(AuthError.Unknown("stub"))

    override suspend fun deleteAccount(): Result<Unit> {
        deleteAccountCallCount++
        return deleteAccountResult
    }

    override suspend fun updateDisplayName(displayName: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateEmail(newEmail: String): Result<Unit> = Result.success(Unit)

    override suspend fun updatePassword(newPassword: String): Result<Unit> = Result.success(Unit)

    override suspend fun sendEmailVerification(): Result<Unit> = Result.success(Unit)

    override suspend fun reauthenticate(credentials: Credentials): AuthResult =
        AuthResult.Failure(AuthError.Unknown("stub"))

    override fun getAvailableProviders(): List<IdentityProvider> = emptyList()

    override suspend fun sendPhoneOtp(phoneNumber: String): PhoneAuthResult =
        PhoneAuthResult.Failure(AuthError.Unknown("stub"))

    override suspend fun verifyPhoneOtp(verificationId: String, otpCode: String): AuthResult =
        AuthResult.Failure(AuthError.Unknown("stub"))

    override suspend fun sendMagicLink(email: String): AuthResult = AuthResult.MagicLinkSent

    override suspend fun signInWithMagicLink(email: String, link: String): AuthResult =
        AuthResult.Failure(AuthError.Unknown("stub"))

    // ── Test helpers ──────────────────────────────────────────────────────────

    fun emitState(state: AuthState) {
        _currentSession = (state as? AuthState.Authenticated)?.session
        _authState.value = state
    }

    companion object {
        fun authenticatedSession(
            userId: String = "user-123",
            email: String = "test@paparcar.io",
            displayName: String = "Test User",
        ) = UserSession(userId = userId, email = email, displayName = displayName, photoUrl = null)
    }
}
