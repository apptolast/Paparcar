package com.rndeveloper.paparcar.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.push.PushTokenProvider
import kotlinx.coroutines.withTimeoutOrNull
import com.rndeveloper.paparcar.domain.usecase.user.SignOutUseCase
import com.apptolast.baselogin.domain.model.AuthState
import com.rndeveloper.paparcar.Routes
import com.rndeveloper.paparcar.core.crash.CrashReporter
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityObserver
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityStatus
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.session.LocalSessionCache
import com.rndeveloper.paparcar.domain.usecase.user.BootstrapUserDataUseCase
import com.rndeveloper.paparcar.domain.model.UserProfile
import com.rndeveloper.paparcar.domain.usecase.user.GetOrCreateUserProfileUseCase
import com.rndeveloper.paparcar.domain.usecase.user.SetFirstStepsDismissedUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
     * Route to navigate to after any first-run setup screen completes (onboarding, or a stray
     * gate screen's fallback). Resolved during bootstrap: HOME if the user already has a vehicle,
     * VEHICLE_SIZE_EXPLAINER if they still need to register one. Navigation decision lives here,
     * not in App.kt. [NAV-001]
     */
    val postSetupRoute: String = Routes.HOME,
    /**
     * Non-null when bootstrap failed before resolving [startRoute]. The UI should surface a
     * recoverable error for [BootstrapFailure.Offline] (retry button) or redirect to login
     * for [BootstrapFailure.Fatal] (already handled by signOut → AuthState change).
     */
    val bootstrapFailure: BootstrapFailure? = null,
)

