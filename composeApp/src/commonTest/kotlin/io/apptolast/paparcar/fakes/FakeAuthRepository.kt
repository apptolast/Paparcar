package io.apptolast.paparcar.fakes

import com.apptolast.customlogin.domain.AuthRepository
import com.apptolast.customlogin.domain.model.AuthState
import com.apptolast.customlogin.domain.model.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository(
    initialSession: UserSession? = null,
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(
        if (initialSession != null) AuthState.Authenticated(initialSession) else AuthState.Unauthenticated,
    )
    private var _currentSession: UserSession? = initialSession

    var signOutCount = 0
        private set

    override fun observeAuthState(): Flow<AuthState> = _authState.asStateFlow()

    override suspend fun getCurrentSession(): UserSession? = _currentSession

    override suspend fun signOut() {
        signOutCount++
        _currentSession = null
        _authState.value = AuthState.Unauthenticated
    }

    fun emitState(state: AuthState) {
        _authState.value = state
        _currentSession = (state as? AuthState.Authenticated)?.session
    }

    companion object {
        fun authenticatedSession(
            userId: String = "user-123",
            email: String = "test@paparcar.io",
            displayName: String = "Test User",
        ) = UserSession(userId = userId, email = email, displayName = displayName, photoUrl = null)
    }
}
