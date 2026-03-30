package io.apptolast.paparcar.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptolast.customlogin.domain.AuthRepository
import com.apptolast.customlogin.domain.model.AuthState
import io.apptolast.paparcar.domain.usecase.user.GetOrCreateUserProfileUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class SplashEffect {
    /** Emitted when profile sync fails unrecoverably — UI should show a snackbar and redirect to login. */
    data class ShowError(val message: String) : SplashEffect()
}

class SplashViewModel(
    private val authRepository: AuthRepository,
    private val getOrCreateUserProfile: GetOrCreateUserProfileUseCase,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.observeAuthState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AuthState.Loading,
        )

    private val _effect = MutableSharedFlow<SplashEffect>()
    val effect = _effect.asSharedFlow()

    /**
     * Checked synchronously by the native Android splash screen condition.
     * Returns true once the initial auth check has resolved (Loading → any other state).
     */
    val isReady: Boolean get() = authState.value !is AuthState.Loading

    init {
        viewModelScope.launch {
            authState
                .filter { it is AuthState.Authenticated }
                .distinctUntilChanged()
                .catch { e -> PaparcarLogger.e(TAG, "authState stream error", e) }
                .collect {
                    getOrCreateUserProfile()
                        .onFailure { e ->
                            PaparcarLogger.e(TAG, "profile sync failed", e)
                            authRepository.signOut()
                            _effect.emit(SplashEffect.ShowError(ERROR_PROFILE_SYNC))
                        }
                }
        }
    }

    private companion object {
        const val TAG = "SplashViewModel"
        const val ERROR_PROFILE_SYNC =
            "Could not load your profile. Please sign in again."
    }
}
