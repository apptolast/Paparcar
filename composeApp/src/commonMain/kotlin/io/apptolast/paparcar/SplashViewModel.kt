package io.apptolast.paparcar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptolast.customlogin.domain.AuthRepository
import com.apptolast.customlogin.domain.model.AuthState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for managing splash screen state and providing a single source of truth for the authentication state.
 */
class SplashViewModel(
    authRepository: AuthRepository
) : ViewModel() {

    /**
     * The single source of truth for the authentication state.
     * The UI layer observes this to decide which main screen to show (Auth or Home).
     * Started eagerly to ensure the auth check runs immediately on app start, avoiding race conditions with the splash screen.
     */
    val authState: StateFlow<AuthState> = authRepository.observeAuthState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AuthState.Loading
        )

    /**
     * A flag to indicate if the initial authentication check is complete.
     * Used by the native Android splash screen to stay on screen until the app is ready.
     */
    val isReady: StateFlow<Boolean> = authState
        .map { it !is AuthState.Loading }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )
}