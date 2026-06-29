package io.apptolast.paparcar.fakes.data.repository

import com.apptolast.customlogin.domain.AuthRepository
import com.apptolast.customlogin.domain.model.*
import io.apptolast.paparcar.fakes.MockScenario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * @param scenario when non-null, the authenticated/unauthenticated state is driven by
 * [MockScenario.session] (so the Dev Catalog can exercise the login flow). When null the
 * fake keeps its original always-signed-in behaviour — used by tests and the default mock boot.
 */
class FakeAuthRepository(private val scenario: MockScenario? = null) : AuthRepository {
    private val mockUser = UserSession(
        userId = "mock_user_001",
        email = "rene@paparcar.mock",
        displayName = "Rene Dev",
        photoUrl = null
    )

    private val _authState = MutableStateFlow<AuthState>(AuthState.Authenticated(mockUser))

    override val currentProviderId: String = "mock"

    override fun observeAuthState(): Flow<AuthState> =
        if (scenario != null) {
            scenario.session.map { s ->
                if (s == MockScenario.Session.LoggedOut) AuthState.Unauthenticated
                else AuthState.Authenticated(mockUser)
            }
        } else {
            _authState.asStateFlow()
        }

    override suspend fun getCurrentSession(): UserSession? =
        if (scenario?.session?.value == MockScenario.Session.LoggedOut) null else mockUser

    override suspend fun signOut(): Result<Unit> {
        if (scenario != null) {
            scenario.session.value = MockScenario.Session.LoggedOut
        } else {
            _authState.value = AuthState.Unauthenticated
        }
        return Result.success(Unit)
    }

    override suspend fun isSignedIn(): Boolean =
        scenario?.session?.value?.let { it != MockScenario.Session.LoggedOut } ?: true

    override suspend fun getIdToken(forceRefresh: Boolean): String? = "mock_token"

    override suspend fun signIn(credentials: Credentials): AuthResult =
        AuthResult.Success(mockUser)

    override suspend fun signUp(data: SignUpData): AuthResult =
        AuthResult.Success(mockUser)

    override suspend fun sendPasswordResetEmail(email: String): AuthResult =
        AuthResult.PasswordResetSent

    override suspend fun confirmPasswordReset(data: PasswordResetData): AuthResult =
        AuthResult.PasswordResetSuccess

    override suspend fun refreshSession(): AuthResult =
        AuthResult.Success(mockUser)

    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

    override suspend fun updateDisplayName(displayName: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateEmail(newEmail: String): Result<Unit> = Result.success(Unit)

    override suspend fun updatePassword(newPassword: String): Result<Unit> = Result.success(Unit)

    override suspend fun sendEmailVerification(): Result<Unit> = Result.success(Unit)

    override suspend fun reauthenticate(credentials: Credentials): AuthResult =
        AuthResult.Success(mockUser)

    override fun getAvailableProviders(): List<IdentityProvider> = emptyList()

    override suspend fun sendPhoneOtp(phoneNumber: String): PhoneAuthResult =
        PhoneAuthResult.CodeSent("mock_verification_id")

    override suspend fun verifyPhoneOtp(verificationId: String, otpCode: String): AuthResult =
        AuthResult.Success(mockUser)

    override suspend fun sendMagicLink(email: String): AuthResult = AuthResult.MagicLinkSent

    override suspend fun signInWithMagicLink(email: String, link: String): AuthResult =
        AuthResult.Success(mockUser)
}
