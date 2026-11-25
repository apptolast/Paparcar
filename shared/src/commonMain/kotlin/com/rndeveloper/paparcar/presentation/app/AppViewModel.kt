package com.rndeveloper.paparcar.presentation.app

import com.rndeveloper.paparcar.domain.analytics.AdMeasurementLane
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.analytics.shouldAskAdMeasurementConsent
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityBannerPhase
import com.rndeveloper.paparcar.domain.usecase.analytics.SetAdMeasurementConsentUseCase
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityObserver
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityStatus
import com.rndeveloper.paparcar.domain.entitlement.PlusMoment
import com.rndeveloper.paparcar.domain.entitlement.plusMomentFor
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.permissions.missingProducerPermissions
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.usecase.detection.PublishDetectionStateUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.ClaimFoundersPromoUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObservePlusGrantUseCase
import com.rndeveloper.paparcar.domain.notification.AlertChannel
import com.rndeveloper.paparcar.domain.notification.MapVisibility
import com.rndeveloper.paparcar.domain.notification.NearbySpotAlerts
import com.rndeveloper.paparcar.domain.notification.alertChannelFor
import com.rndeveloper.paparcar.domain.usecase.zone.ObserveZoneSpotAlertsUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import com.apptolast.baselogin.domain.AuthRepository
import com.apptolast.baselogin.domain.model.AuthState
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val permissionManager: PermissionManager,
    private val appPreferences: AppPreferences,
    private val connectivityObserver: ConnectivityObserver,
    private val vehicleRepository: VehicleRepository,
    private val zoneRepository: ZoneRepository,
    private val userParkingRepository: UserParkingRepository,
    // [MONEY-001] Plus zone alerts, client-side for 1.x: collected HERE because this is the one
    // ViewModel alive for the whole UI session, whatever tab is open.
    private val observeZoneSpotAlerts: ObserveZoneSpotAlertsUseCase,
    /** Decide por qué canal sale el aviso. [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001] */
    private val mapVisibility: MapVisibility,
    /**
     * El carril gratis: una plaza que acaba de quedar libre donde ESTÁ el usuario, decidida en Home
     * —el único sitio con el fix y las plazas ya en memoria— y entregada aquí, por la misma ranura
     * que el aviso de zona. [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
     */
    private val nearbySpotAlerts: NearbySpotAlerts,
    private val appNotificationManager: AppNotificationManager,
    // [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001] Collected HERE for the same reason the zone
    // alerts are: the one ViewModel alive for the whole UI session, whatever tab is open. A
    // per-screen collector would report a permission revocation only if the user happened to open
    // that screen afterwards.
    private val publishDetectionState: PublishDetectionStateUseCase,
    // [MKT-INSTALL-ATTRIBUTION-CHANNEL-001] Mismo sitio que la línea de arriba y por la misma
    // razón: se resuelve una vez por instalación, en el único ViewModel vivo toda la sesión.
    private val resolveInstallAttribution: com.rndeveloper.paparcar.domain.usecase.attribution.ResolveInstallAttributionUseCase,
    // [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001] The one-time ad-measurement question lives at
    // the root because it is asked once per install, whatever screen the user lands on.
    private val adMeasurementLane: AdMeasurementLane,
    private val setAdMeasurementConsent: SetAdMeasurementConsentUseCase,
    // [MONEY-FOUNDERS-WELCOME-001] El Plus de regalo se celebra y se despide desde la raíz, por
    // las mismas tres razones que la pregunta de anuncios: el grant llega ASÍNCRONO tras el
    // registro (segundos, o el arranque siguiente), este es el único ViewModel vivo toda la sesión
    // de UI en cualquier pestaña, y la condición de «estar en una pestaña» es justo la que no
    // interrumpe onboarding, permisos ni alta de vehículo.
    private val observePlusGrant: ObservePlusGrantUseCase,
    /** Entrar en la promo «Fundadores» al pisar Home. [MONEY-FOUNDERS-PLUS-PROMO-001]
     *  Vive aquí y no en `HomeViewModel` porque el carril del regalo entero —pedirlo, celebrarlo,
     *  despedirlo— es de este ViewModel, y Home sólo aporta el instante. */
    private val claimFoundersPromo: ClaimFoundersPromoUseCase,
    /** De quién es la cuenta: «una sola vez» se promete POR CUENTA, y una promesa así no se puede
     *  cumplir sobre un usuario que no se sabe nombrar. Reactivo y no una lectura única — este
     *  ViewModel sobrevive a un cierre de sesión y a la entrada del siguiente. */
    private val authRepository: AuthRepository,
    private val analyticsTracker: AnalyticsTracker? = null,
