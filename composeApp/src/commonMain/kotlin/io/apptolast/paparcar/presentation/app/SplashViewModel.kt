package io.apptolast.paparcar.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptolast.customlogin.domain.AuthRepository
import com.apptolast.customlogin.domain.model.AuthState
import io.apptolast.paparcar.Routes
import io.apptolast.paparcar.core.crash.CrashReporter
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.service.GeofenceManager
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
import kotlin.concurrent.Volatile

sealed class SplashEffect {
    /** Profile sync failed unrecoverably — UI should show a snackbar; user was signed out. */
    data class ShowError(val error: PaparcarError) : SplashEffect()
    /** Bootstrap failed because the device is offline — user was NOT signed out; retry is available. */
    data object ShowOfflineError : SplashEffect()
}

/** Non-null when bootstrap failed and [SplashViewModel.retry] is available. */
sealed class BootstrapFailure {
    /** Device offline at login time — user was not signed out, retry when back online. */
    data object Offline : BootstrapFailure()
    /** Fatal error (auth/data) — user was signed out, must re-login. */
    data object Fatal : BootstrapFailure()
}

data class SplashState(
    /**
     * Null while the splash is still computing the entry point. The native splash screen
     * stays visible during this window (via [isReady]).
     */
    val startRoute: String? = null,
    /**
     * Route to navigate to after the user completes the permissions + GPS-disclaimer flow.
     * Resolved during bootstrap: HOME if the user already has a vehicle, VEHICLE_SIZE_EXPLAINER
     * if they still need to register one. Navigation decision lives here, not in App.kt. [NAV-001]
     */
    val postPermissionsRoute: String = Routes.HOME,
    /**
     * Non-null when bootstrap failed before resolving [startRoute]. The UI should surface a
     * recoverable error for [BootstrapFailure.Offline] (retry button) or redirect to login
     * for [BootstrapFailure.Fatal] (already handled by signOut → AuthState change).
     */
    val bootstrapFailure: BootstrapFailure? = null,
)

class SplashViewModel(
    private val authRepository: AuthRepository,
    private val getOrCreateUserProfile: GetOrCreateUserProfileUseCase,
    private val bootstrapUserData: BootstrapUserDataUseCase,
    private val vehicleRepository: VehicleRepository,
    private val appPreferences: AppPreferences,
    private val permissionManager: PermissionManager,
    private val localSessionCache: LocalSessionCache,
    private val connectivityObserver: ConnectivityObserver,
    private val geofenceManager: GeofenceManager,
) : ViewModel() {

    /** Kept so [retry] can re-enter the bootstrap chain without waiting for a new auth emission. */
    @Volatile
    private var lastAuthState: AuthState.Authenticated? = null

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
     *  - if Authenticated, either startRoute is computed OR bootstrap has failed
     *    (offline/fatal — App will show recovery UI instead of remaining on blank screen).
     */
    val isReady: Boolean
        get() = when (authState.value) {
            is AuthState.Loading -> false
            is AuthState.Authenticated -> _state.value.startRoute != null || _state.value.bootstrapFailure != null
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
        PaparcarLogger.i(TAG, "auth Unauthenticated — draining geofences + wiping local Room cache")
        // Drain OS-level geofences first. They live in Play Services / CoreLocation, not in Room,
        // so clearAllTables() alone leaves them registered: a parking geofence from the previous
        // user would keep monitoring and could fire an exit transition under the next account.
        // removeAllGeofences() drops them all by PendingIntent without needing the (about-to-be-wiped)
        // ids from Room. Best-effort — a failure here must not block the cache wipe. [SESSION-ISOLATION-001]
        runCatching { geofenceManager.removeAllGeofences() }
            .onFailure { e -> PaparcarLogger.e(TAG, "geofence drain failed", e) }
        runCatching { localSessionCache.wipe() }
            .onFailure { e -> PaparcarLogger.e(TAG, "Room wipe failed", e) }
        // Reset splash state so the next Authenticated transition re-runs the bootstrap
        // (otherwise isReady would still see the stale startRoute from the previous user).
        _state.value = SplashState()
    }

    /** Re-runs bootstrap after an [BootstrapFailure.Offline] failure without requiring re-login. */
    fun retry() {
        val state = lastAuthState ?: return
        viewModelScope.launch {
            _state.value = SplashState()  // clear failure, reset to loading state
            bootstrap(state)
        }
    }

    private suspend fun bootstrap(state: AuthState.Authenticated) {
        lastAuthState = state
        val session = state.session
        PaparcarLogger.i(TAG, "[step 1/3] auth Authenticated — userId=${session.userId}")
        CrashReporter.setUserId(session.userId)

        // Fail-fast before touching Firestore: if the device is offline the round-trips will
        // time out anyway, but detecting it here gives a faster, differentiated error path
        // (no sign-out, retry available).
        if (connectivityObserver.status.value == ConnectivityStatus.Offline) {
            PaparcarLogger.w(TAG, "bootstrap aborted — device offline")
            _state.value = SplashState(bootstrapFailure = BootstrapFailure.Offline)
            _effect.emit(SplashEffect.ShowOfflineError)
            return
        }

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
        _state.value = SplashState(bootstrapFailure = BootstrapFailure.Fatal)
        authRepository.signOut()
        _effect.emit(SplashEffect.ShowError(PaparcarError.Auth.ProfileSyncFailed))
    }

    private fun resolveStartRoute(hasVehicle: Boolean) {
        val isOnboardingCompleted = appPreferences.isOnboardingCompleted
        val perms = permissionManager.permissionState.value

        // Destination once the full onboarding+permissions flow is complete.
        // Computed separately from route because route may short-circuit to ONBOARDING
        // before reaching the hasVehicle check — the two are independent decisions.
        val postPermissionsRoute = if (hasVehicle) Routes.HOME else Routes.VEHICLE_SIZE_EXPLAINER

        // [DET-READY-001d] Cold-start gate on CORE only: a returning user who granted CORE but
        // not PRODUCER lands straight on Home (banner nudges them to enable detection) instead of
        // being re-routed through the permission rationale on every launch.
        val route = when {
            !isOnboardingCompleted -> Routes.ONBOARDING
            // Single explain-and-grant permissions surface (rationale screen retired). [ONB-SCAFFOLD-001]
            !perms.hasCorePermissions -> Routes.PERMISSIONS
            !perms.isLocationServicesEnabled -> Routes.PERMISSIONS
            else -> postPermissionsRoute
        }
        PaparcarLogger.i(
            TAG,
            "startRoute decision — onboarding=$isOnboardingCompleted, " +
                "hasVehicle=$hasVehicle, " +
                "coreGranted=${perms.hasCorePermissions}, " +
                "gpsEnabled=${perms.isLocationServicesEnabled} " +
                "→ route=$route",
        )
        _state.value = SplashState(startRoute = route, postPermissionsRoute = postPermissionsRoute)
    }

    private companion object {
        const val TAG = "SplashViewModel"
    }
}