class SplashViewModel(
    private val authRepository: AuthRepository,
    /** Salir retira antes los tokens de push. [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001] */
    private val signOut: SignOutUseCase,
    /** Y una sesión que CADUCA tampoco puede dejar el token vivo.
     *  [PRIV-AN-EXPIRED-SESSION-STILL-OWNS-THE-TOKEN-001] */
    private val pushTokens: PushTokenProvider,
    private val getOrCreateUserProfile: GetOrCreateUserProfileUseCase,
    private val bootstrapUserData: BootstrapUserDataUseCase,
    private val vehicleRepository: VehicleRepository,
    private val appPreferences: AppPreferences,
    private val localSessionCache: LocalSessionCache,
    private val connectivityObserver: ConnectivityObserver,
    private val geofenceManager: GeofenceManager,
    private val entitlementRepository: EntitlementRepository,
    // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001] Used for the repair
    // direction of [reconcileFirstStepsMemory] — a dismissal this device knows and the account does
    // not, because the write happened offline.
    private val setFirstStepsDismissed: SetFirstStepsDismissedUseCase,
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
        // ⛔ Este es el hook de TODA transición a Unauthenticated, incluida la que nadie pidió: un
        // token caducado. El `SignOutUseCase` cubre las salidas por la puerta; esta cubre la
        // ventana, y sin ella el servidor seguiría teniendo un token vivo apuntando a un móvil
        // cuya sesión ya no existe. Con plazo, porque aquí no hay usuario esperando y una red mala
        // no puede dejar la app colgada en el arranque.
        // [PRIV-AN-EXPIRED-SESSION-STILL-OWNS-THE-TOKEN-001]
        runCatching { withTimeoutOrNull(TOKEN_RETIRE_TIMEOUT_MS) { pushTokens.retireToken() } }
            .onFailure { PaparcarLogger.w(TAG, "no se pudo invalidar el token al caducar la sesión", it) }
        // Drain OS-level geofences first. They live in Play Services / CoreLocation, not in Room,
        // so clearAllTables() alone leaves them registered: a parking geofence from the previous
        // user would keep monitoring and could fire an exit transition under the next account.
        // removeAllGeofences() drops them all by PendingIntent without needing the (about-to-be-wiped)
        // ids from Room. Best-effort — a failure here must not block the cache wipe. [SESSION-ISOLATION-001]
        runCatching { geofenceManager.removeAllGeofences() }
            .onFailure { e -> PaparcarLogger.e(TAG, "geofence drain failed", e) }
        runCatching { localSessionCache.wipe() }
            .onFailure { e -> PaparcarLogger.e(TAG, "Room wipe failed", e) }
        // Unbind the store identity too, so the next account on this device never inherits
        // this user's Plus. Best-effort like the rest of the wipe; on a logged-out cold
        // start the repository sees an anonymous profile and no-ops. [MONEY-001]
        entitlementRepository.logOut()
            .onFailure { e -> PaparcarLogger.w(TAG, "store identity logOut failed", e) }
        // The third local store, and the one this hook forgot for as long as it has existed: the
        // preferences. The guided tutorial, the first-park nudge counters and the two open
        // questions ("where did you leave your car?", "did you park?") all belonged to the user who
        // just left — and the last two name a vehicle the Room wipe above has just deleted.
        // What stays is what describes this phone; see `AppPreferences.clearUserScopedState`.
        // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
        runCatching { appPreferences.clearUserScopedState() }
            .onFailure { e -> PaparcarLogger.e(TAG, "preferences wipe failed", e) }
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

        // Bind the store identity to this uid so the Plus subscription follows the ACCOUNT.
        // Warm starts hit the same-uid guard inside (no network). Deliberately non-fatal:
        // a failed logIn must never block bootstrap or sign the user out — the entitlement
        // just stays as the store last knew it, whose safe side is FREE. [MONEY-001]
        entitlementRepository.logIn(session.userId)
            .onFailure { e -> PaparcarLogger.w(TAG, "store identity logIn failed", e) }

        // Fail-fast before touching Firestore: if the device is offline the round-trips will
        // time out anyway, but detecting it here gives a faster, differentiated error path
        // (no sign-out, retry available).
        //
        // But a RETURNING user already has their data cached in Room (the wipe only runs on
        // sign-out, not on every restart), so blocking them behind a retry wall on an offline
        // cold-start is needless friction: enter the app offline-first and let the connectivity
        // banner communicate the state, resolving the route straight from the local cache. Only
        // when there is NO cache (first launch offline — nothing to render and the profile can't
        // be created) do we keep the blocking retry dialog. [RETRY-GATE-001]
        if (connectivityObserver.status.value == ConnectivityStatus.Offline) {
            val hasCachedVehicle = runCatching { vehicleRepository.hasVehicles(session.userId) }
                .getOrDefault(false)
            if (hasCachedVehicle) {
                PaparcarLogger.i(TAG, "offline with cached data — entering offline-first (remote sync skipped)")
                resolveStartRoute(hasVehicle = true)
                return
            }
            PaparcarLogger.w(TAG, "bootstrap aborted — offline and no cached data")
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

            reconcileFirstStepsMemory(profile)

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

    /**
     * Makes the account and this device agree on whether the user still wants the guided checklist.
     * [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
     *
     * Runs here because this is the one moment that holds both halves: the freshly pulled profile
     * and the local preferences of the install that is booting. Two directions, and both are
     * needed:
     *
     *  - **account → device** is the fix this ticket is about. A reinstall, a restored backup or a
     *    new phone arrives with empty preferences, and without this the tutorial greets a user who
     *    finished it months ago — halfway through, because the live half of the projection ticks
     *    the steps their real parking already proves.
     *  - **device → account** repairs the write that never landed: the dismissal is stamped
     *    best-effort (offline, or a signed-out moment), so a device that knows and an account that
     *    does not is a state that has to heal by itself rather than wait for the user to close the
     *    checklist a second time.
     *
     * Never the reverse of the first direction: a null from the account can only fail to restore a
     * dismissal, never clear one. That is what makes it safe for the profile to come from the Room
     * fallback, where this field is not stored at all — the cost of a stale null is a re-stamped
     * timestamp, and nothing reads that value beyond "is it there".
     */
    private suspend fun reconcileFirstStepsMemory(profile: UserProfile) {
        val dismissedOnAccount = profile.firstStepsDismissedAt != null
        val dismissedOnDevice = appPreferences.observeFirstStepsDismissed().first()
        when {
            dismissedOnAccount && !dismissedOnDevice -> {
                PaparcarLogger.i(TAG, "first steps: account remembers the dismissal — restoring it locally")
                appPreferences.setFirstStepsDismissed(true)
            }
            !dismissedOnAccount && dismissedOnDevice -> {
                PaparcarLogger.i(TAG, "first steps: device knows, account does not — mirroring it up")
                setFirstStepsDismissed(true)
            }
        }
    }

    /** Single exit point for any fatal bootstrap failure: sign out and notify UI. */
    private suspend fun abortBootstrap() {
        _state.value = SplashState(bootstrapFailure = BootstrapFailure.Fatal)
        // Misma puerta que el logout de Ajustes: una salida forzada deja el móvil igual de expuesto
        // que una voluntaria. [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001]
        signOut()
        _effect.emit(SplashEffect.ShowError(PaparcarError.Auth.ProfileSyncFailed))
    }

    private fun resolveStartRoute(hasVehicle: Boolean) {
        val isOnboardingCompleted = appPreferences.isOnboardingCompleted

        // Destination once first-run setup is complete. Computed separately from route because
        // route may short-circuit to ONBOARDING before reaching the hasVehicle check.
        val postSetupRoute = if (hasVehicle) Routes.HOME else Routes.VEHICLE_SIZE_EXPLAINER

        // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001] No permission gate on cold start:
        // Home knows how to live without location (DetectionStory.BlockedCore), and the fine
        // location ask happens ON the map on first entry — the map is its own context. A gate
        // screen here was the app leading with locks instead of spots.
        val route = when {
            !isOnboardingCompleted -> Routes.ONBOARDING
            else -> postSetupRoute
        }
        PaparcarLogger.i(
            TAG,
            "startRoute decision — onboarding=$isOnboardingCompleted, " +
                "hasVehicle=$hasVehicle → route=$route",
        )
        _state.value = SplashState(startRoute = route, postSetupRoute = postSetupRoute)
    }

    private companion object {
        const val TAG = "SplashViewModel"

        /** Lo que se espera a invalidar un token al caducar la sesión. Nadie está mirando esta
         *  pantalla esperándolo, así que la red mala no puede convertirlo en un arranque colgado. */
        const val TOKEN_RETIRE_TIMEOUT_MS = 3_000L
    }
}