// `Nothing` as the effect type: the root ViewModel has no one-shot side effects to emit, and saying
// so in the type stops one from being smuggled in. Its only effect used to be applying a language
// the OS owns. [SETTINGS-LANGUAGE-LIVES-IN-THE-SYSTEM-001]
) : BaseViewModel<AppState, AppIntent, Nothing>() {

    // [BATT-002] Written only from AppIntent.SetAppForeground, which the app's ROOT composable
    // sends via ForegroundEffect — true whenever ANY app UI is visible, false the moment it isn't
    // (background, screen off, task-switched away). Starts false: nothing is on screen before the
    // first composition reports in.
    private val appForeground = MutableStateFlow(false)
    private var currentUid: String? = null
    private var foundersHomeUid: String? = null
    private var foundersClaimJob: Job? = null
    private val foundersClaimRevision = MutableStateFlow(0)

    init {
        // Synchronously correct state before first composition.
        // initState() cannot access constructor params — it is called during the superclass
        // constructor (via the _state lazy delegate) before subclass params are assigned.
        permissionManager.permissionState.value.let { current ->
            updateState {
                copy(
                    // [DET-READY-001d] CORE tracks the map's minimum; PRODUCER feeds
                    // isDetectionReady (the GPS-accuracy disclaimer's trigger). Neither blocks
                    // navigation — absence is surfaced in the Home detection banner.
                    permissionsGranted = current.hasCorePermissions,
                    detectionPermissionsGranted = current.missingProducerPermissions().isEmpty(),
                    locationServicesEnabled = current.isLocationServicesEnabled,
                    themeMode = appPreferences.themeMode,
                    imperialUnits = appPreferences.useImperialUnits,
                    connectivity = connectivityObserver.status.value,
                    // Cold-start offline shows the banner immediately; online starts hidden. [CONN-BANNER-001]
                    connectivityBanner = if (connectivityObserver.status.value == ConnectivityStatus.Offline) {
                        ConnectivityBannerPhase.Offline
                    } else {
                        ConnectivityBannerPhase.Hidden
                    },
                    hasSeenGpsAccuracyDisclaimer = appPreferences.hasSeenGpsAccuracyDisclaimer,
                    askAdMeasurementConsent = shouldAskAdMeasurementConsent(
                        adMeasurementLane,
                        appPreferences.adMeasurementConsent,
                    ),
                )
            }
        }
        viewModelScope.launch {
            // StateFlow never throws — no .catch needed here.
            permissionManager.permissionState.collect { permState ->
                updateState {
                    copy(
                        permissionsGranted = permState.hasCorePermissions, // [DET-READY-001d]
                        detectionPermissionsGranted = permState.missingProducerPermissions().isEmpty(),
                        locationServicesEnabled = permState.isLocationServicesEnabled,
                    )
                }
            }
        }
        viewModelScope.launch {
            // Track the previous status locally so the transient "Restored" banner only fires on a
            // real Offline → Online transition, not on cold start. [CONN-BANNER-001]
            var previous = connectivityObserver.status.value
            connectivityObserver.status
                .collect { current ->
                    when {
                        current == ConnectivityStatus.Offline -> {
                            // Cancel any pending auto-hide: we're offline again, keep the red banner up.
                            restoredHideJob?.cancel()
                            updateState { copy(connectivity = current, connectivityBanner = ConnectivityBannerPhase.Offline) }
                        }
                        previous == ConnectivityStatus.Offline -> {
                            // Real reconnect → push any edits made while offline to the cloud, then
                            // show the green banner briefly, then settle to Hidden. [SYNC-RECONCILE-001]
                            drainPendingSync()
                            claimFoundersPromoIfReady()
                            updateState { copy(connectivity = current, connectivityBanner = ConnectivityBannerPhase.Restored) }
                            restoredHideJob?.cancel()
                            restoredHideJob = viewModelScope.launch {
                                delay(RESTORED_VISIBLE_MS.milliseconds)
                                updateState { copy(connectivityBanner = ConnectivityBannerPhase.Hidden) }
                            }
                        }
                        else -> {
                            // Steady online (incl. cold start) → leave the banner as-is (Hidden).
                            updateState { copy(connectivity = current) }
                        }
                    }
                    previous = current
                }
        }
        // Mirror the real vehicle list into appState so navigation decisions
        // (e.g. post-permissions: HOME vs VEHICLE_REGISTRATION) can read it directly.
        //
        // observeVehicles() is auth-reactive at the repo level — it switches
        // automatically as the user logs in/out, emitting [] while unauthenticated.
        // No flatMapLatest needed here.
        vehicleRepository.observeVehicles()
            .catch { e -> PaparcarLogger.e(TAG, "vehicle observation failed", e) }
            .onEach { vehicles ->
                updateState { copy(hasVehicle = vehicles.isNotEmpty()) }
            }
            .launchIn(viewModelScope)

        // On a fresh (online) start, drain any edits left un-synced by a previous offline session so
        // they reliably reach the cloud (and other devices). [SYNC-RECONCILE-001]
        if (connectivityObserver.status.value == ConnectivityStatus.Online) drainPendingSync()

        // [MONEY-001] Plus zone alerts → local notification. The use case owns every rule (Plus
        // gate, per-zone opt-in, master switch, dedupe, baseline); this collector only delivers.
        // [BATT-002] Gated on appForeground: each subscribed zone opens 3×3 geohash Firestore
        // listeners (ObserveZoneSpotAlertsUseCase → observeNearbySpots), and this ViewModel is
        // deliberately the one alive for the whole UI session — which a sentry-resident FGS can
        // stretch to hours with nobody looking. The server's push fanout
        // ([ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]) already covers exactly
        // this gap; dropping the client listener while backgrounded loses nothing, it only moves
        // delivery to the lane built for it.
        appForeground
            .flatMapLatest { visible -> if (visible) observeZoneSpotAlerts() else emptyFlow() }
            .onEach { alert ->
                // ⛔ UN colector y UNA decisión. El stream apunta cada plaza en su registro durable
                // ANTES de emitirla, así que dos consumidores harían que el segundo no viera nada:
                // el primero ya la marcó como contada. El canal se elige aquí.
                // [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
                val channel = alertChannelFor(mapVisibility.onScreen.value)
                analyticsTracker?.track(
                    AnalyticsEvent.ZoneSpotAlertShown(location = alert.spot.location, channel = channel),
                )
                when (channel) {
                    AlertChannel.IN_APP -> showSpotAlertInApp(
                        SpotAlertCue(
                            spotId = alert.spot.id,
                            origin = SpotAlertOrigin.InZone(alert.zoneName),
                            latitude = alert.spot.location.latitude,
                            longitude = alert.spot.location.longitude,
                            unconfirmed = alert.spot.status.isUnconfirmedOffer,
                        ),
                    )
                    // ⛔ `isProvisional` tenía default `false` y este call site lo omitía — inofensivo
                    // mientras el stream vetaba las deducidas, y una promesa falsa en el instante en que
                    // deja de vetarlas. [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001]
                    AlertChannel.SYSTEM_NOTIFICATION -> appNotificationManager.showZoneSpotAlert(
                        zoneName = alert.zoneName,
                        latitude = alert.spot.location.latitude,
                        longitude = alert.spot.location.longitude,
                        isProvisional = alert.spot.status.isUnconfirmedOffer,
                    )
                }
            }
            .catch { e -> PaparcarLogger.w(TAG, "zone-alert delivery stream failed", e) }
            .launchIn(viewModelScope)

        // El carril gratis: una plaza recién libre DONDE ESTÁ el usuario.
        // [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
        //
        // ⛔ **Sin `alertChannelFor`, y no por olvido: este carril es in-app por construcción.** Sólo
        // decide cuando el mapa está delante, porque es de ahí de donde salen el fix y las plazas, así
        // que preguntar «¿está el mapa delante?» sería preguntar algo que ya se sabe. Y una
        // notificación del sistema aquí sería peor que redundante: prometería vigilancia con la app
        // cerrada, que es justo lo que este carril NO hace y lo que el de zona cobra por hacer.
        nearbySpotAlerts.alerts
            .onEach { spot ->
                showSpotAlertInApp(
                    SpotAlertCue(
                        spotId = spot.id,
                        origin = SpotAlertOrigin.NearYou,
                        latitude = spot.location.latitude,
                        longitude = spot.location.longitude,
                        unconfirmed = spot.status.isUnconfirmedOffer,
                    ),
                )
            }
            .catch { e -> PaparcarLogger.w(TAG, "nearby-alert delivery stream failed", e) }
            .launchIn(viewModelScope)

        // [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001] Report whether detection can actually run.
        // The use case owns every rule (what counts as a change, the Firestore dedupe, the signed-out
        // no-op); this collector only delivers, exactly like the zone-alert one above.
        publishDetectionState.observe()
            .onEach { (snapshot, reliability) -> publishDetectionState.publish(snapshot, reliability) }
            .catch { e -> PaparcarLogger.w(TAG, "detection-state stream failed", e) }
            .launchIn(viewModelScope)

        // [MKT-INSTALL-ATTRIBUTION-CHANNEL-001] Fire-and-forget: nunca bloquea la ruta de entrada,
        // y el propio use case decide en un instante si hay algo que hacer (gate en AppPreferences).
        viewModelScope.launch {
            runCatching { resolveInstallAttribution.resolve() }
                .onFailure { e -> PaparcarLogger.w(TAG, "install attribution resolve failed", e) }
        }

        // [MONEY-FOUNDERS-WELCOME-001] WHAT the app owes this account about its gifted Plus.
        //
        // The grant and uid can arrive in either order. A founders receipt triggers a fresh
        // RevenueCat fetch after Home, and the welcome reacts to the resulting entitlement.
        //
        // The two memories are READ here and not collected: they only ever change because of the
        // intent below, which sets the state itself. A `combine` over them as flows would be a
        // second path to the same transition.
        val signedInUid = signedInUid().onEach { uid ->
            if (foundersHomeUid == currentUid) foundersHomeUid = null
            currentUid = uid
            foundersClaimJob?.cancel()
            foundersClaimJob = null
            claimFoundersPromoIfReady()

            // [MKT-A-REFERRER-READ-BEFORE-LOGIN-MUST-SURVIVE-001] `resolve()` above almost always
            // ran before this session existed, so a classified install attribution may still be
            // waiting on AppPreferences — flush it the moment a uid shows up, instead of waiting
            // for the next cold start.
            if (uid != null) {
                viewModelScope.launch {
                    runCatching { resolveInstallAttribution.flushPending() }
                        .onFailure { e -> PaparcarLogger.w(TAG, "pending install attribution flush failed", e) }
                }
            }
        }
        // The receipt can arrive after an already-confirmed, unchanged SDK grant.
        // Re-evaluate the same eligibility rule when reconciliation finishes as well.
        combine(observePlusGrant(), signedInUid, foundersClaimRevision) { grant, uid, _ ->
            plusMomentFor(
                grant = grant,
                uid = uid,
                welcomedUids = appPreferences.plusPromoWelcomedUids,
                endingNoticedUids = appPreferences.plusPromoEndingNoticedUids,
                foundersUids = appPreferences.foundersPromoGrantedUids,
                nowMs = Clock.System.now().toEpochMilliseconds(),
            )
        }
            .distinctUntilChanged()
            .onEach { moment -> updateState { copy(plusMoment = moment) } }
            .catch { e -> PaparcarLogger.w(TAG, "plus-moment stream failed", e) }
            .launchIn(viewModelScope)
    }

    /** El uid de la sesión, o null mientras no haya ninguna. */
    private fun signedInUid() = authRepository.observeAuthState()
        .map { (it as? AuthState.Authenticated)?.session?.userId }
        .distinctUntilChanged()

    private fun claimFoundersPromoIfReady() {
        val uid = foundersHomeUid ?: return
        if (uid != currentUid || connectivityObserver.status.value != ConnectivityStatus.Online) return
        if (foundersClaimJob?.isActive == true) return
        foundersClaimJob = viewModelScope.launch {
            claimFoundersPromo(uid)
            if (uid == currentUid) foundersClaimRevision.value += 1
        }
    }

    /** El temporizador del aviso in-app de plaza. Uno solo: una plaza nueva REEMPLAZA a la
     *  anterior en vez de encolarse — nadie quiere leer una cola de avisos sobre un mapa que ya
     *  los está enseñando. [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001] */
    private var spotAlertHideJob: Job? = null

    /**
     * La ÚNICA forma de poner el aviso in-app en pantalla, para los dos carriles.
     * [NOTIF-A-FLOW-SPEAKS-THROUGH-ONE-SLOT-001]
     *
     * ⛔ Existe porque desde [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001] hay **dos**
     * emisores y **una** ranura en el estado. Con el cuerpo copiado en cada colector, el temporizador
     * sería lo primero en divergir: uno cancelaría el anterior y el otro no, y el aviso se quedaría
     * clavado en pantalla sin que ninguno de los dos pareciera culpable.
     *
     * Se retira solo: un aviso que habla de algo que ya está en el mapa no tiene por qué esperar a
     * que lo cierren.
     */
    private fun showSpotAlertInApp(cue: SpotAlertCue) {
        spotAlertHideJob?.cancel()
        spotAlertHideJob = viewModelScope.launch {
            delay(SPOT_ALERT_VISIBLE_MS)
            updateState { copy(spotAlert = null) }
        }
        updateState { copy(spotAlert = cue) }
    }

    /** Auto-hide timer for the transient [ConnectivityBannerPhase.Restored] banner. */
    private var restoredHideJob: Job? = null

    /** Fire-and-forget push of any un-synced vehicle + zone + parking edits to Firestore (the outbox
     *  drainer). Parking drains an offline clear/move so the remote mirror converges instead of
     *  keeping a stale active session. [SYNC-RECONCILE-USERPARKING-001] */
    private fun drainPendingSync() {
        viewModelScope.launch { vehicleRepository.pushPendingVehicles() }
        viewModelScope.launch { zoneRepository.pushPendingZones() }
        viewModelScope.launch { userParkingRepository.pushPendingParkingSessions() }
    }

    private companion object {
        const val TAG = "AppViewModel"

        /** How long the "connection restored" banner stays before settling to Hidden. [CONN-BANNER-001] */
        const val RESTORED_VISIBLE_MS = 2_500L
    }

    override fun initState() = AppState()

    override fun handleIntent(intent: AppIntent) {
        when (intent) {
            AppIntent.MarkOnboardingCompleted -> appPreferences.setOnboardingCompleted()
            AppIntent.DismissSpotAlert -> {
                spotAlertHideJob?.cancel()
                updateState { copy(spotAlert = null) }
            }
            AppIntent.DismissGpsAccuracyDisclaimer -> {
                appPreferences.setGpsAccuracyDisclaimerSeen()
                updateState { copy(hasSeenGpsAccuracyDisclaimer = true) }
            }
            // Either answer closes the question: a "no" is an answer, not a postponement, and
            // asking again after it is exactly the nagging the rules forbid. Settings is where it
            // can be changed. Closed even if the write failed, so the dialog cannot trap the user.
            is AppIntent.AnswerAdMeasurementConsent -> {
                setAdMeasurementConsent(intent.granted)
                    .onFailure { e -> PaparcarLogger.w(TAG, "ad-measurement answer not stored", e) }
                updateState { copy(askAdMeasurementConsent = false) }
            }
            // Se cierra y se apunta, y se apunta SÓLO la memoria del momento que se enseñó: la
            // otra sigue abierta a propósito. Si la promo se extendiera, un aviso ya dado no debe
            // enterrar para siempre una bienvenida que volvería a ser verdad.
            // [MONEY-FOUNDERS-WELCOME-001]
            is AppIntent.DismissPlusMoment -> {
                currentUid?.let { uid ->
                    when (intent.moment) {
                        is PlusMoment.FoundersWelcome -> appPreferences.addPlusPromoWelcomedUid(uid)
                        is PlusMoment.PromoEnding -> appPreferences.addPlusPromoEndingNoticedUid(uid)
                    }
                }
                updateState { copy(plusMoment = null) }
            }
            is AppIntent.ReachedHome -> {
                foundersHomeUid = intent.uid
                claimFoundersPromoIfReady()
            }
            is AppIntent.SetThemeMode -> {
                appPreferences.setThemeMode(intent.mode)
                updateState { copy(themeMode = intent.mode) }
            }
            is AppIntent.SetDistanceUnit -> {
                appPreferences.setUseImperialUnits(intent.imperial)
                updateState { copy(imperialUnits = intent.imperial) }
            }
            is AppIntent.SetAppForeground -> appForeground.value = intent.visible
        }
    }
}

/** Lo que el aviso in-app se queda en pantalla. Cuatro segundos: lo que se tarda en leer una línea
 *  y mirar el mapa, sin llegar a estorbar lo que el propio aviso quiere que mires.
 *  [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001] */
private const val SPOT_ALERT_VISIBLE_MS = 4_000L
