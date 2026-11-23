package com.rndeveloper.paparcar.presentation.settings

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.usecase.user.SignOutUseCase
import com.rndeveloper.paparcar.domain.analytics.AdMeasurementConsent
import com.rndeveloper.paparcar.domain.analytics.AdMeasurementLane
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.usecase.analytics.SetAdMeasurementConsentUseCase
import com.rndeveloper.paparcar.core.crash.CrashReporter
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.permissions.missingPermissions
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import com.rndeveloper.paparcar.domain.service.WidgetPinRequester
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDetectionReliabilityUseCase
import com.rndeveloper.paparcar.domain.usecase.diagnostics.SendDiagnosticsReportUseCase
import com.rndeveloper.paparcar.domain.model.PlusOrigin
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObservePlusGrantUseCase
import com.rndeveloper.paparcar.domain.usecase.user.DeleteAccountUseCase
import com.rndeveloper.paparcar.domain.usecase.user.SetFirstStepsDismissedUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import com.rndeveloper.paparcar.presentation.permissions.PermissionsFocus
import com.rndeveloper.paparcar.presentation.permissions.detectionPermissionGap
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefs: AppPreferences,
    private val authRepository: AuthRepository,
    /** Salir retira antes los tokens de push. [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001] */
    private val signOut: SignOutUseCase,
    private val userProfileRepository: UserProfileRepository,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001] The same verb Home
    // uses to close the checklist, run backwards to put it back on offer — on this device and on
    // the account, which is what makes the replay survive the next cold start.
    private val setFirstStepsDismissed: SetFirstStepsDismissedUseCase,
    private val permissionManager: PermissionManager,
    private val vehicleRepository: VehicleRepository,
    private val observeDetectionReliability: ObserveDetectionReliabilityUseCase,
    private val sendDiagnosticsReport: SendDiagnosticsReportUseCase,
    private val observePlusGrant: ObservePlusGrantUseCase,
    // [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001] Where a yes to ad measurement is taken back
    // (or given late). Same door as the one-time prompt, so both switch the lane the same way.
    private val adMeasurementLane: AdMeasurementLane,
    private val setAdMeasurementConsent: SetAdMeasurementConsentUseCase,
    // Android door for pinning the widget from here; null on platforms/launchers without it —
    // the row simply does not exist. [WIDGET-ACTIONS-002]
    private val widgetPinRequester: WidgetPinRequester? = null,
    private val analyticsTracker: AnalyticsTracker? = null,
) : BaseViewModel<SettingsState, SettingsIntent, SettingsEffect>() {

    override fun initState(): SettingsState = SettingsState()

    init {
        updateState {
            copy(
                canPinWidget = widgetPinRequester?.isPinSupported == true,
                adMeasurementAvailable = adMeasurementLane.isAvailable,
            )
        }
        loadFromPreferences()
        observePermissions()
        observeActiveVehicle()
        observeReliability()
        observeEntitlement()
        refreshEmailVerification()
        viewModelScope.launch {
            val userId = authRepository.getCurrentSession()?.userId
            if (userId != null) {
                userProfileRepository.observeProfile(userId)
                    .onEach { profile -> updateState { copy(userProfile = profile) } }
                    .catch { e -> PaparcarLogger.w(TAG, "Failed to observe profile", e) }
                    .launchIn(viewModelScope)
            }
        }
    }

    /**
     * Lee del AUTH si el correo está confirmado, recargando ANTES la sesión. [AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001]
     *
     * ⛔ El `refreshSession()` no es un extra: sin él, quien pulsa el enlace del correo y vuelve a
     * la app seguiría leyendo «sin confirmar» **para siempre**, porque `isEmailVerified` viene de la
     * sesión cacheada y nada la invalida.
     *
     * Best-effort, con el mismo patrón que ya usa `GetOrCreateUserProfileUseCase`: si la recarga
     * falla, el flag se queda **como estaba**. No se invierte ni se borra — eso sería afirmar algo
     * que no sabemos, y pintaría «sin confirmar» a quien sí lo tiene.
     */
    private fun refreshEmailVerification() {
        viewModelScope.launch {
            runCatching { authRepository.refreshSession() }
                .onFailure { e -> PaparcarLogger.w(TAG, "refreshSession threw", e) }
            val verified = authRepository.getCurrentSession()?.isEmailVerified
            if (verified != null) updateState { copy(emailVerified = verified) }
        }
    }

    /**
     * Manda el correo de confirmación. [AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001]
     *
     * ⛔ Directo al repositorio y sin caso de uso: el criterio de este ViewModel — escrito en su
     * propio [SignOutUseCase] — es *¿orquesta consecuencias?*. Salir sí (retira los tokens de push);
     * esto no toca Room, ni Firestore, ni push, ni geocercas. Un caso de uso aquí sería un
     * passthrough. [DET-VERDICT-NOT-PREDICATE-001]
     *
     * El éxito NO marca el correo como confirmado: lo único que sabemos es que el enlace salió.
     * Confirmarlo es cosa del usuario, y lo detecta [refreshEmailVerification] al volver.
     */
    private fun sendEmailVerification() {
        if (state.value.isSendingEmailVerification || state.value.emailVerificationSent) return
        updateState { copy(isSendingEmailVerification = true) }
        viewModelScope.launch {
            authRepository.sendEmailVerification()
                .onSuccess {
                    updateState { copy(isSendingEmailVerification = false, emailVerificationSent = true) }
                }
                .onFailure { e ->
                    updateState { copy(isSendingEmailVerification = false) }
                    PaparcarLogger.e(TAG, "Failed to send the verification email", e)
                    sendEffect(SettingsEffect.ShowError(PaparcarError.Auth.VerificationEmailFailed))
                }
        }
    }

    /**
     * Mirrors [PermissionManager.permissionState] into the detection-health fields. Reactive, so a
     * grant made in the permissions screen (or system settings) reflects the moment the manager is
     * refreshed on resume. [SETTINGS-REMODEL-001]
     */
    private fun observePermissions() {
        permissionManager.permissionState
            .onEach { perms ->
                updateState {
                    copy(
                        missingDetectionPermissions = perms.missingPermissions(),
                        isLocationServicesEnabled = perms.isLocationServicesEnabled,
                        isBatteryOptimizationExempt = perms.isBatteryOptimizationExempt,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** Tracks the active vehicle so the BT-improvement row knows its target + configured state. */
    private fun observeActiveVehicle() {
        vehicleRepository.observeActiveVehicle()
            .onEach { vehicle ->
                updateState {
                    copy(
                        activeVehicleId = vehicle?.id,
                        btDeviceConfigured = vehicle?.bluetoothDeviceId != null,
                    )
                }
            }
            .catch { e -> PaparcarLogger.w(TAG, "Failed to observe active vehicle", e) }
            .launchIn(viewModelScope)
    }

    /** Live Plus entitlement — the Plus row and the zone-alert gate react without re-entering. [MONEY-001] */
    private fun observeEntitlement() {
        // ⛔ El GRANT y no sólo `isPlus`: la fila tiene que poder decir que este Plus es un REGALO
        // y hasta cuándo, y seguir siendo puerta mientras haya algo que decidir.
        // [MONEY-FOUNDERS-WELCOME-001]
        observePlusGrant()
            .onEach { grant ->
                updateState {
                    copy(
                        isPlus = grant != null,
                        plusPromoEndsAtMillis = grant
                            ?.takeIf { it.origin == PlusOrigin.PROMOTIONAL }
                            ?.expiresAtMillis,
                    )
                }
            }
            .catch { e -> PaparcarLogger.w(TAG, "Failed to observe entitlement", e) }
            .launchIn(viewModelScope)
    }

    /** Mirrors the single reliability evaluator into the health row. [DET-RELIABILITY-001] */
    private fun observeReliability() {
        observeDetectionReliability()
            .onEach { report -> updateState { copy(detectionReliability = report.level) } }
            .catch { e -> PaparcarLogger.w(TAG, "Failed to observe detection reliability", e) }
            .launchIn(viewModelScope)
    }

    /**
     * Snapshot-reads every pref-backed field into state. Called from [init]
     * and again from the screen via [refreshFromPreferences] every time the
     * user navigates back to Settings — covers the case where another screen
     * (e.g. Vehicles, Bluetooth config) mutates a pref while Settings was
     * off-screen. AppPreferences doesn't expose Flow accessors yet, so a
     * pull-on-resume strategy is the cheapest way to keep state fresh.
     */
    private fun loadFromPreferences() {
        updateState {
            copy(
                autoDetectParking = prefs.autoDetectParking,
                notifyParkingDetected = prefs.notifyParkingDetected,
                notifyZoneSpots = prefs.notifyZoneSpots,
                notifyFuelPrices = prefs.notifyFuelPrices,
                notifyMonthRecap = prefs.notifyMonthRecap,
                syncRouteGeometry = prefs.syncRouteGeometry,
                adMeasurementAllowed = prefs.adMeasurementConsent == AdMeasurementConsent.GRANTED,
            )
        }
    }

    /**
     * Public hook used by [SettingsScreen] from a `LaunchedEffect(Unit)`. Also re-queries runtime
     * permissions so the detection-health row is fresh when the user returns from the permissions
     * flow / system settings (permission grants can change while off-screen).
     */
    fun refreshFromPreferences() {
        loadFromPreferences()
        permissionManager.refreshPermissions()
    }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            // [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001] The switch reports
            // `autoDetectActive` (intent AND permissions), so turning it ON while detection is
            // blocked is an ASK, not a claim: bank the intent and open the very flow that grants
            // what is missing — the same single action Home's `EnableAutoDetection` performs, so
            // both surfaces bring detection online in one tap whatever was in the way.
            is SettingsIntent.ToggleAutoDetect -> {
                prefs.setAutoDetectParking(intent.enabled)
                updateState { copy(autoDetectParking = intent.enabled) }
                val gap = detectionGapFocus()
                when {
                    intent.enabled && gap != null -> sendEffect(SettingsEffect.NavigateToPermissions(gap))
                    // Confirm a turn-OFF at the point of action with a one-tap undo. [DET-TOGGLE-002]
                    // Only reachable while the switch reads ON, i.e. while detection really ran.
                    !intent.enabled -> sendEffect(SettingsEffect.DetectionTurnedOff)
                }
            }
            is SettingsIntent.ToggleParkingDetectedNotif -> {
                prefs.setNotifyParkingDetected(intent.enabled)
                updateState { copy(notifyParkingDetected = intent.enabled) }
            }
            // [MONEY-001] Soft gate: enabling as free is the attempt that opens the paywall.
            // Disabling is always allowed — silencing a channel needs no entitlement.
            is SettingsIntent.ToggleZoneAlertsNotif -> {
                if (intent.enabled && !state.value.isPlus) {
                    sendEffect(SettingsEffect.NavigateToPaywall(PaywallReason.ZONE_ALERTS))
                } else {
                    prefs.setNotifyZoneSpots(intent.enabled)
                    updateState { copy(notifyZoneSpots = intent.enabled) }
                    analyticsTracker?.track(
                        AnalyticsEvent.NotificationChannelToggled(PaywallReason.ZONE_ALERTS, intent.enabled),
                    )
                }
            }
            // [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] The same soft gate, the same
            // asymmetry: turning the watch ON is the paywall moment, turning it off never is.
            // ⛔ What this does NOT touch is which stations are followed — those stay starred
            // through a lapse, so regaining Plus resumes the same watch instead of an empty one.
            // [MONEY-A-CARD-THAT-NEVER-APPEARS-IS-NEVER-SOLD-001] La misma asimetría que sus dos
            // vecinas: encenderlo siendo free es el intento que abre el paywall, apagarlo se
            // permite siempre. ⛔ Y su motivo es PROPIO, no el de cifras: querer que te CUENTEN
            // cómo fue el mes y querer ELEGIR un periodo son dos deseos distintos, y separarlos es
            // el trabajo entero del embudo.
            is SettingsIntent.ToggleMonthRecapNotif -> {
                if (intent.enabled && !state.value.isPlus) {
                    sendEffect(SettingsEffect.NavigateToPaywall(PaywallReason.MONTH_RECAP))
                } else {
                    prefs.setNotifyMonthRecap(intent.enabled)
                    updateState { copy(notifyMonthRecap = intent.enabled) }
                    analyticsTracker?.track(
                        AnalyticsEvent.NotificationChannelToggled(PaywallReason.MONTH_RECAP, intent.enabled),
                    )
                }
            }
            // ⛔ **Sin paywall, y es el cambio de plan hecho interruptor.**
            // [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001] Desde el 13-10 el aviso de lo que sube o baja
            // a TU ALREDEDOR es free, así que un free que enciende esto recibe algo de verdad. Si
            // siguiera abriendo el paywall, la fila prometería Plus para activar una función que ya
            // es suya. Lo que vende Plus se pide donde se encuentra el límite: la estrella de una
            // gasolinera, que vigila un sujeto esté donde esté.
            is SettingsIntent.ToggleFuelAlertsNotif -> {
                prefs.setNotifyFuelPrices(intent.enabled)
                updateState { copy(notifyFuelPrices = intent.enabled) }
                analyticsTracker?.track(
                    AnalyticsEvent.NotificationChannelToggled(PaywallReason.FUEL_ALERTS, intent.enabled),
                )
            }
            // [PRIV-A-TRACE-IS-NOT-A-STAT-001] Same soft gate as the zone alerts: the trace only
            // leaves the phone under a Plus consent, and a free user's attempt IS the paywall
            // moment. Turning it OFF is always allowed — withdrawing consent needs no entitlement.
            is SettingsIntent.ToggleRouteCloudSync -> {
                if (intent.enabled && !state.value.isPlus) {
                    sendEffect(SettingsEffect.NavigateToPaywall(PaywallReason.ROUTE_CLOUD))
                } else {
                    prefs.setSyncRouteGeometry(intent.enabled)
                    updateState { copy(syncRouteGeometry = intent.enabled) }
                }
            }
            // [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001] Free, ungated, symmetric: withdrawing is
            // exactly as easy as giving, and neither has anything to do with Plus.
            is SettingsIntent.ToggleAdMeasurement -> {
                setAdMeasurementConsent(intent.enabled)
                    .onFailure { e -> PaparcarLogger.w(TAG, "ad-measurement switch not fully applied", e) }
                updateState { copy(adMeasurementAllowed = intent.enabled) }
            }
            // The Plus row is an OFFER, not a gate — GENERAL keeps the funnel honest.
            // [FREE-LIMITS-001] Already Plus with nothing left to sell (bought, or a lifetime
            // gift) → the read-only recap instead; a Plus-promo with an end date still has a real
            // decision to make, so it keeps going to the paywall. [MONEY-PLUS-CAN-SEE-ITS-OWN-BENEFITS-001]
            is SettingsIntent.OpenPlusPaywall -> {
                val s = state.value
                if (s.isPlus && s.plusPromoEndsAtMillis == null) {
                    sendEffect(SettingsEffect.NavigateToPlusStatus)
                } else {
                    sendEffect(SettingsEffect.NavigateToPaywall(PaywallReason.GENERAL))
                }
            }
            // Hands off to the system pin sheet — the launcher owns the rest. [WIDGET-ACTIONS-002]
            is SettingsIntent.PinWidget -> widgetPinRequester?.requestPin()
            is SettingsIntent.NavigateToVehicles ->
                sendEffect(SettingsEffect.NavigateToVehicles)
            // Health "Fix" — the row only offers it while something IS missing, so the fallback is
            // just belt-and-braces: PRODUCER is where the optional detection setup lives.
            is SettingsIntent.FixDetectionPermissions ->
                sendEffect(SettingsEffect.NavigateToPermissions(detectionGapFocus() ?: PermissionsFocus.Producer))
            // BT setup needs a vehicle to attach to; send them to add one first if none exists.
            is SettingsIntent.ConfigureBluetooth -> {
                val vehicleId = state.value.activeVehicleId
                if (vehicleId != null) {
                    sendEffect(SettingsEffect.NavigateToBluetoothConfig(vehicleId))
                } else {
                    sendEffect(SettingsEffect.NavigateToVehicles)
                }
            }
            // Battery exemption lives in the PRODUCER reliability section of the permissions flow.
            is SettingsIntent.ConfigureBattery ->
                sendEffect(SettingsEffect.NavigateToPermissions(PermissionsFocus.Producer))
            // REDUCED-reliability "Fix": the remedies (battery exemption + OEM autostart cards)
            // live in the same optional section of the permissions flow. [DET-RELIABILITY-001]
            is SettingsIntent.FixDetectionReliability ->
                sendEffect(SettingsEffect.NavigateToPermissions(PermissionsFocus.Producer))
            // [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] ALL THREE halves of the tutorial's own
            // state, and only those. The banked steps go because otherwise the checklist would come
            // back already finished, and the DEFERRED ones go because a "not yet" the replay kept
            // would silently skip those steps forever — the contract in `AppPreferences` promised
            // this from day one and the code only cleared two of the three.
            // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
            //
            // `hasConfirmedFirstPark` deliberately stays, so the cold-start notification machine is
            // not woken by someone re-reading an explanation.
            //
            // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] `introducedComplements` stays too, and for
            // the same reason the OTHER live signals do: a replay does not un-park the car, and it
            // does not un-tell somebody where the car-park data comes from. The fifth step comes
            // back ALREADY TICKED for a user who has been into a lane, exactly as step 1 comes back
            // ticked for a user with a car marked — and its row still opens its explainer, which is
            // what a replay is for. Clearing it would also make both complement lanes re-introduce
            // themselves, which is a lecture nobody asked for by pressing "replay the tutorial".
            is SettingsIntent.RestartFirstSteps -> viewModelScope.launch {
                prefs.setFirstStepsDone(emptySet())
                prefs.setFirstStepsDeferred(emptySet())
                // Device AND account: without clearing the remote fact the next cold start would
                // helpfully restore the dismissal the user just asked us to undo.
                setFirstStepsDismissed(false)
                sendEffect(SettingsEffect.FirstStepsRestarted)
            }
            is SettingsIntent.OpenPrivacyPolicy ->
                sendEffect(SettingsEffect.OpenUrl(PRIVACY_POLICY_URL))
            is SettingsIntent.OpenLicenses ->
                sendEffect(SettingsEffect.NavigateToLicenses)
            is SettingsIntent.OpenContact ->
                sendEffect(SettingsEffect.OpenUrl(CONTACT_MAILTO))
            is SettingsIntent.SendEmailVerification -> sendEmailVerification()
            is SettingsIntent.RequestSendDiagnostics ->
                updateState { copy(showSendDiagnosticsConfirmation = true) }
            is SettingsIntent.UpdateDiagnosticsMessage ->
                updateState { copy(diagnosticsMessage = intent.message) }
            // Closing the dialog does NOT discard the draft: BasicAlertDialog dismisses on an
            // outside tap, and that must not cost the user what they wrote.
            // [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001]
            is SettingsIntent.DismissSendDiagnostics ->
                updateState { copy(showSendDiagnosticsConfirmation = false) }
            is SettingsIntent.ConfirmSendDiagnostics -> sendDiagnostics()
            is SettingsIntent.RequestDeleteAccount ->
                updateState { copy(showDeleteAccountConfirmation = true) }
            is SettingsIntent.DismissDeleteAccount ->
                updateState { copy(showDeleteAccountConfirmation = false) }
            is SettingsIntent.ConfirmDeleteAccount -> deleteAccount()
            is SettingsIntent.Logout -> viewModelScope.launch {
                CrashReporter.setUserId(null)
                // ⛔ Por el caso de uso y no por `authRepository.signOut()` a secas: salir tiene que
                // retirar antes los tokens de push, y eso exige la sesión todavía abierta.
                // [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001]
                signOut()
            }
        }
    }

    /**
     * Where the permissions flow has to open for whatever is missing RIGHT NOW, or null when
     * detection is fully permitted. Read from the manager rather than from state so it is never a
     * frame behind a grant made in system settings.
     *
     * CORE when a hard blocker (foreground location) is missing OR the GPS master switch is off
     * (the "Enable GPS" row lives in the essential/CORE section); otherwise the PRODUCER section
     * holds the remaining detection permissions. [SETTINGS-REMODEL-001]
     *
     * One decision, two callers — the health row's "Fix" and the master toggle turning ON while
     * detection cannot run, which must land in the same place.
     * [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001]
     */
    private fun detectionGapFocus(): PermissionsFocus? =
        detectionPermissionGap(permissionManager.permissionState.value)

    /** The consent dialog stays up (with progress) until the upload resolves, so the user never
     *  wonders whether the tap did anything. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001] */
    private fun sendDiagnostics() {
        updateState { copy(isSendingDiagnostics = true) }
        viewModelScope.launch {
            sendDiagnosticsReport(state.value.diagnosticsMessage)
                .onSuccess {
                    // Only a delivered report clears the draft — anything else and the retry would
                    // start from a blank field.
                    updateState {
                        copy(
                            isSendingDiagnostics = false,
                            showSendDiagnosticsConfirmation = false,
                            diagnosticsMessage = "",
                        )
                    }
                    sendEffect(SettingsEffect.DiagnosticsSent)
                }
                .onFailure { e ->
                    updateState { copy(isSendingDiagnostics = false, showSendDiagnosticsConfirmation = false) }
                    PaparcarLogger.e(TAG, "Failed to send diagnostics report", e)
                    sendEffect(SettingsEffect.ShowError(PaparcarError.Network.Unknown("diagnostics upload failed")))
                }
        }
    }

    /** The confirmation stays up (with progress) until the deletion resolves — same contract as the
     *  diagnostics upload above and as the vehicle deletion. Closing it here, in the very update
     *  that raised the flag, is what left the dialog's own spinner unreachable and moved the only
     *  visible one to the danger-zone button behind it.
     *  [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001] */
    private fun deleteAccount() {
        updateState { copy(isDeletingAccount = true) }
        viewModelScope.launch {
            deleteAccountUseCase()
                .onSuccess {
                    CrashReporter.setUserId(null)
                    sendEffect(SettingsEffect.NavigateToAuth)
                }
                .onFailure { e ->
                    // Only the flag drops: the dialog stays open so the retry is one tap away.
                    updateState { copy(isDeletingAccount = false) }
                    PaparcarLogger.e(TAG, "Failed to delete account", e)
                    sendEffect(SettingsEffect.ShowError(PaparcarError.Auth.DeleteFailed))
                }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"

        /** Custom domain of the pap-26 Hosting site — the URL declared in Play Console.
         *  pap-26.web.app keeps serving the same content as a fallback. */
        const val PRIVACY_POLICY_URL = "https://paparcar.com/privacy-policy"

        const val CONTACT_MAILTO = "mailto:support@paparcar.com"
    }
}
