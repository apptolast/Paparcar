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
import io.apptolast.paparcar.domain.session.LocalSessionCache
import io.apptolast.paparcar.domain.usecase.user.BootstrapUserDataUseCase
import io.apptolast.paparcar.domain.usecase.user.GetOrCreateUserProfileUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val bootstrapUserData: BootstrapUserDataUseCase,
    private val vehicleRepository: VehicleRepository,
    private val appPreferences: AppPreferences,
    private val permissionManager: PermissionManager,
    private val localSessionCache: LocalSessionCache,
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
            // Single collector serializes Unauthenticated → Authenticated transitions so
            // the Room wipe always completes before the next user's bootstrap writes new
            // rows. Without this ordering, a fast sign-in after sign-out could race with
            // the wipe and clear the freshly-synced data of the new user. authState is a
            // StateFlow, which already deduplicates equal emissions on its own.
            authState.collect { state ->
                when (state) {
                    is AuthState.Unauthenticated -> wipeLocalUserData()
                    is AuthState.Authenticated -> bootstrap(state)
                    else -> Unit  // Loading / Error — no side-effects.
                }
            }
        }
    }

    /**
     * Drops every Room table when the auth state transitions to Unauthenticated. Paparcar
     * treats local storage as the active user's cache only: the next sign-in repopulates
     * vehicles, zones and parking history from Firestore via [bootstrap]. Wiping here is
     * the single hook that guarantees no previous user's rows leak into the new session,
     * regardless of which path triggered the sign-out (Settings logout, DeleteAccount,
     * token expiry, splash-time profile sync failure). [SESSION-ISOLATION-001]
     */
    private suspend fun wipeLocalUserData() {
        PaparcarLogger.i(TAG, "auth Unauthenticated — wiping local Room cache")
        runCatching { localSessionCache.wipe() }
            .onFailure { e -> PaparcarLogger.e(TAG, "Room wipe failed", e) }
        // Reset splash state so the next Authenticated transition re-runs the bootstrap
        // (otherwise isReady would still see the stale startRoute from the previous user).
        _state.value = SplashState()
    }

    private suspend fun bootstrap(state: AuthState.Authenticated) {
        val session = state.session
        PaparcarLogger.i(TAG, "[step 1/3] auth Authenticated — userId=${session.userId}")
        CrashReporter.setUserId(session.userId)
        // Sequential bootstrap. Order matters:
        //   1. profile           — pulls / creates the UserProfile doc.
        //   2. bootstrapUserData — parallel sync of vehicles + parking history + zones.
        //   3. resolveRoute      — reads local state to decide the entry screen.
        // Any failure in (1) or (2) is fatal: we sign the user out and the
        // Unauthenticated transition takes them back to login. Paparcar requires
        // connectivity at login — partial data is never preferable to a clean retry.
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
                .onFailure { e -> PaparcarLogger.e(TAG, "profile sync FAILED — signing out", e) }
                .getOrElse {
                    abortBootstrap()
                    return
                }

            PaparcarLogger.i(TAG, "[step 3a/3] starting user-data bootstrap (parallel)")
            bootstrapUserData(session.userId)
                .onSuccess { PaparcarLogger.i(TAG, "user-data bootstrap OK") }
                .onFailure { e -> PaparcarLogger.e(TAG, "user-data bootstrap FAILED — signing out", e) }
                .getOrElse {
                    abortBootstrap()
                    return
                }

            PaparcarLogger.i(TAG, "[step 3b/3] resolving start route")
            // Read the vehicle count from Room (already populated by bootstrapUserData above)
            // rather than relying on profile.defaultVehicleId, which can be null on accounts
            // where the profile field was never set or was cleared across devices.
            val hasVehicle = vehicleRepository.hasVehicles(session.userId)
            resolveStartRoute(hasVehicle = hasVehicle)
        } catch (e: Throwable) {
            PaparcarLogger.e(TAG, "bootstrap chain failed with uncaught exception", e)
            abortBootstrap()
        }
    }

    /** Single exit point for any fatal bootstrap failure: sign out and notify UI. */
    private suspend fun abortBootstrap() {
        authRepository.signOut()
        _effect.emit(SplashEffect.ShowError(PaparcarError.Auth.ProfileSyncFailed))
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
