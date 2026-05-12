package io.apptolast.paparcar.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptolast.customlogin.domain.AuthRepository
import com.apptolast.customlogin.domain.model.AuthState
import io.apptolast.paparcar.Routes
import io.apptolast.paparcar.core.crash.CrashReporter
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.usecase.user.GetOrCreateUserProfileUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class SplashEffect {
    /** Emitted when profile sync fails unrecoverably — UI should show a snackbar and redirect to login. */
    data class ShowError(val error: PaparcarError) : SplashEffect()
}

data class SplashState(
    /**
     * Null while the splash is still computing the entry point. The native splash screen
     * stays visible during this window (via [isReady]).
     *
     * Resolved exactly once per [AuthState.Authenticated] session, after:
     *  - profile sync succeeds,
     *  - the first emission of [VehicleRepository.observeVehicles] (which performs lazy
     *    Firestore sync when Room is empty — see VehicleRepositoryImpl).
     */
    val startRoute: String? = null,
)

class SplashViewModel(
    private val authRepository: AuthRepository,
    private val getOrCreateUserProfile: GetOrCreateUserProfileUseCase,
    private val vehicleRepository: VehicleRepository,
    private val appPreferences: AppPreferences,
    private val permissionManager: PermissionManager,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.observeAuthState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AuthState.Loading,
        )

    private val _state = MutableStateFlow(SplashState())
    val state: StateFlow<SplashState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<SplashEffect>()
    val effect = _effect.asSharedFlow()

    /**
     * Checked synchronously by the native Android splash screen condition.
     * Returns true once:
     *  - the auth state is resolved AND
     *  - if Authenticated, the start route has been computed (vehicle + prefs + permissions all read).
     */
    val isReady: Boolean
        get() = when (authState.value) {
            is AuthState.Loading -> false
            is AuthState.Authenticated -> _state.value.startRoute != null
            // Unauthenticated states use the AuthNavigation flow — no startRoute needed.
            else -> true
        }

    init {
        PaparcarLogger.i(TAG, "init — subscribing to authState")
        viewModelScope.launch {
            authState
                .filter { it is AuthState.Authenticated }
                .distinctUntilChanged()
                .catch { e -> PaparcarLogger.e(TAG, "authState stream error", e) }
                .collect { state ->
                    val session = (state as? AuthState.Authenticated)?.session ?: return@collect
                    PaparcarLogger.i(TAG, "[step 1/3] auth Authenticated — userId=${session.userId}")
                    CrashReporter.setUserId(session.userId)
                    // Sequential bootstrap. Order matters:
                    //   1. profile sync   — pulls / creates the UserProfile doc.
                    //   2. vehicle sync   — populates Room from the user's vehicles subcollection.
                    //   3. resolveRoute   — reads local state to decide the entry screen.
                    // try/catch is the safety net so any uncaught exception in the chain
                    // (network glitch, deserialization bug, etc.) cannot crash the app.
                    try {
                        PaparcarLogger.i(TAG, "[step 2/3] starting profile sync")
                        val profile = getOrCreateUserProfile()
                            .onSuccess { p ->
                                PaparcarLogger.i(
                                    TAG,
                                    "profile sync OK — defaultVehicleId=${p.defaultVehicleId}, " +
                                        "displayName=${p.displayName}",
                                )
                            }
                            .onFailure { e ->
                                PaparcarLogger.e(TAG, "profile sync FAILED — signing out", e)
                                authRepository.signOut()
                                _effect.emit(SplashEffect.ShowError(PaparcarError.Auth.ProfileSyncFailed))
                            }
                            .getOrNull() ?: return@collect

                        PaparcarLogger.i(TAG, "[step 3a/3] starting vehicle sync")
                        vehicleRepository.syncFromRemote(session.userId)
                            .onSuccess { PaparcarLogger.i(TAG, "vehicle sync OK") }
                            .onFailure { e -> PaparcarLogger.w(TAG, "vehicle sync FAILED (continuing with cached state)", e) }

                        PaparcarLogger.i(TAG, "[step 3b/3] resolving start route")
                        resolveStartRoute(hasVehicle = profile.defaultVehicleId != null)
                    } catch (e: Throwable) {
                        PaparcarLogger.e(TAG, "bootstrap chain failed with uncaught exception", e)
                        _effect.emit(SplashEffect.ShowError(PaparcarError.Auth.ProfileSyncFailed))
                    }
                }
        }
    }

    private fun resolveStartRoute(hasVehicle: Boolean) {
        val isOnboardingCompleted = appPreferences.isOnboardingCompleted
        val perms = permissionManager.permissionState.value

        val route = when {
            // First-run flow is linear:
            //   Onboarding → PermissionsRationale → Permissions → VehicleRegistration → Home.
            // Permissions come before vehicle registration because:
            //  (a) the Onboarding narrative ends in "set up permissions", so the next screen
            //      should be the rationale, not a coche form.
            //  (b) registering a vehicle is trivial post-permissions and doesn't need consent.
            //  (c) if the user rejects permissions, the app cannot function — so we don't waste
            //      their effort collecting vehicle data first.
            !isOnboardingCompleted -> Routes.ONBOARDING
            !perms.allPermissionsGranted -> Routes.PERMISSIONS_RATIONALE
            !perms.isLocationServicesEnabled -> Routes.PERMISSIONS
            // The explainer is the visible step; it leads to VEHICLE_REGISTRATION on continue.
            !hasVehicle -> Routes.VEHICLE_SIZE_EXPLAINER
            else -> Routes.HOME
        }
        PaparcarLogger.i(
            TAG,
            "startRoute decision — onboarding=$isOnboardingCompleted, " +
                "hasVehicle=$hasVehicle, " +
                "permsGranted=${perms.allPermissionsGranted}, " +
                "gpsEnabled=${perms.isLocationServicesEnabled} " +
                "→ route=$route",
        )
        _state.value = SplashState(startRoute = route)
    }

    private companion object {
        const val TAG = "SplashViewModel"
    }
}
