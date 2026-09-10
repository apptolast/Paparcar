package com.rndeveloper.paparcar.dev

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityBannerPhase
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.DetectionReliabilityLevel
import com.rndeveloper.paparcar.domain.model.PlusOffer
import com.rndeveloper.paparcar.domain.model.PlusPlan
import com.rndeveloper.paparcar.domain.model.UserProfile
import com.rndeveloper.paparcar.domain.model.VehicleColor
import com.rndeveloper.paparcar.domain.model.VehicleParkingFootprint
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.VehicleType
import com.rndeveloper.paparcar.domain.preferences.ThemeMode
import com.rndeveloper.paparcar.presentation.bluetooth.BluetoothConfigContent
import com.rndeveloper.paparcar.presentation.bluetooth.BluetoothConfigState
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SearchResult
import com.rndeveloper.paparcar.presentation.home.HomeMode
import com.rndeveloper.paparcar.presentation.home.HomeSelection
import com.rndeveloper.paparcar.presentation.home.HomeState
import com.rndeveloper.paparcar.presentation.home.sections.map.components.HomeGpsAccuracyPill
import com.rndeveloper.paparcar.presentation.home.sections.header.components.HomeSearchBar
import com.rndeveloper.paparcar.presentation.home.model.DetectionStory
import com.rndeveloper.paparcar.presentation.home.model.ParkedWatchBadge
import com.rndeveloper.paparcar.presentation.home.toBrowseListSlice
import com.rndeveloper.paparcar.presentation.home.toPeekSlice
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomeDetectionSurface
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomePeekHandle
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.SpotFitRow
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.AddZoneChip
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.ZoneChip
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.homeSheetItems
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.presentation.map.FocusedParking
import com.rndeveloper.paparcar.presentation.map.HistoryDetailSheet
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.first_steps_spotlight_caption
import com.rndeveloper.paparcar.presentation.onboarding.FirstStepExplainerScreen
import com.rndeveloper.paparcar.presentation.onboarding.FirstStepsCard
import com.rndeveloper.paparcar.presentation.onboarding.OnboardingScreen
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.WatchReinforcement
import com.rndeveloper.paparcar.domain.onboarding.resolveFirstSteps
import com.rndeveloper.paparcar.ui.components.PapSpotlight
import com.rndeveloper.paparcar.presentation.permissions.PermissionsContent
import com.rndeveloper.paparcar.presentation.permissions.PermissionsFocus
import com.rndeveloper.paparcar.presentation.permissions.PermissionsState
import com.rndeveloper.paparcar.presentation.preview.FakeData
import com.rndeveloper.paparcar.domain.model.DetectionTier
import com.rndeveloper.paparcar.domain.permissions.RequiredPermission
import com.rndeveloper.paparcar.presentation.licenses.LicenseDetailContent
import com.rndeveloper.paparcar.presentation.licenses.LicensesContent
import com.rndeveloper.paparcar.presentation.licenses.LicensesState
import com.rndeveloper.paparcar.presentation.settings.SettingsContent
import com.rndeveloper.paparcar.presentation.settings.SettingsState
import com.rndeveloper.paparcar.presentation.vehicleregistration.VehicleRegistrationContent
import com.rndeveloper.paparcar.presentation.vehicleregistration.VehicleRegistrationState
import com.rndeveloper.paparcar.presentation.vehicles.HistoryContent
import com.rndeveloper.paparcar.presentation.vehicles.HistoryFilter
import com.rndeveloper.paparcar.presentation.vehicles.HistoryState
import com.rndeveloper.paparcar.presentation.stats.StatsContent
import com.rndeveloper.paparcar.presentation.stats.StatsState
import com.rndeveloper.paparcar.presentation.vehicles.HistoryTimeline
import com.rndeveloper.paparcar.presentation.vehicles.VehiclesContent
import com.rndeveloper.paparcar.presentation.vehicles.VehiclesState
import com.apptolast.baselogin.presentation.screens.components.DefaultAuthContainer
import com.apptolast.baselogin.presentation.slots.defaultslots.DefaultDivider
import com.rndeveloper.paparcar.di.paparcarLoginConfig
import com.rndeveloper.paparcar.di.paparcarSocialProviders
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.model.ZoneIcon
import com.rndeveloper.paparcar.presentation.paywall.PaywallContent
import com.rndeveloper.paparcar.presentation.paywall.PaywallState
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.presentation.util.zoneIconFor
import com.rndeveloper.paparcar.ui.auth.paparcarAuthSlots
import com.rndeveloper.paparcar.ui.components.ConnectivityBanner
import com.rndeveloper.paparcar.ui.components.FreeSpotClusterMarker
import com.rndeveloper.paparcar.ui.components.FuelStationMarker
import com.rndeveloper.paparcar.ui.components.FreeSpotMarker
import com.rndeveloper.paparcar.ui.components.LicensePlateMarker
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReport
import com.rndeveloper.paparcar.ui.components.VehicleBadgeMarker
import com.rndeveloper.paparcar.ui.components.VehicleTopdownIcon
import com.rndeveloper.paparcar.ui.components.VehicleIcon
import com.rndeveloper.paparcar.ui.components.VehicleMarkerDoubt
import com.rndeveloper.paparcar.ui.components.MyVehicleMarker
import com.rndeveloper.paparcar.ui.components.ParkingCenterPin
import com.rndeveloper.paparcar.ui.components.ReportCenterPin
import com.rndeveloper.paparcar.ui.components.ZoneCenterPin
import com.rndeveloper.paparcar.ui.components.ZoneMarker
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.domain.detection.DetectionPath
import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomeFuelSection
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.VehicleWithStats
import com.rndeveloper.paparcar.domain.model.Money
import kotlin.time.Clock

/**
 * How a variant should be presented in the viewer.
 * - [FullScreen]: the composable already fills the screen (Settings, History, the expanded sheet…).
 * - [Surface]: a partial Home surface (detection card, peek handle, SpotFit row).
 *   Shown bottom-anchored like Home's sheet by default ("Completa"), with a "Solo" toggle to inspect
 *   the bare composable centered.
 */
private enum class Placement { FullScreen, Surface }

/** One renderable screen state. [content] is the RAW composable — the viewer owns theme + host. */
private class Variant(
    val name: String,
    val placement: Placement = Placement.FullScreen,
    val content: @Composable () -> Unit,
)
private class ScreenGroup(val title: String, val variants: List<Variant>)

private val licensesLoaded = LicensesState(
    isLoading = false,
    libraries = FakeData.openSourceLibraries,
    licenses = FakeData.openSourceLicenses,
)

private val sampleProfile = UserProfile(
    userId = "u1",
    email = "user@paparcar.app",
    displayName = "Carlos López",
    photoUrl = null,
    createdAt = 0L,
    updatedAt = 0L,
)

// Full HomeContent is private + map-bound, so the gallery renders the partial Home surfaces
// (detection card / peek / SpotFit) on their own; the viewer hosts them
// bottom-anchored (Placement.Surface) so they read like Home's sheet.
/**
 * The guided checklist at an arbitrary position. Goes through the REAL resolver rather than
 * hand-building a progress object, so a gallery variant can never show a combination the product
 * cannot actually reach. [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
 */
@Composable
private fun firstStepsCard(
    done: Set<FirstStep>,
    hasSpotsOnOffer: Boolean = false,
    isAutoDetectionStopped: Boolean = false,
    reinforcement: WatchReinforcement = WatchReinforcement.NONE,
    deferred: Set<FirstStep> = emptySet(),
) {
    FirstStepsCard(
        progress = resolveFirstSteps(
            done = done,
            dismissed = false,
            hasActiveSession = false,
            isWatching = false,
            hasTouchedSpots = false,
            // [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001] Decides the third step's face,
            // so both faces are reachable from the gallery.
            hasSpotsOnOffer = hasSpotsOnOffer,
            isAutoDetectionStopped = isAutoDetectionStopped,
            // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] El paso de refuerzo y su salida.
            reinforcement = reinforcement,
            deferred = deferred,
        ),
        onStartStep = {},
        onDismiss = {},
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

/** The coach mark over the centre pin, on a stand-in map surface (the gallery has no tiles). */
@Composable
private fun firstStepsSpotlight() {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        PapSpotlight(
            visible = true,
            caption = stringResource(Res.string.first_steps_spotlight_caption),
            onDismiss = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun detectionSurface(story: DetectionStory) {
    HomeDetectionSurface(
        story = story,
        onAddVehicle = {},
        onOpenPermissions = {},
        onMarkSpot = {},
        onStartDrivingDetection = {},
        onActivateDetection = {},
        allowDrivingDetection = true,
    )
}

private val sampleGps = GpsPoint(40.4165, -3.7030, 12f, 0L, 0f)

// [MONEY-001] Los dos planes tal y como los devolvería la tienda. Mismos números que el
// FakeEntitlementRepository, para que la galería y el flavor mock cuenten lo mismo.
// ⛔ Precios de mentira: en producción los pone la tienda y el código nunca los escribe.
private val samplePlusOffers = listOf(
    PlusOffer(
        plan = PlusPlan.MONTHLY,
        price = "1,99 €",
        amountMicros = 1_990_000L,
        freeTrialDays = 7,
    ),
    PlusOffer(
        plan = PlusPlan.YEARLY,
        price = "13,99 €",
        amountMicros = 13_990_000L,
        pricePerMonth = "1,17 €",
        freeTrialDays = 7,
    ),
)

// [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001] Una queja como las que llegan de verdad: qué
// esperaba, qué pasó y cuándo. Es lo que convierte horas de traza en una búsqueda acotada.
private val sampleReportMessage =
    "Ayer sobre las 9:15 aparqué en Fuencarral y el pin salió en la calle anterior. " +
        "Tuve que moverlo a mano."

// El contador solo se pinta en los últimos caracteres; esta muestra se queda a 30 del techo.
private val sampleReportMessageNearLimit =
    sampleReportMessage.repeat(4).take(DiagnosticsReport.MAX_MESSAGE_CHARS - 30)

// [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Sitio e instante fijos de la pregunta en la
// galería: la hora es un valor estampado, no `now`, para que la captura de un estado sea la
// misma hoy que dentro de un mes.
private const val GALLERY_ASK_SHOWN_AT_MS = 1_756_000_860_000L
private val GALLERY_ASK_CANDIDATE = GpsPoint(40.4168, -3.7038, 14f, GALLERY_ASK_SHOWN_AT_MS, 0f)

private val sampleSearchResults = listOf(
    SearchResult("Gran Vía, Madrid", 40.4203, -3.7058),
    SearchResult("Gran Vía de les Corts Catalanes, Barcelona", 41.3809, 2.1677),
)

@Composable
private fun searchBar(
    results: List<SearchResult> = emptyList(),
    isSearching: Boolean = false,
    showNoResults: Boolean = false,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        HomeSearchBar(
            query = "Gran Vía",
            results = results,
            isActive = true,
            isSearching = isSearching,
            showNoResults = showNoResults,
            onQueryChange = {},
            onResultClick = {},
            onClear = {},
        )
    }
}

@Composable
private fun peek(state: HomeState, showsZoneHeader: Boolean = false) =
    HomePeekHandle(slice = state.toPeekSlice(), browseShowsZoneHeader = showsZoneHeader)

/** Renders the expanded Home sheet (spots list + own-parking card) via homeSheetItems. */
@Composable
private fun sheet(state: HomeState) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        homeSheetItems(
            slice = state.toBrowseListSlice(),
            onIntent = {},
            onAction = {},
        )
    }
}

// Un historial ya resuelto, con sus vistas derivadas calculadas por el MISMO sitio que las calcula
// en producción — así la galería no puede mostrar un set filtrado que contradiga a su filtro.
private fun resolvedHistory(
    sessions: List<UserParking>,
    filter: HistoryFilter = HistoryFilter.All,
    isPlus: Boolean = true,
    // Sin coche no hay consumo, y sin consumo la tarjeta de cifras se queda sin CO2 ni gasto: la
    // galeria pasa uno para ensenar el bloque como se ve de verdad. [STATS-UI-001]
    vehicle: Vehicle? = FakeData.vehiclesWithStats.firstOrNull()?.vehicle,
) = HistoryTimeline.resolve(sessions, filter, isPlus, vehicle)

// [MONEY-001] Sesiones envejecidas para las variantes free: el gate corta por
// location.timestamp, así que empujar la marca 40 días atrás la saca de la ventana gratuita
// (7 días desde FREE-LIMITS-001, no los 30 con los que nació MONEY-001).
private const val BEYOND_FREE_WINDOW_MS = 40L * 86_400_000L

private fun UserParking.agedBeyondFreeWindow(): UserParking =
    copy(location = location.copy(timestamp = location.timestamp - BEYOND_FREE_WINDOW_MS))

private fun agedHalfBeyondFreeWindow(): List<UserParking> =
    FakeData.endedSessions.mapIndexed { i, s -> if (i % 2 == 0) s.agedBeyondFreeWindow() else s }

private fun agedAllBeyondFreeWindow(): List<UserParking> =
    FakeData.endedSessions.map { it.agedBeyondFreeWindow() }

/**
 * El primer coche de los fakes, convertido en enchufable: es el unico tipo que consume las DOS
 * energias, asi que es el unico que enciende a la vez el tile de litros y el de kWh.
 * [STATS-A-COMPUTED-LITRE-WITH-NO-READER-001]
 */
private fun plugInHybridFleet(): List<VehicleWithStats> =
    FakeData.vehiclesWithStats.take(1).map { withStats ->
        withStats.copy(
            vehicle = withStats.vehicle.copy(
                fuelType = FuelType.PLUG_IN_HYBRID,
                consumptionPer100 = 4.2f,
                consumptionElectricPer100 = 17.5f,
            ),
        )
    }

/** Estado del panel de cifras, resuelto por el MISMO sitio que lo resuelve en produccion, para que
 *  la galeria no pueda ensenar una rejilla que la pantalla real no daria. [STATS-PANEL-001] */
private fun statsPanelState(
    filter: HistoryFilter = HistoryFilter.ThisMonth,
    isPlus: Boolean = true,
    sessions: List<UserParking> = FakeData.allSessions,
    vehiclesOverride: List<VehicleWithStats>? = null,
): StatsState {
    val vehicles = vehiclesOverride ?: FakeData.vehiclesWithStats
    // Las sesiones de FakeData nacen sin vehicleId, y sin coche el agregador no puede convertir
    // kilometros en litros ni en CO2: la tarjeta salia sin sus dos cifras principales SOLO en la
    // galeria. Se las atamos al primer coche, que es el que la galeria pinta. [STATS-PANEL-001]
    val ownerId = vehicles.firstOrNull()?.vehicle?.id
    val owned = sessions.map { it.copy(vehicleId = ownerId) }
    // ⛔ El coche se PASA, no se deja al parametro por defecto.
    // [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001] `resolvedHistory` cae por defecto en el
    // primer coche de FakeData, asi que la variante del enchufable cambiaba la CABECERA y dejaba
    // el calculo con el coche de gasolina: salian litros y no salia el tile de kWh. Una variante
    // que no ejercita su caso es peor que no tenerla — se ve verde y no vigila nada.
    val resolved = resolvedHistory(owned, filter, isPlus, vehicles.firstOrNull()?.vehicle)
    return StatsState(
        vehicles = vehicles,
        selectedVehicleIndex = 0,
        filter = filter,
        filteredSessions = resolved.filteredSessions,
        costOrigin = resolved.costOrigin,
        periodStats = resolved.periodStats,
        lifetimeParkings = resolved.lifetimeParkings,
        lifetimeSpotsReleased = resolved.lifetimeSpotsReleased,
        isPlus = isPlus,
    )
}

/** Historial ya resuelto para TODOS los coches falsos, que es lo que espera la pantalla Vehicles. */
private fun vehiclesHistoryCache(sessions: List<UserParking>) =
    FakeData.vehiclesWithStats.associate { vws ->
        vws.vehicle.id to HistoryState(timeline = resolvedHistory(sessions))
    }

@Composable
private fun history(
    state: HistoryState,
    isPlus: Boolean = true,
) {
    HistoryContent(
        state = state,
        contentPadding = PaddingValues(0.dp),
        onViewOnMap = { _, _, _ -> },
        onFilterSelected = {},
        isPlus = isPlus,
    )
}

// History detail sheet — map-free surface, bottom-anchored like on the real screen. Showcases the
// real detection label (auto/manual/home), the real vehicle pictogram + the timeline stepper
// (‹ older / › newer). [HISTORY-DETAIL-002]
@Composable
private fun parkingDetailSheet(
    session: UserParking,
    vehicle: Vehicle,
    hasOlder: Boolean = true,
    hasNewer: Boolean = true,
    canWithdraw: Boolean = false,
    withdrawing: Boolean = false,
) {
    Box(Modifier.fillMaxSize()) {
        HistoryDetailSheet(
            focused = FocusedParking.Resolved(session),
            vehicle = vehicle,
            hasOlder = hasOlder,
            hasNewer = hasNewer,
            onOlder = {},
            onNewer = {},
            onNavigate = { _, _ -> },
            onWithdraw = if (canWithdraw) ({}) else null,
            isWithdrawing = withdrawing,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
    }
}

// Las dos caras SIN datos de la misma ficha: "aun no lo se" (skeleton) y "ese aparcamiento ya no
// esta en tu historial". Antes las dos se pintaban como una tarjeta que decia "Ubicacion
// desconocida". [UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001]
@Composable
private fun parkingDetailSheetFace(focused: FocusedParking) {
    Box(Modifier.fillMaxSize()) {
        HistoryDetailSheet(
            focused = focused,
            vehicle = null,
            hasOlder = false,
            hasNewer = false,
            onOlder = {},
            onNewer = {},
            onNavigate = { _, _ -> },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
    }
}

// SpotFit outcomes derived from computeSpotFit(spot, vehicle): same body = OPTIMAL,
// car ≤ spot length = FITS, car > spot = DOES_NOT_FIT, spot without size = UNKNOWN.
private fun fitSpot(size: VehicleSize?, carbody: CarbodyType? = null) =
    FakeData.nearbySpots.first().copy(sizeCategory = size, carbodyType = carbody)
private fun fitVehicle(size: VehicleSize, carbody: CarbodyType? = null) =
    FakeData.vehicleSedan.copy(sizeCategory = size, carbodyType = carbody)

@Composable
private fun spotFit(spot: com.rndeveloper.paparcar.domain.model.Spot, vehicle: com.rndeveloper.paparcar.domain.model.Vehicle) =
    SpotFitRow(spot = spot, vehicle = vehicle)

// Login (BaseLogin slots) — mirrors PaparcarAuthSlotsPreviews: renders the REAL Paparcar slots
// inside the library's container, driven by a static state (no ViewModel, no OAuth).
// Stand-in for BuildConfig.GOOGLE_WEB_CLIENT_ID: the gallery only needs the id to be non-blank so
// the Google button appears exactly as in production; it never reaches Firebase.
private const val GALLERY_GOOGLE_WEB_CLIENT_ID = "gallery.apps.googleusercontent.com"

@Composable
private fun loginScreen(
    email: String = "",
    password: String = "",
    emailError: String? = null,
    passwordError: String? = null,
    isLoading: Boolean = false,
    consentAccepted: Boolean = false,
) {
    val slots = paparcarAuthSlots(
        hasAcceptedLegalConsent = consentAccepted,
        onLegalConsentAccepted = {},
    ).login
    val isFormValid = email.isNotBlank() && password.isNotBlank()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        DefaultAuthContainer(
            modifier = Modifier,
            verticalArrangement = slots.layoutVerticalArrangement,
        ) {
            slots.header()
            Spacer(Modifier.height(16.dp))
            slots.emailField(email, {}, emailError, !isLoading)
            Spacer(Modifier.height(8.dp))
            slots.passwordField(password, {}, passwordError, !isLoading)
            slots.forgotPasswordLink {}
            Spacer(Modifier.height(16.dp))
            slots.submitButton({}, isLoading, isFormValid && !isLoading, "Iniciar sesión")
            slots.socialProviders?.let { social ->
                DefaultDivider("O")
                // The REAL offer, not a hand-written list: same builder the app boots with, so the
                // gallery cannot show a button production does not have (it used to promise Apple
                // and hide the SMS one). [AUTH-PROVIDERS-EXPLICIT-001]
                social(paparcarSocialProviders(paparcarLoginConfig(GALLERY_GOOGLE_WEB_CLIENT_ID)), null) {}
                Spacer(Modifier.height(8.dp))
            }
            slots.registerLink {}
        }
    }
}

// Map markers off-tiles — mirrors PaparcarMapMarkersPreviews' showcase so marker rendering
// (freshness ramp, en-route blue, clusters, zones, centre pins) can be eyeballed on-device.
@Composable
private fun markersShowcase() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(40.dp)) // clears the "← Lista" control row
        MarkerSectionLabel("LicensePlate — sin matrícula · con matrícula · seleccionado")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LicensePlateMarker()
            LicensePlateMarker(plateText = "1234ABC")
            LicensePlateMarker(plateText = "1234ABC", selected = true)
        }
        // [UI-A-MOTORCYCLE-IS-DRAWN-LIKE-EVERY-OTHER-VEHICLE-001] La familia entera en una fila: aquí
        // se ve de un vistazo que la moto es del MISMO mundo que los coches (misma rueda, mismo
        // borde blanco, mismo verde) y no un glifo prestado. Se recorre `CarbodyType.entries`, así
        // que una carrocería nueva sin arte aparece sola en vez de pasar desapercibida.
        MarkerSectionLabel("Pictograma de vehículo · perfil — moto + todas las carrocerías")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            VehicleIcon(carbody = null, size = VehicleSize.MOTORCYCLE, modifier = Modifier.size(44.dp))
            CarbodyType.entries.forEach { body ->
                VehicleIcon(carbody = body, size = null, modifier = Modifier.size(44.dp))
            }
        }
        MarkerSectionLabel("Pictograma de vehículo · cenital (puck) — moto · hatchback · furgo")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            VehicleTopdownIcon(carbody = null, size = VehicleSize.MOTORCYCLE, modifier = Modifier.size(44.dp))
            VehicleTopdownIcon(carbody = CarbodyType.HATCHBACK_MEDIUM, size = null, modifier = Modifier.size(44.dp))
            VehicleTopdownIcon(carbody = CarbodyType.VAN_LIGHT, size = null, modifier = Modifier.size(44.dp))
        }
        MarkerSectionLabel("MyVehicle — normal · seleccionado")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MyVehicleMarker()
            MyVehicleMarker(selected = true)
        }
        // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] El fantasma junto al confirmado: hay que
        // poder ver de un vistazo que son EL MISMO objeto en dos estados, no dos marcadores.
        MarkerSectionLabel("VehicleBadge — confirmado · SIN CONFIRMAR (pregunta abierta)")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            VehicleBadgeMarker(carbodyType = CarbodyType.SUV_MEDIUM)
            VehicleBadgeMarker(carbodyType = CarbodyType.SUV_MEDIUM, doubt = VehicleMarkerDoubt.ASKING)
            VehicleBadgeMarker(
                carbodyType = CarbodyType.SUV_MEDIUM,
                doubt = VehicleMarkerDoubt.ASKING,
                isBluetoothPaired = true,
            )
        }
        // [UI-A-SAVED-ZONE-WEARS-ITS-DOUBT-TOO-001] La tercera duda, junto a las otras dos: hay que
        // poder ver de un vistazo que la ZONA GUARDADA no es «una pregunta abierta» (borde sólido,
        // etiqueta opaca, diana en vez de `?`) ni un pin exacto. En el mapa lleva además su anillo
        // alrededor; aquí se mira el marcador solo, que es lo que sobrevive al zoom de calle.
        MarkerSectionLabel("VehicleBadge · ZONA GUARDADA — vigilado · Bluetooth · sin vigilancia · seleccionado")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            VehicleBadgeMarker(carbodyType = CarbodyType.SEDAN, doubt = VehicleMarkerDoubt.APPROXIMATE)
            VehicleBadgeMarker(
                carbodyType = CarbodyType.SEDAN,
                isBluetoothPaired = true,
                doubt = VehicleMarkerDoubt.APPROXIMATE,
            )
            VehicleBadgeMarker(
                carbodyType = CarbodyType.SEDAN,
                isActive = false,
                doubt = VehicleMarkerDoubt.APPROXIMATE,
            )
            VehicleBadgeMarker(
                carbodyType = CarbodyType.SEDAN,
                selected = true,
                doubt = VehicleMarkerDoubt.APPROXIMATE,
            )
        }
        MarkerSectionLabel("FreeSpot — HIGH · MEDIUM · LOW · MANUAL · seleccionado")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            FreeSpotMarker(reliability = SpotFreshness.FRESH)
            FreeSpotMarker(reliability = SpotFreshness.RECENT)
            FreeSpotMarker(reliability = SpotFreshness.STALE)
            FreeSpotMarker(isManual = true)
            FreeSpotMarker(reliability = SpotFreshness.FRESH, selected = true)
        }
        // [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] Area spot: the ground dot becomes the target ⊙.
        MarkerSectionLabel("FreeSpot · zona — FRESH · RECENT · seleccionado")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            FreeSpotMarker(reliability = SpotFreshness.FRESH, approximate = true)
            FreeSpotMarker(reliability = SpotFreshness.RECENT, approximate = true)
            FreeSpotMarker(reliability = SpotFreshness.FRESH, selected = true, approximate = true)
        }
        MarkerSectionLabel("FreeSpot · en route — 2 · 5 · 9+ · seleccionado")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            FreeSpotMarker(enRouteCount = 2)
            FreeSpotMarker(enRouteCount = 5)
            FreeSpotMarker(enRouteCount = 12)
            FreeSpotMarker(enRouteCount = 5, selected = true)
        }
        MarkerSectionLabel("Cluster — 3 · 12 · 99+")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FreeSpotClusterMarker(count = 3)
            FreeSpotClusterMarker(count = 12)
            FreeSpotClusterMarker(count = 250)
        }
        MarkerSectionLabel("Zona — pública · privada · nombre largo")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoneMarker(name = "Casa", icon = zoneIconFor(ZoneIcon.HOME), isPrivate = false)
            ZoneMarker(name = "Trabajo", icon = zoneIconFor(ZoneIcon.WORK), isPrivate = true)
            ZoneMarker(name = "Gimnasio del barrio", icon = zoneIconFor(ZoneIcon.GYM), isPrivate = false)
        }
        // El chip de la cabecera vive aquí junto a la etiqueta del mapa a propósito: son la MISMA
        // zona en dos superficies, y verlos separados es lo que dejó al chip en verde de marca
        // mientras el mapa ya la pintaba neutral. [UI-A-PRIVATE-ZONE-IS-PRIVATE-ON-EVERY-SURFACE-001]
        MarkerSectionLabel("Chip de zona — pública · privada · el + a la misma altura")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoneChip(label = "Casa", iconKey = ZoneIcon.HOME, isPrivate = false, onClick = {}, onEdit = {})
            ZoneChip(label = "Garaje", iconKey = ZoneIcon.WORK, isPrivate = true, onClick = {}, onEdit = {})
            AddZoneChip(expanded = false, onAddZone = {}, onDismiss = {})
        }
        // El colapso VIVO: es lo que el user reportó como "sin animación ni nada", y no se puede
        // juzgar en una captura estática. [UI-THE-ADD-ZONE-CHIP-IS-ONE-CHIP-IN-EVERY-STATE-001]
        MarkerSectionLabel("Invitación de zona — pulsa la × y mira el colapso")
        var inviteExpanded by remember { mutableStateOf(true) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddZoneChip(
                expanded = inviteExpanded,
                onAddZone = { inviteExpanded = !inviteExpanded },
                onDismiss = { inviteExpanded = false },
            )
        }
        // La cápsula de precisión ya NO vive en la cabecera: está abajo a la izquierda, en la banda
        // de los FABs, y solo habla cuando el fix es malo de verdad (> 50 m). En device puede pasar
        // semanas sin verse, que es justo por qué llevaba tanto tiempo sin que nadie la mirara.
        // Aquí está su único nivel y su entrada. [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001]
        MarkerSectionLabel("Precisión GPS — 30 m no dice nada · 120 m avisa")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeGpsAccuracyPill(accuracy = 30f)
            HomeGpsAccuracyPill(accuracy = 120f)
        }
        MarkerSectionLabel("Precisión GPS — pulsa para verla entrar y salir por la izquierda")
        var gpsDegraded by remember { mutableStateOf(true) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { gpsDegraded = !gpsDegraded }) { Text("Alternar") }
            HomeGpsAccuracyPill(accuracy = if (gpsDegraded) 120f else 8f)
        }
        MarkerSectionLabel("Pin central · Report (reposo · elevado)")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CenterPinSlot { ReportCenterPin(cameraMoving = false) }
            CenterPinSlot { ReportCenterPin(cameraMoving = true) }
        }
        MarkerSectionLabel("Pin central · Parking (reposo · elevado)")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CenterPinSlot { ParkingCenterPin(cameraMoving = false) }
            CenterPinSlot { ParkingCenterPin(cameraMoving = true) }
        }
        MarkerSectionLabel("Pin central · Zona (reposo · elevado · privada)")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CenterPinSlot { ZoneCenterPin(icon = Icons.Rounded.LocalParking, cameraMoving = false, isPrivate = false) }
            CenterPinSlot { ZoneCenterPin(icon = Icons.Rounded.LocalParking, cameraMoving = true, isPrivate = false) }
            CenterPinSlot { ZoneCenterPin(icon = Icons.Rounded.LocalParking, cameraMoving = false, isPrivate = true) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CenterPinSlot(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(width = 64.dp, height = 96.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun MarkerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// Recipes mirror the existing *Previews.kt so the gallery shows the same curated states on-device.
private val galleryGroups: List<ScreenGroup> = listOf(
    ScreenGroup(
        "Home · detección",
        listOf(
            Variant("Sin permiso CORE (BlockedCore)", Placement.Surface) { detectionSurface(DetectionStory.BlockedCore) },
            Variant("Detección inactiva — flag off o permisos (Inactive)", Placement.Surface) { detectionSurface(DetectionStory.Inactive) },
            Variant("Sin coche registrado (NoVehicle)", Placement.Surface) { detectionSurface(DetectionStory.NoVehicle) },
            Variant("Sin aparcar aún (AwaitingFirstPark)", Placement.Surface) { detectionSurface(DetectionStory.AwaitingFirstPark) },
            // [UX-DETECTION-STORY-001] Relatos felices — línea discreta, sin card.
            Variant("Vigilando (activo aparcado)", Placement.Surface) {
                detectionSurface(DetectionStory.Watching("Škoda Kamiq", isParked = true, watch = VehicleWatch.Assisted))
            },
            // [DET-WATCH-HONEST-001] Honesto: frágil (avisa) e interrumpido (el OS mató la FGS).
            Variant("Vigilando FRÁGIL (sin exención batería)", Placement.Surface) {
                detectionSurface(
                    DetectionStory.Watching(
                        "Škoda Kamiq", isParked = true, watch = VehicleWatch.Assisted,
                        watchBadge = ParkedWatchBadge.WATCHING_FRAGILE,
                    ),
                )
            },
            // [DET-WATCH-REACTIVATE-001] Su CTA reactiva el vigilante (no pide batería): en el build
            // mock el fake pasa la presencia a Sentry, así que la fila se cura sola al tocarla.
            Variant("Vigilancia en PAUSA (el sistema paró el vigilante)", Placement.Surface) {
                detectionSurface(
                    DetectionStory.Watching(
                        "Škoda Kamiq", isParked = true, watch = VehicleWatch.Assisted,
                        watchBadge = ParkedWatchBadge.WATCH_INTERRUPTED,
                    ),
                )
            },
            Variant("Vigilando (BT armado, sin sesión)", Placement.Surface) {
                detectionSurface(DetectionStory.Watching("Škoda Kamiq", isParked = false, watch = VehicleWatch.Bluetooth))
            },
            Variant("Conduciendo (detección activa → verde)", Placement.Surface) {
                detectionSurface(DetectionStory.Driving("Škoda Kamiq", isCandidate = false))
            },
            // [UI-COLOR-DOCTRINE-001] La fila viste el color del MÉTODO del coche: BT → azul.
            Variant("Conduciendo (BT → azul)", Placement.Surface) {
                detectionSurface(DetectionStory.Driving("Škoda Kamiq", isCandidate = false, watch = VehicleWatch.Bluetooth))
            },
            Variant("Aparcando… (candidate)", Placement.Surface) {
                detectionSurface(DetectionStory.Driving("Škoda Kamiq", isCandidate = true))
            },
        ),
    ),
    ScreenGroup(
        "Conectividad · banner",
        listOf(
            Variant("Sin conexión (Offline · persistente, rojo)") {
                Column(Modifier.fillMaxSize()) { ConnectivityBanner(ConnectivityBannerPhase.Offline) }
            },
            Variant("Conexión restablecida (Restored · verde, ~2,5s)") {
                Column(Modifier.fillMaxSize()) { ConnectivityBanner(ConnectivityBannerPhase.Restored) }
            },
        ),
    ),
    ScreenGroup(
        "Detalle de aparcamiento histórico",
        listOf(
            // Detection label reads the REAL origin (was hard-stuck on "automática"); icon is the real
            // vehicle body shape (was a generic car); stepper walks the whole history. [HISTORY-DETAIL-001]
            //
            // Las tres primeras son el par acento+fuente: el acento sale de la vigilancia del COCHE
            // (azul BT / verde asistido) y el texto de la vía que puso ESE pin, leída de
            // `detectionPath`. La legacy no afirma vía ninguna. [UI-HISTORY-IDENTITY-AND-SOURCE-001]
            Variant("Activa · Bluetooth (azul)") {
                parkingDetailSheet(
                    session = FakeData.activeSession.copy(
                        spotType = SpotType.AUTO_DETECTED,
                        detectionPath = "bt",
                    ),
                    vehicle = FakeData.vehicleCorolla,
                )
            },
            Variant("Activa · Asistido (verde)") {
                parkingDetailSheet(
                    session = FakeData.activeSession.copy(
                        spotType = SpotType.AUTO_DETECTED,
                        // [DET-DETECTION-PATH-IS-A-TYPE-001] Era "steps=3 kinematicFixes=7", jerga de
                        // diagnóstico que producción NUNCA escribe como detectionPath — así que esta
                        // variante decía «Asistido» y desde el tipo se leería como desconocida. El
                        // camino sale del tipo, no de una cadena inventada aquí.
                        detectionPath = DetectionPath.StepsEgress.label,
                    ),
                    vehicle = FakeData.vehicleSedan,
                )
            },
            // [DET-DOUBT-MUST-REACH-THE-SCREEN-001] La sesión que la app guardó como ZONA. Esta
            // pantalla la pintaba como un punto exacto, con «Navegar a esta ubicación» debajo.
            Variant("Aproximada · zona con su duda") {
                parkingDetailSheet(
                    session = FakeData.activeSession.copy(
                        spotType = SpotType.AUTO_DETECTED,
                        detectionPath = DetectionPath.UnattendedZone("gap_anchor").label,
                        detectionReliability = 0.5f,
                        zoneRadiusMeters = 250f,
                    ),
                    vehicle = FakeData.vehicleSedan,
                )
            },
            Variant("Legacy sin provenance (no afirma vía)") {
                parkingDetailSheet(
                    session = FakeData.activeSession.copy(
                        spotType = SpotType.AUTO_DETECTED,
                        detectionPath = null,
                    ),
                    vehicle = FakeData.vehicleSedan,
                )
            },
            Variant("Cerrada · asistida (apagada)") {
                parkingDetailSheet(
                    session = FakeData.endedSessions[1].copy(
                        spotType = SpotType.AUTO_DETECTED,
                        // [DET-GUARDRAILS-KEEP-THE-DOCTRINE-001] Lo cazó el guardarraíl nuevo: se me
                        // escapó en el barrido de DET-DOUBT-MUST-REACH-THE-SCREEN-001.
                        detectionPath = DetectionPath.VehicleExitWindow.label,
                    ),
                    vehicle = FakeData.vehicleSedan,
                )
            },
            Variant("Reporte manual · coche") {
                parkingDetailSheet(
                    session = FakeData.endedSessions[0].copy(spotType = SpotType.MANUAL_REPORT),
                    vehicle = FakeData.vehicleSedan,
                )
            },
            Variant("Geocerca de casa · furgoneta") {
                parkingDetailSheet(
                    session = FakeData.endedSessions[2].copy(spotType = SpotType.HOME_GEOFENCE),
                    vehicle = FakeData.vehicleVan,
                )
            },
            Variant("Moto · manual") {
                parkingDetailSheet(
                    session = FakeData.endedSessions[3].copy(spotType = SpotType.MANUAL_REPORT),
                    vehicle = FakeData.vehicleMoto,
                )
            },
            Variant("Sesión activa · más reciente (solo ‹ activo)") {
                parkingDetailSheet(
                    session = FakeData.activeSession.copy(
                        spotType = SpotType.AUTO_DETECTED,
                        detectionPath = "unattended_timeout",
                    ),
                    vehicle = FakeData.vehicleSedan,
                    hasNewer = false,
                )
            },
            Variant("Más antiguo del historial (solo › activo)") {
                parkingDetailSheet(
                    session = FakeData.endedSessions.last().copy(
                        spotType = SpotType.AUTO_DETECTED,
                        detectionPath = "bt_timeout",
                    ),
                    vehicle = FakeData.vehicleVan,
                    hasOlder = false,
                )
            },
            // Las dos caras que antes se pintaban igual (y mintiendo).
            // [UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001]
            // Sesión cerrada: se puede QUITAR del historial (retirada, no borrado). Una sesión viva
            // no ofrece el botón. [PARK-A-HISTORIC-PARKING-CAN-BE-WITHDRAWN-001]
            Variant("Cerrada · con quitar del historial") {
                parkingDetailSheet(
                    session = FakeData.endedSessions.first(),
                    vehicle = FakeData.vehicleSedan,
                    canWithdraw = true,
                )
            },
            // El confirm de quitar vive en un `remember` dentro de la ficha, así que esta variante
            // se mira PULSANDO «Quitar del historial»: el diálogo sale ya con su spinner. Es la
            // única forma de ver este estado sin una retirada real.
            // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
            Variant("Cerrada · quitando del historial") {
                parkingDetailSheet(
                    session = FakeData.endedSessions.first(),
                    vehicle = FakeData.vehicleSedan,
                    canWithdraw = true,
                    withdrawing = true,
                )
            },
            Variant("Sin resolver (skeleton)") {
                parkingDetailSheetFace(FocusedParking.Unresolved)
            },
            Variant("Ya no está en el historial") {
                parkingDetailSheetFace(FocusedParking.NotFound)
            },
        ),
    ),
    ScreenGroup(
        "Home · peek / sheet",
        listOf(
            // PapSheet subject rule: parked car → vehicle lead (no trailing free-spots pill; the
            // count reads in the expanded sheet). [UI-SHEET-001]
            Variant("PapSheet · browse coche aparcado", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan),
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                    ),
                )
            },
            // Expanded browse with a parked car: the header hands over to the ZONE — the car's
            // info lives in its TUS VEHÍCULOS card below. [UI-SHEET-004]
            Variant("PapSheet · browse expandido (zona, coche aparcado)", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan),
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                    ),
                    showsZoneHeader = true,
                )
            },
            // No parked car → counter lead (green with spots). [UI-SHEET-001]
            Variant("PapSheet · browse contador verde (sin coche)", Placement.Surface) {
                peek(HomeState(cameraAddressAndPlace = FakeData.addressAndPlaceFuel, nearbySpots = FakeData.nearbySpots))
            },
            // 0 spots → amber counter + swipe-up hint. [UI-SHEET-001]
            Variant("PapSheet · browse contador ámbar 0 + hint", Placement.Surface) {
                peek(HomeState(cameraAddressAndPlace = FakeData.addressAndPlaceStreet, nearbySpots = emptyList()))
            },
            // First of the browse order: nothing behind it, so the footer stepper offers only ›.
            // [UI-PEEK-STEPS-BETWEEN-PINS-001]
            Variant("PapSheet · spot seleccionado, el primero (solo ›)", Placement.Surface) {
                peek(
                    HomeState(
                        nearbySpots = FakeData.nearbySpots,
                        userGpsPoint = sampleGps,
                        selection = HomeSelection.Spot(FakeData.nearbySpots.first().id),
                    ),
                )
            },
            // Mid-list: both chevrons. The pair is anchored to the edges over reserved slots, so
            // this variant and the two end ones must line up pixel-for-pixel.
            Variant("PapSheet · spot en medio de la lista (‹ y ›)", Placement.Surface) {
                peek(
                    HomeState(
                        nearbySpots = FakeData.nearbySpots,
                        userGpsPoint = sampleGps,
                        selection = HomeSelection.Spot(FakeData.nearbySpots[1].id),
                    ),
                )
            },
            Variant("PapSheet · último spot de la lista (solo ‹)", Placement.Surface) {
                peek(
                    HomeState(
                        nearbySpots = FakeData.nearbySpots,
                        userGpsPoint = sampleGps,
                        selection = HomeSelection.Spot(FakeData.nearbySpots.last().id),
                    ),
                )
            },
            // One spot on offer → no neighbour on either side → no stepper row and no divider: the
            // peek ends at the two signal buttons, exactly as it did before the stepper existed.
            Variant("PapSheet · spot único (sin flechas)", Placement.Surface) {
                peek(
                    HomeState(
                        nearbySpots = listOf(FakeData.nearbySpots.first()),
                        userGpsPoint = sampleGps,
                        selection = HomeSelection.Spot(FakeData.nearbySpots.first().id),
                    ),
                )
            },
            // [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001] The vote pair is offered only to a
            // witness. Every spot in FakeData sits 119-441 m from `sampleGps`, so WITHOUT this
            // variant the buttons would be invisible everywhere in the catalog and the feature
            // would be untestable off-device — the user is placed on top of the spot instead.
            Variant("PapSheet · spot con el usuario ENCIMA (botones de voto)", Placement.Surface) {
                val spot = FakeData.nearbySpots.first()
                peek(
                    HomeState(
                        nearbySpots = FakeData.nearbySpots,
                        userGpsPoint = sampleGps.copy(
                            latitude = spot.location.latitude,
                            longitude = spot.location.longitude,
                        ),
                        selection = HomeSelection.Spot(spot.id),
                    ),
                )
            },
            // The same spot from across town: no buttons at all, rather than buttons that invite
            // the tap and then refuse it.
            Variant("PapSheet · spot LEJOS (sin botones de voto)", Placement.Surface) {
                peek(
                    HomeState(
                        nearbySpots = FakeData.nearbySpots,
                        userGpsPoint = sampleGps,
                        selection = HomeSelection.Spot(FakeData.nearbySpots.first().id),
                    ),
                )
            },
            // …and once this session has voted, the pair stops being offered even standing there.
            Variant("PapSheet · spot ya votado en esta sesión", Placement.Surface) {
                val spot = FakeData.nearbySpots.first()
                peek(
                    HomeState(
                        nearbySpots = FakeData.nearbySpots,
                        userGpsPoint = sampleGps.copy(
                            latitude = spot.location.latitude,
                            longitude = spot.location.longitude,
                        ),
                        selection = HomeSelection.Spot(spot.id),
                        votedSpotIds = setOf(spot.id),
                    ),
                )
            },
            // [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001] The whole ramp in three variants, without
            // waiting half an hour for a spot to cross a threshold. Every surface of the peek —
            // eyebrow, puck, meta accents, meter — has to agree with the age on the subtitle.
            Variant("PapSheet · spot RECIÉN LIBERADO (verde, 4 min)", Placement.Surface) {
                peek(
                    HomeState(
                        nearbySpots = FakeData.nearbySpots,
                        userGpsPoint = sampleGps,
                        selection = HomeSelection.Spot("sp_1"),
                    ),
                )
            },
            Variant("PapSheet · spot RECIENTE (ámbar, 15 min)", Placement.Surface) {
                peek(
                    HomeState(
                        nearbySpots = FakeData.nearbySpots,
                        userGpsPoint = sampleGps,
                        selection = HomeSelection.Spot("sp_2"),
                    ),
                )
            },
            Variant("PapSheet · spot VIEJO (rojo, 40 min — sigue en el mapa)", Placement.Surface) {
                peek(
                    HomeState(
                        nearbySpots = FakeData.nearbySpots,
                        userGpsPoint = sampleGps,
                        selection = HomeSelection.Spot("sp_3"),
                    ),
                )
            },
            // [DET-HANDOFF-NOT-MANUAL-001 §B.3] A spot published on a DEDUCED departure: still a
            // real offer (full community loop), but it says it is unconfirmed and explains what
            // the two signal buttons below are for.
            Variant("PapSheet · spot sin confirmar (salida deducida)", Placement.Surface) {
                val unconfirmed = FakeData.nearbySpots.first().copy(status = SpotStatus.PROVISIONAL)
                peek(
                    HomeState(
                        nearbySpots = listOf(unconfirmed) + FakeData.nearbySpots.drop(1),
                        userGpsPoint = sampleGps,
                        selection = HomeSelection.Spot(unconfirmed.id),
                    ),
                )
            },
            // …and the same spot after the trip proved no drive: withdrawn. No directions, no
            // "still there?" — every one of those actions is about a space that exists.
            Variant("PapSheet · spot retirado (la salida no ocurrió)", Placement.Surface) {
                val retracted = FakeData.nearbySpots.first().copy(status = SpotStatus.RETRACTED)
                peek(
                    HomeState(
                        nearbySpots = listOf(retracted) + FakeData.nearbySpots.drop(1),
                        userGpsPoint = sampleGps,
                        selection = HomeSelection.Spot(retracted.id),
                    ),
                )
            },
            // [UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001] The exact state of field 2026-08-21
            // 23:46: a deduced departure published the spot while KEEPING the session alive, and
            // the spot reuses the session's id. The owner must see ONE thing — their car — not a
            // free space offering the metre their own car is standing on. The counter reads one
            // less than `nearbySpots` because this spot is not on offer to this viewer.
            Variant("PapSheet · mi plaza provisional NO se me ofrece (salida deducida)", Placement.Surface) {
                val mine = FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)
                val myTwin = FakeData.nearbySpots.first().copy(
                    id = mine.id,
                    location = mine.location,
                    status = SpotStatus.PROVISIONAL,
                )
                peek(
                    HomeState(
                        vehicles = listOf(FakeData.vehicleSedan),
                        activeSessions = listOf(mine),
                        nearbySpots = listOf(myTwin) + FakeData.nearbySpots.drop(1),
                        userGpsPoint = sampleGps,
                    ),
                )
            },
            // One parked car: the peek has no sibling to step to, so it ends at "Me voy".
            Variant("PapSheet · parking seleccionado (Me voy + Directions + editar)", Placement.Surface) {
                peek(
                    HomeState(
                        vehicles = listOf(FakeData.vehicleSedan),
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                        selection = HomeSelection.Parking(FakeData.activeSession.id),
                    ),
                )
            },
            // Two cars parked at once: from the first one's peek, › opens the second — the same
            // gesture as the spot peek, instead of closing this card to hunt for the other marker.
            // [MULTI-PARKING-001] [UI-PEEK-STEPS-BETWEEN-PINS-001]
            Variant("PapSheet · 2 coches aparcados, paso al otro (solo ›)", Placement.Surface) {
                val first = FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)
                val second = FakeData.activeSession.copy(
                    id = "s_active_2",
                    vehicleId = FakeData.vehicleVan.id,
                )
                peek(
                    HomeState(
                        vehicles = listOf(FakeData.vehicleSedan, FakeData.vehicleVan),
                        activeSessions = listOf(first, second),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                        selection = HomeSelection.Parking(first.id),
                    ),
                )
            },
            // The car lane walks VEHICLES, not sessions: from the parked car's peek, › reaches the
            // UNPARKED van — whose page is its add-parking modal, instead of a dead end back at
            // the chip list. [UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001]
            Variant("PapSheet · aparcado + coche sin aparcar (› abre Aparcar)", Placement.Surface) {
                val parked = FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)
                peek(
                    HomeState(
                        vehicles = listOf(FakeData.vehicleSedan, FakeData.vehicleVan),
                        activeSessions = listOf(parked),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                        selection = HomeSelection.Parking(parked.id),
                    ),
                )
            },
            Variant("PapSheet · add parking (Aparcar aquí)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.AddingParking,
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan),
                        addingParkingVehicleId = FakeData.vehicleSedan.id,
                    ),
                )
            },
            // Offline camera geocode borrowed from a nearby cached cell — the title declares the
            // approximation ("Cerca de …"), never passes it off as exact. [GEO-CACHE-ANSWERS-NEARBY-001]
            Variant("PapSheet · add parking, dirección aproximada (Cerca de …)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.AddingParking,
                        cameraAddressAndPlace = FakeData.addressAndPlaceApproximate,
                        vehicles = listOf(FakeData.vehicleSedan),
                        addingParkingVehicleId = FakeData.vehicleSedan.id,
                    ),
                )
            },
            // …and the add-parking peek is itself a page of the lane: ‹ goes back to the parked
            // sedan's peek. Same ‹ › chrome as every other pin. [UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001]
            Variant("PapSheet · add parking con vecino aparcado (solo ‹)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.AddingParking,
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan, FakeData.vehicleVan),
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        addingParkingVehicleId = FakeData.vehicleVan.id,
                    ),
                )
            },
            // Nothing parked at all — the case the lane exists for: two bare vehicles still step
            // between their add-parking modals. [UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001]
            Variant("PapSheet · add parking, nada aparcado (paso al otro coche)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.AddingParking,
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan, FakeData.vehicleVan),
                        addingParkingVehicleId = FakeData.vehicleSedan.id,
                    ),
                )
            },
            // Edit mode = decide at confirm. Three answers to one question — ¿el MISMO aparcamiento
            // u otro?: Corregir ubicación / Marcar aparcamiento nuevo / Eliminar aparcamiento (rojo,
            // con confirmación). Su banner es el único que nombra el eje: corregir mantiene el
            // tiempo aparcado. [UX-PARKED-STATE-001][COPY-PARKING-EDIT-THREE-ANSWERS-ONE-QUESTION-001]
            Variant("PapSheet · edit parking (corregir / nuevo / eliminar)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.AddingParking,
                        editingParkingId = FakeData.activeSession.id,
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan),
                    ),
                )
            },
            Variant("PapSheet · add spot (chips tamaño)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.Reporting,
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                    ),
                )
            },
            // Zone form: banner + icon picker + radius slider + privacy toggle. The NAME is not
            // here — it is asked in the confirm dialog. [HOME-ATOMIZE-001 F3] [UI-ZONE-MANAGE-001]
            Variant("PapSheet · add zona (formulario)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.AddingZone,
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        addingZoneName = "Casa",
                    ),
                )
            },
            // Edit mode = same form + the destructive escape (rojo, con confirmación), reached
            // from the chip's pencil instead of the old × on the map. [UI-ZONE-MANAGE-001]
            Variant("PapSheet · editar zona (con borrar)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.AddingZone,
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        addingZoneName = "Casa",
                        addingZoneIconKey = ZoneIcon.HOME,
                        addingZoneRadius = 180f,
                        addingZoneIsPrivate = true,
                        editingZoneId = "zone-1",
                    ),
                )
            },
            Variant("Peek · cargando (skeleton)", Placement.Surface) { peek(HomeState()) },
            // The session's vehicle hasn't resolved from Room yet (vehicles omitted) → the peek's
            // vehicle lead breathes a skeleton instead of flashing the generic car for one frame.
            // [UI-VEHICLE-ICON-SKELETON-001]
            Variant("Peek · coche resolviéndose (skeleton icono)", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                    ),
                )
            },
            // A live trip OUTRANKS a parked car as the peek subject: driving the Corolla while the
            // sedan sits parked → the peek says "EN RUTA", not the sedan's parked header.
            // [UI-BROWSE-DRIVING-OVER-PARKED-001]
            Variant("Peek · en ruta gana al aparcado", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        // El título en ruta sale del coche, no de la cámara: sin esto el peek dice
                        // "dirección desconocida" aunque la cámara tenga una.
                        // [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
                        userAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan, FakeData.vehicleCorolla),
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                        drivingMeta = com.rndeveloper.paparcar.presentation.home.DrivingMeta(
                            vehicleId = FakeData.vehicleCorolla.id,
                            phase = com.rndeveloper.paparcar.domain.detection.DetectionPhase.Driving,
                        ),
                    ),
                )
            },
            Variant("Sheet · coche + spots") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        activeSessions = listOf(FakeData.activeSession),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                    ),
                )
            },
            Variant("Sheet · spots primero (sin coche)") {
                sheet(HomeState(hasCorePermissions = true, userGpsPoint = sampleGps, nearbySpots = FakeData.nearbySpots))
            },
            // Driving chip: the monitored vehicle's trip is in progress (drivingPuck.vehicleId == its
            // id, no active session) → chip shows "Conduciendo" + radar halo, floated first. [CHIP-DRIVING-001]
            Variant("Sheet · coche conduciendo (driving)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        // En ruta la card rellena su sublínea con DÓNDE va el coche, sacada de su
                        // propio geocode. [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
                        userAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan),
                        nearbySpots = FakeData.nearbySpots,
                        drivingMeta = com.rndeveloper.paparcar.presentation.home.DrivingMeta(
                            vehicleId = FakeData.vehicleSedan.id,
                            phase = com.rndeveloper.paparcar.domain.detection.DetectionPhase.Driving,
                        ),
                    ),
                )
            },
            // El caso que no puede romperse: viaje vivo con el geocode aún en vuelo. Ni sublínea de
            // relleno ni "dirección desconocida" — la card dice "En ruta" y calla.
            // [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
            Variant("Sheet · en ruta sin dirección todavía") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(FakeData.vehicleSedan),
                        nearbySpots = FakeData.nearbySpots,
                        drivingMeta = com.rndeveloper.paparcar.presentation.home.DrivingMeta(
                            vehicleId = FakeData.vehicleSedan.id,
                            phase = com.rndeveloper.paparcar.domain.detection.DetectionPhase.Driving,
                        ),
                    ),
                )
            },
            // Candidate phase: stopped + walking away → chip flips to "Aparcando…" in green. [DET-PHASE-001]
            Variant("Sheet · coche candidato (aparcando)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(FakeData.vehicleSedan),
                        nearbySpots = FakeData.nearbySpots,
                        drivingMeta = com.rndeveloper.paparcar.presentation.home.DrivingMeta(
                            vehicleId = FakeData.vehicleSedan.id,
                            phase = com.rndeveloper.paparcar.domain.detection.DetectionPhase.Candidate,
                        ),
                    ),
                )
            },
            // Single vehicle → full-width HomeVehicleCard: identity + watch badge + size chip, and a
            // footer with the parked address (location icon + "Aparcado en …" + chevron). [HOME-VEH-REFINE-001]
            Variant("Sheet · 1 coche aparcado (card + dirección)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(FakeData.vehicleSedan),
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        nearbySpots = FakeData.nearbySpots,
                    ),
                )
            },
            // 2+ vehicles → compact chips. Azul calmado = mi coche vigilado (glifo BT o radar +
            // borde); azul vivo + halo = conduciendo; gris = sin vigilar. El pie es el ACENTO:
            // direccion en azul calmado (aparcado) o "Sin marcar" gris. Las plazas siguen siendo
            // las unicas verdes. [HOME-VEH-REFINE-001] [UI-COLOR-DOCTRINE-001]
            Variant("Sheet · chips mixtos (aparcado + sin marcar)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(
                            FakeData.vehicleSedan,   // asistido + aparcado → glifo radar azul + direccion azul
                            FakeData.vehicleCorolla, // BT + sin marcar → glifo BT azul + "Sin marcar" gris
                            FakeData.vehicleMoto,    // sin vigilar + aparcado → todo gris + direccion
                        ),
                        activeSessions = listOf(
                            FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id),
                            FakeData.activeSessionSupermarket.copy(vehicleId = FakeData.vehicleMoto.id),
                        ),
                        nearbySpots = FakeData.nearbySpots,
                    ),
                )
            },
            // El estado que el pie del chip compacto no sabía contar: uno de los coches EN RUTA
            // junto a otro aparcado. El que conduce cambia el pin de aparcado por la ruta que se
            // dibuja sola; el aparcado no se entera. [UI-CHIP-ROUTE-GLYPH-001]
            Variant("Sheet · chips 2+ con uno en ruta (glifo de ruta)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        // El pie del chip en ruta dice "En ruta · <calle>": la palabra late, la
                        // calle no. [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
                        userAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan, FakeData.vehicleCorolla),
                        activeSessions = listOf(
                            FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id),
                        ),
                        nearbySpots = FakeData.nearbySpots,
                        drivingMeta = com.rndeveloper.paparcar.presentation.home.DrivingMeta(
                            vehicleId = FakeData.vehicleCorolla.id,
                            phase = com.rndeveloper.paparcar.domain.detection.DetectionPhase.Driving,
                        ),
                    ),
                )
            },
            // All unmarked → every chip shows the "Sin marcar" glyph across the three watch tiers.
            Variant("Sheet · chips sin marcar (BT + activo/inactivo)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(
                            FakeData.vehicleSedan,   // asistido → glifo radar azul + borde azul
                            FakeData.vehicleCorolla, // BT → glifo BT azul + borde azul
                            FakeData.vehicleMoto,    // sin vigilar → glifo hueco gris + borde neutro
                            FakeData.vehicleVan,     // BT → glifo BT azul + borde azul
                        ),
                        nearbySpots = FakeData.nearbySpots,
                    ),
                )
            },
            Variant("Sheet · vacío (dashed + avisar plaza)") {
                sheet(HomeState(hasCorePermissions = true, nearbySpots = emptyList()))
            },
        ),
    ),
    ScreenGroup(
        "Home · compatibilidad (SpotFit)",
        listOf(
            Variant("OPTIMAL (mismo carrocería)", Placement.Surface) {
                spotFit(
                    fitSpot(VehicleSize.MEDIUM_SUV, CarbodyType.HATCHBACK_MEDIUM),
                    fitVehicle(VehicleSize.MEDIUM_SUV, CarbodyType.HATCHBACK_MEDIUM),
                )
            },
            Variant("FITS (coche ≤ plaza)", Placement.Surface) {
                spotFit(fitSpot(VehicleSize.MEDIUM_SUV), fitVehicle(VehicleSize.MICRO_SMALL))
            },
            Variant("DOES_NOT_FIT (coche > plaza)", Placement.Surface) {
                spotFit(fitSpot(VehicleSize.MICRO_SMALL), fitVehicle(VehicleSize.VAN_HIGH))
            },
            Variant("UNKNOWN (plaza sin tamaño)", Placement.Surface) {
                spotFit(fitSpot(null), fitVehicle(VehicleSize.MEDIUM_SUV))
            },
        ),
    ),
    ScreenGroup(
        "Panel de cifras",
        listOf(
            Variant("Plus · mes completo") {
                StatsContent(state = statsPanelState(), onNavigateBack = {}, onIntent = {})
            },
            // Free entra al panel: ve la primera cifra entera, el resto tapado y la fila Plus. No se
            // le cierra la puerta, se le ensena la habitacion. [STATS-PANEL-001]
            Variant("Free · una cifra y el resto tapado") {
                StatsContent(
                    state = statsPanelState(isPlus = false),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // Un enchufable es el UNICO caso que enciende los dos tiles de consumo a la vez, y no
            // habia ninguno en los fakes (gasolina, hibrido, gasolina, diesel). Sin esta variante,
            // el tile de kWh no se veria nunca en la galeria.
            // [STATS-A-COMPUTED-LITRE-WITH-NO-READER-001]
            Variant("Enchufable · litros Y kWh") {
                StatsContent(
                    state = statsPanelState(vehiclesOverride = plugInHybridFleet()),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            Variant("Periodo sin aparcamientos") {
                StatsContent(
                    state = statsPanelState(sessions = emptyList()),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            Variant("Sin resolver (skeleton)") {
                StatsContent(state = StatsState(), onNavigateBack = {}, onIntent = {})
            },
        ),
    ),
    ScreenGroup(
        "Historial",
        listOf(
            // El raíl, el punto pulsante y el wash de la sesión viva llevan la identidad del coche
            // de esa página del pager — no un verde fijo. [UI-HISTORY-IDENTITY-AND-SOURCE-001]
            Variant("Lista · coche asistido (verde)") {
                history(HistoryState(timeline = resolvedHistory(FakeData.allSessions)))
            },
            Variant("Lista · coche Bluetooth (azul)") {
                history(
                    HistoryState(timeline = resolvedHistory(FakeData.allSessions)),
                )
            },
            Variant("Lista · coche sin vigilancia (gris)") {
                history(
                    HistoryState(timeline = resolvedHistory(FakeData.allSessions)),
                )
            },
            Variant("Filtro: esta semana") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(FakeData.allSessions, HistoryFilter.ThisWeek),
                        activeFilter = HistoryFilter.ThisWeek,
                    ),
                )
            },
            // [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001] Los km del bloque salen sólo de las
            // sesiones CON ruta. En un coche mixto (unas por Coordinator, otras por BT o legacy)
            // la cifra describía un subconjunto sin decirlo; aquí se ve diciéndolo.
            Variant("Km de cobertura parcial (mixto BT + ruta)") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(
                            FakeData.allSessions.mapIndexed { i, session ->
                                // Sólo una de cada tres trae ruta — el resto son el carril BT, que
                                // despierta en el destino y nunca traza el viaje.
                                if (i % 3 == 0) session.copy(routeDistanceMeters = 4_200f)
                                else session.copy(routeDistanceMeters = null)
                            },
                        ),
                    ),
                )
            },
            // ── Tarjeta de cifras: la SEGUNDA card de Actividad. [STATS-UI-001] ──────────────
            // Titulada por su periodo, como la del gráfico lo está por su cuenta: las dos viven
            // bajo el mismo encabezado porque el mismo filtro manda sobre ambas.
            Variant("Cifras · Plus (todo visible)") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(FakeData.allSessions, HistoryFilter.ThisMonth),
                        activeFilter = HistoryFilter.ThisMonth,
                    ),
                    isPlus = true,
                )
            },
            // En free se ve ENTERA la primera cifra y el resto va tapado. El tapado es una barra,
            // no un desenfoque: Modifier.blur no hace nada por debajo de API 31 y el velo se
            // habría leído perfectamente en cualquier móvil viejo. [MONEY-001]
            Variant("Cifras · free (una cifra + resto tapado)") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(
                            FakeData.allSessions, HistoryFilter.ThisMonth, isPlus = false,
                        ),
                        activeFilter = HistoryFilter.ThisMonth,
                    ),
                    isPlus = false,
                )
            },
            // Coche recién dado de alta: nada llega a su umbral, así que la tarjeta enseña sólo la
            // cifra donde el cero SÍ es una respuesta (plazas cedidas). Ningún "0" de relleno.
            Variant("Cifras · por debajo de los umbrales") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(FakeData.allSessions.take(2), HistoryFilter.ThisMonth),
                        activeFilter = HistoryFilter.ThisMonth,
                    ),
                )
            },
            // Las DOS caras que antes eran el mismo valor: "no hay nada" frente a "aún no lo sé".
            // [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001]
            Variant("Vacío (historial resuelto, sin sesiones)") {
                history(HistoryState(timeline = resolvedHistory(emptyList())))
            },
            Variant("Sin resolver (skeleton)") {
                history(HistoryState(timeline = HistoryTimeline.Unresolved))
            },
        ),
    ),
    ScreenGroup(
        "Settings",
        listOf(
            Variant("Con perfil") { SettingsContent(state = SettingsState(userProfile = sampleProfile)) },
            Variant("Sin perfil") { SettingsContent(state = SettingsState(userProfile = null)) },
            // [WIDGET-ACTIONS-002] La fila "añadir el widget" solo existe cuando el launcher
            // soporta el pin sheet — esta es su cara visible.
            Variant("Con fila de widget (launcher con pin)") {
                SettingsContent(state = SettingsState(userProfile = sampleProfile, canPinWidget = true))
            },
            Variant("Detección/notif off, imperial") {
                SettingsContent(
                    state = SettingsState(
                        userProfile = sampleProfile,
                        autoDetectParking = false,
                        // Detection OFF → the "Parking detected" notif sub-row is dimmed + locked.
                        notifyParkingDetected = false,
                    ),
                    themeMode = ThemeMode.DARK,
                    imperialUnits = true,
                )
            },
            // [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001] La preferencia sigue en true (su
            // default) y aun así el maestro se pinta APAGADO: no puede afirmar que la detección
            // corre justo encima de su propia fila de salud diciendo qué le falta. La fila
            // "Aparcamiento detectado" cae con él.
            Variant("Permisos incompletos (el maestro se apaga)") {
                SettingsContent(
                    state = SettingsState(
                        userProfile = sampleProfile,
                        // Amber health row + "Fix"
                        missingDetectionPermissions = setOf(RequiredPermission.BACKGROUND_LOCATION),
                        isLocationServicesEnabled = true,
                    ),
                )
            },
            // El otro camino al mismo apagado: permisos completos pero el GPS del sistema off.
            Variant("GPS apagado (el maestro se apaga)") {
                SettingsContent(
                    state = SettingsState(
                        userProfile = sampleProfile,
                        missingDetectionPermissions = emptySet(),
                        isLocationServicesEnabled = false,
                    ),
                )
            },
            Variant("Detección lista + BT configurado") {
                SettingsContent(
                    state = SettingsState(
                        userProfile = sampleProfile,
                        missingDetectionPermissions = emptySet(),
                        isLocationServicesEnabled = true,
                        isBatteryOptimizationExempt = true,
                        activeVehicleId = "v1",
                        btDeviceConfigured = true,
                    ),
                )
            },
            Variant("Fiabilidad REDUCED (OEM agresivo)") {
                SettingsContent(
                    state = SettingsState(
                        userProfile = sampleProfile,
                        // Permissions fine, but aggressive OEM + no BT + no exemption → amber
                        // reliability row with its own Fix. [DET-RELIABILITY-001]
                        missingDetectionPermissions = emptySet(),
                        isLocationServicesEnabled = true,
                        isBatteryOptimizationExempt = false,
                        btDeviceConfigured = false,
                        detectionReliability = DetectionReliabilityLevel.REDUCED,
                    ),
                )
            },
            Variant("Diálogo borrar cuenta") {
                SettingsContent(state = SettingsState(userProfile = sampleProfile, showDeleteAccountConfirmation = true))
            },
            // El estado que antes no existía: el diálogo se cerraba en el mismo update que levantaba
            // la bandera, así que el spinner que se veía era el del botón de la danger zone, detrás.
            // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
            Variant("Borrando cuenta") {
                SettingsContent(
                    state = SettingsState(
                        userProfile = sampleProfile,
                        showDeleteAccountConfirmation = true,
                        isDeletingAccount = true,
                    ),
                )
            },
            Variant("Diálogo enviar diagnóstico") {
                SettingsContent(state = SettingsState(userProfile = sampleProfile, showSendDiagnosticsConfirmation = true))
            },
            Variant("Diagnóstico con descripción") {
                SettingsContent(
                    state = SettingsState(
                        userProfile = sampleProfile,
                        showSendDiagnosticsConfirmation = true,
                        diagnosticsMessage = sampleReportMessage,
                    ),
                )
            },
            // El contador solo aparece cerca del techo — esta variante existe para verlo.
            // [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001]
            Variant("Diagnóstico al límite") {
                SettingsContent(
                    state = SettingsState(
                        userProfile = sampleProfile,
                        showSendDiagnosticsConfirmation = true,
                        diagnosticsMessage = sampleReportMessageNearLimit,
                    ),
                )
            },
            Variant("Enviando diagnóstico") {
                SettingsContent(
                    state = SettingsState(
                        userProfile = sampleProfile,
                        showSendDiagnosticsConfirmation = true,
                        diagnosticsMessage = sampleReportMessage,
                        isSendingDiagnostics = true,
                    ),
                )
            },
        ),
    ),
    // [MONEY-001] Paparcar Plus: el paywall y las superficies gateadas, en free y en plus.
    // Desde UI-PAYWALL-THE-PITCH-IS-OURS-... el paywall es NUESTRO en los dos casos, así que la
    // galería ya puede enseñar la pantalla real y no solo un sucedáneo sin tienda: lo único que
    // cambia entre un build que vende y uno que no son los precios, que vienen de la tienda.
    ScreenGroup(
        "Paparcar Plus",
        listOf(
            // Con planes: comparativa + las dos suscripciones con precio. Los precios salen del
            // FakeEntitlementRepository — en prod los pone la tienda, nunca el código. [MONEY-001]
            Variant("Paywall · con planes (anual preseleccionado)") {
                PaywallContent(state = PaywallState(offers = samplePlusOffers), onIntent = {})
            },
            Variant("Paywall · plan mensual elegido") {
                PaywallContent(
                    state = PaywallState(
                        offers = samplePlusOffers,
                        selectedPlan = PlusPlan.MONTHLY,
                    ),
                    onIntent = {},
                )
            },
            Variant("Paywall · comprando (botón en curso)") {
                PaywallContent(
                    state = PaywallState(offers = samplePlusOffers, purchasing = true),
                    onIntent = {},
                )
            },
            Variant("Paywall · compra fallida o cancelada") {
                PaywallContent(
                    state = PaywallState(offers = samplePlusOffers, purchaseFailed = true),
                    onIntent = {},
                )
            },
            // Sin tienda: la comparativa sigue en pie y NO aparece ni un precio. Es lo que ve un
            // release sin key todavía, y lo que se vería si las ofertas no cargaran.
            Variant("Paywall · restaurando compra") {
                PaywallContent(
                    state = PaywallState(offers = samplePlusOffers, restoring = true),
                    onIntent = {},
                )
            },
            // El caso honesto: pulsa "ya pagé" y esa cuenta de Google no tiene nada. No es un
            // error, así que no va en rojo.
            Variant("Paywall · restaurar no encontró nada") {
                PaywallContent(
                    state = PaywallState(offers = samplePlusOffers, restoreFoundNothing = true),
                    onIntent = {},
                )
            },
            Variant("Paywall · sin tienda (sin ningún precio)") {
                PaywallContent(state = PaywallState(offers = emptyList()), onIntent = {})
            },
            // [FREE-LIMITS-001] El marco sin tienda dice el MOTIVO que trajo al usuario: aquí, el
            // límite de coches. GENERAL (fila de Ajustes) no añade línea — es la variante de arriba.
            Variant("Paywall · sin tienda, venía del límite de coches") {
                PaywallContent(
                    state = PaywallState(offers = emptyList()),
                    onIntent = {},
                    reason = com.rndeveloper.paparcar.domain.entitlement.PaywallReason.VEHICLES,
                )
            },
            Variant("Historial free · pie \"Ver todo con Plus\"") {
                history(
                    HistoryState(timeline = resolvedHistory(agedHalfBeyondFreeWindow(), isPlus = false)),
                )
            },
            Variant("Historial free · TODO más allá de la ventana") {
                // Un historial OCULTO no puede decir "nunca has aparcado": el pie sustituye
                // al empty state y cuenta lo que hay detrás del muro.
                history(
                    HistoryState(timeline = resolvedHistory(agedAllBeyondFreeWindow(), isPlus = false)),
                )
            },
            Variant("Ajustes · Plus ACTIVO (badge, sin chevron)") {
                SettingsContent(
                    state = SettingsState(userProfile = sampleProfile, isPlus = true),
                )
            },
            Variant("PapSheet · form de zona con alerta ON", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.AddingZone,
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        addingZoneName = "Casa",
                        addingZoneNotify = true,
                    ),
                )
            },
        ),
    ),
    // [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001] La atribución OSS. Con datos de muestra: la lista de
    // verdad la genera el build a partir del grafo de Gradle, y en la galería importa la FORMA —
    // las dos clases de licencia (texto entregable vs términos que solo se enlazan) y los dos
    // fallos posibles (cargando, y un APK que salió sin el fichero).
    ScreenGroup(
        "Licencias open source",
        listOf(
            Variant("Lista de librerías") { LicensesContent(state = licensesLoaded) },
            Variant("Cargando") { LicensesContent(state = LicensesState(isLoading = true)) },
            Variant("No se pudo leer") {
                LicensesContent(state = LicensesState(isLoading = false, failedToLoad = true))
            },
            Variant("Detalle · texto completo") {
                LicenseDetailContent(state = licensesLoaded, licenseId = FakeData.licenseApache.id)
            },
            Variant("Detalle · solo enlace") {
                LicenseDetailContent(state = licensesLoaded, licenseId = FakeData.licenseTerms.id)
            },
        ),
    ),
    ScreenGroup(
        "Vehicles",
        listOf(
            // Full history per vehicle so the activity chart + filter bar + timeline actually render
            // (the hero card alone doesn't exercise the History section). [VEHICLES-REDESIGN-001]
            Variant("Lista") {
                // Las vistas derivadas (filtrado + stats) salen del MISMO resolvedor que usa el
                // ViewModel, así la galería ejercita la celda de plazas cedidas y el pie de facts.
                // [VEH-STATS-SAY-SOMETHING-USEFUL-001]
                val history = vehiclesHistoryCache(FakeData.allSessions)
                VehiclesContent(
                    state = VehiclesState(
                        vehicles = FakeData.vehiclesWithStats,
                        isLoading = false,
                        historyCache = history,
                    ),
                )
            },
            // Low-data: a single session in the window → the chart collapses to the compact summary
            // instead of a near-empty full-height chart. [VEHICLES-REDESIGN-001 · Task 3]
            Variant("Pocos datos") {
                val history = vehiclesHistoryCache(FakeData.endedSessions.take(1))
                VehiclesContent(
                    state = VehiclesState(
                        vehicles = FakeData.vehiclesWithStats,
                        isLoading = false,
                        historyCache = history,
                    ),
                )
            },
            // Bluetooth ficha (page 1 = Corolla): blue status pin, no method label. [HOME-VEH-REFINE-001]
            Variant("Ficha Bluetooth") {
                val history = vehiclesHistoryCache(FakeData.allSessions)
                VehiclesContent(
                    state = VehiclesState(
                        vehicles = FakeData.vehiclesWithStats,
                        isLoading = false,
                        selectedVehicleIndex = 1,
                        historyCache = history,
                    ),
                )
            },
            // Inactive ficha (page 2 = Moto): grey pin, MUTED stats it still keeps, plus the separate
            // "Establecer como activo" row (absent for active / BT vehicles). [HOME-VEH-REFINE-001]
            Variant("Ficha inactiva (métricas atenuadas + activar)") {
                val history = vehiclesHistoryCache(FakeData.allSessions)
                VehiclesContent(
                    state = VehiclesState(
                        vehicles = FakeData.vehiclesWithStats,
                        isLoading = false,
                        selectedVehicleIndex = 2,
                        historyCache = history,
                    ),
                )
            },
            Variant("Vacío") { VehiclesContent(state = VehiclesState(vehicles = emptyList(), isLoading = false)) },
            Variant("Cargando") { VehiclesContent(state = VehiclesState(isLoading = true)) },
            // Los coches ya están, su historial NO: cada página cae en el fallback `Unresolved` y
            // pinta skeleton bajo la hero card, nunca el "aún no hay historial".
            // [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001]
            Variant("Historial sin resolver") {
                VehiclesContent(
                    state = VehiclesState(
                        vehicles = FakeData.vehiclesWithStats,
                        isLoading = false,
                        historyCache = emptyMap(),
                    ),
                )
            },
        ),
    ),
    ScreenGroup(
        "Permisos",
        listOf(
            // ── Foco Core: la vista del primer arranque — esencial + teaser, sin muro producer.
            //    [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
            Variant("Primer arranque (todo pendiente)") {
                PermissionsContent(state = PermissionsState(), onRequestPermissions = {})
            },
            Variant("Primer arranque · concedido (Continuar)") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        isLocationServicesEnabled = true,
                    ),
                    onRequestPermissions = {},
                )
            },
            Variant("Prompt de ajustes") {
                PermissionsContent(state = PermissionsState(showSettingsPrompt = true), onRequestPermissions = {})
            },
            // ── Foco Producer: la visita "Activar detección" desde Home/Ajustes.
            Variant("Activar detección (todo pendiente)") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        isLocationServicesEnabled = true,
                    ),
                    onRequestPermissions = {},
                    focus = PermissionsFocus.Producer,
                )
            },
            Variant("Parcial (falta background)") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        hasActivityRecognition = true,
                        hasNotifications = true,
                        isLocationServicesEnabled = true,
                        hasBackgroundLocation = false,
                    ),
                    onRequestPermissions = {},
                    focus = PermissionsFocus.Producer,
                )
            },
            Variant("Críticos concedidos") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        hasBackgroundLocation = true,
                        hasActivityRecognition = true,
                        hasNotifications = true,
                        isLocationServicesEnabled = true,
                    ),
                    onRequestPermissions = {},
                    focus = PermissionsFocus.Producer,
                )
            },
            Variant("Todo + Bluetooth") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        hasBackgroundLocation = true,
                        hasActivityRecognition = true,
                        hasNotifications = true,
                        isLocationServicesEnabled = true,
                        hasBluetoothConnect = true,
                    ),
                    onRequestPermissions = {},
                    focus = PermissionsFocus.Producer,
                )
            },
            Variant("Tarjeta autostart (OEM)") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        hasBackgroundLocation = true,
                        hasActivityRecognition = true,
                        hasNotifications = true,
                        isLocationServicesEnabled = true,
                        isBatteryOptimizationExempt = true,
                        showAutostartCard = true,
                    ),
                    onRequestPermissions = {},
                    focus = PermissionsFocus.Producer,
                )
            },
            Variant("Autostart + batería pendiente") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        hasBackgroundLocation = true,
                        hasActivityRecognition = true,
                        hasNotifications = true,
                        isLocationServicesEnabled = true,
                        // Autostart card visible while the battery exemption is still pending —
                        // the early-onboarding OEM state (previews' "autostart + battery pending").
                        isBatteryOptimizationExempt = false,
                        showAutostartCard = true,
                    ),
                    onRequestPermissions = {},
                    focus = PermissionsFocus.Producer,
                )
            },
            Variant("Fiabilidad REDUCED (callout honesto)") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        hasBackgroundLocation = true,
                        hasActivityRecognition = true,
                        hasNotifications = true,
                        isLocationServicesEnabled = true,
                        // Aggressive OEM + no exemption + no BT pairing → the optional tier swaps
                        // its generic hint for the amber manufacturer-policy callout. [DET-RELIABILITY-001]
                        isBatteryOptimizationExempt = false,
                        showAutostartCard = true,
                        showOemBatteryCard = true,
                        isReliabilityReduced = true,
                    ),
                    onRequestPermissions = {},
                    focus = PermissionsFocus.Producer,
                )
            },
            // Cabecera de nivel de detección — la promesa honesta según el setup. [DET-TIERS-001]
            Variant("Nivel Automático (BT emparejado)") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true, hasBackgroundLocation = true,
                        hasActivityRecognition = true, hasNotifications = true,
                        isLocationServicesEnabled = true, hasBluetoothConnect = true,
                        isBatteryOptimizationExempt = true,
                        currentTier = DetectionTier.AUTOMATIC,
                    ),
                    onRequestPermissions = {},
                    focus = PermissionsFocus.Producer,
                )
            },
            Variant("Nivel Asistido + (exención batería)") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true, hasBackgroundLocation = true,
                        hasActivityRecognition = true, hasNotifications = true,
                        isLocationServicesEnabled = true,
                        isBatteryOptimizationExempt = true,
                        currentTier = DetectionTier.ASSISTED_PLUS,
                    ),
                    onRequestPermissions = {},
                    focus = PermissionsFocus.Producer,
                )
            },
            Variant("Nivel Asistido (base, sin BT ni exención)") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true, hasBackgroundLocation = true,
                        hasActivityRecognition = true, hasNotifications = true,
                        isLocationServicesEnabled = true,
                        currentTier = DetectionTier.ASSISTED,
                    ),
                    onRequestPermissions = {},
                    focus = PermissionsFocus.Producer,
                )
            },
        ),
    ),
    ScreenGroup(
        "Registro de vehículo",
        listOf(
            Variant("Nuevo") { VehicleRegistrationContent(state = VehicleRegistrationState()) },
            Variant("Error de validación (marca vacía)") {
                VehicleRegistrationContent(state = VehicleRegistrationState(hasInteractedWithForm = true))
            },
            // [VEH-FUEL-001] La sección de combustible: el PHEV es la variante más completa (dos
            // consumos), y la moto prueba que un tipo sin carrocería sigue preguntando combustible.
            Variant("Nuevo · PHEV con dos consumos") {
                VehicleRegistrationContent(
                    state = VehicleRegistrationState(
                        brand = "Cupra",
                        model = "Formentor",
                        vehicleType = com.rndeveloper.paparcar.domain.model.VehicleType.CAR,
                        carbodyType = com.rndeveloper.paparcar.domain.model.CarbodyType.SUV_MEDIUM,
                        sizeCategory = VehicleSize.MEDIUM_SUV,
                        fuelType = com.rndeveloper.paparcar.domain.model.FuelType.PLUG_IN_HYBRID,
                        energyLabel = com.rndeveloper.paparcar.domain.model.EnergyLabel.ZERO,
                    ),
                )
            },
            Variant("Nuevo · moto (combustible sin carrocería)") {
                VehicleRegistrationContent(
                    state = VehicleRegistrationState(
                        brand = "Honda",
                        model = "CBR 600",
                        isBrandOther = true,
                        vehicleType = com.rndeveloper.paparcar.domain.model.VehicleType.MOTORCYCLE,
                        sizeCategory = VehicleSize.MOTORCYCLE,
                        fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE,
                    ),
                )
            },
            Variant("Edición") {
                VehicleRegistrationContent(
                    state = VehicleRegistrationState(
                        editingVehicleId = "v-edit",
                        brand = "Toyota",
                        model = "Corolla",
                        sizeCategory = VehicleSize.MEDIUM_SUV,
                        showBrandModelOnSpot = true,
                    ),
                )
            },
            // [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001] La zona de borrado sólo aparece
            // con más de un vehículo (`existingVehicleCount > 1`), así que hasta ahora la galería
            // no la enseñaba en ninguna variante. Estas dos son sus dos caras.
            Variant("Edición · borrar con historial") {
                VehicleRegistrationContent(
                    state = VehicleRegistrationState(
                        editingVehicleId = "v-history",
                        brand = "Toyota",
                        model = "Corolla",
                        sizeCategory = VehicleSize.MEDIUM_SUV,
                        existingVehicleCount = 2,
                        parkingFootprint = VehicleParkingFootprint(
                            endedParkings = 12,
                            hasActiveParking = false,
                        ),
                    ),
                )
            },
            Variant("Edición · aparcado (borrado bloqueado)") {
                VehicleRegistrationContent(
                    state = VehicleRegistrationState(
                        editingVehicleId = "v-parked",
                        brand = "Skoda",
                        model = "Kamiq",
                        sizeCategory = VehicleSize.MEDIUM_SUV,
                        existingVehicleCount = 2,
                        parkingFootprint = VehicleParkingFootprint(
                            endedParkings = 12,
                            hasActiveParking = true,
                        ),
                    ),
                )
            },
            Variant("Con color") {
                VehicleRegistrationContent(
                    state = VehicleRegistrationState(
                        editingVehicleId = "v-color",
                        brand = "Seat",
                        model = "León",
                        vehicleType = VehicleType.CAR,
                        carbodyType = CarbodyType.HATCHBACK_MEDIUM,
                        sizeCategory = VehicleSize.MEDIUM_SUV,
                        color = VehicleColor.RED,
                    ),
                )
            },
            Variant("Guardando") {
                VehicleRegistrationContent(
                    state = VehicleRegistrationState(
                        brand = "Seat",
                        model = "Ibiza",
                        sizeCategory = VehicleSize.MICRO_SMALL,
                        isSaving = true,
                    ),
                )
            },
        ),
    ),
    ScreenGroup(
        "Bluetooth",
        listOf(
            Variant("Lista de dispositivos") {
                BluetoothConfigContent(
                    state = BluetoothConfigState(
                        vehicleName = "Toyota Corolla",
                        bondedDevices = FakeData.btDevices,
                        selectedAddress = FakeData.btDevices.first().address,
                        currentDeviceAddress = FakeData.btDevices.first().address,
                        isBluetoothEnabled = true,
                        isLoading = false,
                    ),
                )
            },
            Variant("Sin dispositivos") {
                BluetoothConfigContent(
                    state = BluetoothConfigState(
                        vehicleName = "Ford Transit",
                        bondedDevices = emptyList(),
                        isBluetoothEnabled = true,
                        isLoading = false,
                    ),
                )
            },
            Variant("BT desactivado") {
                BluetoothConfigContent(
                    state = BluetoothConfigState(vehicleName = "Toyota Corolla", isBluetoothEnabled = false, isLoading = false),
                )
            },
            Variant("Cargando") { BluetoothConfigContent(state = BluetoothConfigState(isLoading = true)) },
        ),
    ),
    ScreenGroup(
        "Onboarding",
        listOf(
            Variant("Onboarding") { OnboardingScreen(onComplete = {}) },
        ),
    ),
    // [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] The guided checklist walked through every
    // position the resolver can put it in. Surface placement: it lives in Home's sheet.
    ScreenGroup(
        "Primeros pasos (checklist)",
        listOf(
            Variant("Paso 1 · marcar aparcamiento", Placement.Surface) {
                firstStepsCard(done = emptySet())
            },
            Variant("Paso 2 · con deteccion · solo explica", Placement.Surface) {
                firstStepsCard(done = setOf(FirstStep.MARK_PARKING))
            },
            // La deteccion parada: el paso deja de describir y pide activarla, con el CTA de la
            // fila que sustituye. [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
            Variant("Paso 2 · deteccion parada · activar", Placement.Surface) {
                firstStepsCard(
                    done = setOf(FirstStep.MARK_PARKING),
                    isAutoDetectionStopped = true,
                )
            },
            // El paso 3 tiene DOS caras según lo que la comunidad pueda ofrecer ahora mismo.
            // [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
            Variant("Paso 3 · sin plazas · avisar de una", Placement.Surface) {
                firstStepsCard(
                    done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH),
                    hasSpotsOnOffer = false,
                )
            },
            Variant("Paso 3 · con plazas · verlas", Placement.Surface) {
                firstStepsCard(
                    done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH),
                    hasSpotsOnOffer = true,
                )
            },
            // Out-of-order progress: reported a spot before ever parking. Step 3 is banked and the
            // checklist STILL asks for step 1 — nothing is watching their car yet.
            Variant("Desordenado · avisó antes de aparcar", Placement.Surface) {
                firstStepsCard(done = setOf(FirstStep.FIND_SPOT))
            },
            // El paso 2.5: solo existe cuando hay algo que reforzar, y con dos caras segun el coche.
            // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
            Variant("Paso 2.5 · vincular BT", Placement.Surface) {
                firstStepsCard(
                    done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH),
                    reinforcement = WatchReinforcement.BLUETOOTH,
                )
            },
            Variant("Paso 2.5 · exencion de bateria", Placement.Surface) {
                firstStepsCard(
                    done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH),
                    reinforcement = WatchReinforcement.BATTERY,
                )
            },
            Variant("Aplazado · sigue guiando", Placement.Surface) {
                firstStepsCard(
                    done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH),
                    reinforcement = WatchReinforcement.BATTERY,
                    deferred = setOf(FirstStep.FORTIFY_WATCH),
                )
            },
            Variant("Cierre honesto · algo quedo aplazado", Placement.Surface) {
                firstStepsCard(
                    done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH, FirstStep.FIND_SPOT),
                    reinforcement = WatchReinforcement.BATTERY,
                    deferred = setOf(FirstStep.FORTIFY_WATCH),
                )
            },
            Variant("Completado · tarjeta de cierre", Placement.Surface) {
                firstStepsCard(done = FirstStep.entries.toSet())
            },
        ),
    ),
    ScreenGroup(
        "Primeros pasos (foco del pin)",
        listOf(
            Variant("Foco sobre el pin central") { firstStepsSpotlight() },
        ),
    ),
    // [ONBOARDING-A-SPOT-IS-BORN-TWO-WAYS-001] Donde por fin se explican las DOS formas de que nazca
    // una plaza. El paso 2 es el que lleva las dos mecánicas de la forma 1 (automática y "Me voy").
    ScreenGroup(
        "Primeros pasos (explicadores)",
        listOf(
            Variant("Paso 1 · qué abre marcar tu aparcamiento", Placement.Surface) {
                FirstStepExplainerScreen(FirstStep.MARK_PARKING, onClose = {})
            },
            Variant("Paso 2 · las 2 formas de liberar (auto + Me voy)", Placement.Surface) {
                FirstStepExplainerScreen(FirstStep.UNDERSTAND_WATCH, onClose = {})
            },
            Variant("Paso 3 · avisar de una plaza ajena", Placement.Surface) {
                FirstStepExplainerScreen(FirstStep.FIND_SPOT, onClose = {})
            },
            // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] El 2.5 tiene la suya: reutilizar
            // la del paso 2 respondia a otra pregunta.
            Variant("Explicacion · reforzar la vigilancia") {
                FirstStepExplainerScreen(FirstStep.FORTIFY_WATCH, onClose = {})
            },
        ),
    ),
    ScreenGroup(
        "Login (BaseLogin)",
        listOf(
            Variant("Vacío") { loginScreen() },
            Variant("Con datos") { loginScreen(email = "ana@paparcar.io", password = "MiPassword123") },
            Variant("Errores de validación") {
                loginScreen(
                    email = "no-es-un-email",
                    password = "123",
                    emailError = "Formato de email no válido",
                    passwordError = "Mínimo 8 caracteres",
                )
            },
            Variant("Cargando") {
                loginScreen(email = "ana@paparcar.io", password = "MiPassword123", isLoading = true)
            },
            // Consent already persisted on this device → no checkbox, CTAs ungated.
            // [AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001]
            Variant("Consentimiento ya dado") {
                loginScreen(email = "ana@paparcar.io", password = "MiPassword123", consentAccepted = true)
            },
        ),
    ),
    ScreenGroup(
        "Mapa · marcadores",
        listOf(
            Variant("Showcase completo (sin tiles)") { markersShowcase() },
        ),
    ),
    // Las cuatro frases de la sección son DISTINTAS a propósito: lista vacía, fallo y país sin
    // precios publicados se leen igual ("no hay precios") si se les deja colapsar en una.
    // [FUEL-STATIONS-LIST-001]
    ScreenGroup(
        "Home · dónde repostar",
        listOf(
            Variant("Plegada (nada pedido todavía)", Placement.Surface) {
                HomeFuelSection(slice = HomeFuelSlice(), onToggle = {})
            },
            Variant("Cargando", Placement.Surface) {
                HomeFuelSection(
                    slice = HomeFuelSlice(isExpanded = true, isLoading = true),
                    onToggle = {},
                )
            },
            // España: precio real por surtidor y la hora del feed al pie.
            Variant("Con precios (España)", Placement.Surface) {
                HomeFuelSection(
                    slice = HomeFuelSlice(
                        isExpanded = true,
                        stations = galleryFuelStations,
                        reportedAt = "09/09/2026 08:30:00",
                    ),
                    onToggle = {},
                )
            },
            // El resto del mundo: gasolineras reales de OSM y ni un precio. Se dice ANTES de las
            // filas, para que una columna de distancias sin cifras no se lea como un fallo de carga.
            Variant("Sin precios publicados (resto del mundo)", Placement.Surface) {
                HomeFuelSection(
                    slice = HomeFuelSlice(
                        isExpanded = true,
                        stations = galleryFuelStations.map { it.copy(price = null) },
                    ),
                    onToggle = {},
                )
            },
            // Francia: la fuente NO sella la respuesta, sella cada precio. El pie acota la edad
            // ("el más antiguo") en vez de inventar una hora para toda la lista, y el precio viejo
            // de la tercera fila es justo el que un sello único taparía.
            // [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
            Variant("Precio con hora PROPIA (Francia)", Placement.Surface) {
                HomeFuelSection(
                    slice = HomeFuelSlice(
                        isExpanded = true,
                        stations = galleryFrenchStations,
                        reportedAt = null,
                    ),
                    onToggle = {},
                )
            },
            Variant("Ninguna a menos de 5 km", Placement.Surface) {
                HomeFuelSection(slice = HomeFuelSlice(isExpanded = true), onToggle = {})
            },
            // ⛔ No es lo mismo que la anterior: decir "no hay ninguna" cuando no se pudo preguntar
            // manda al usuario a conducir sobre algo que nadie comprobó.
            Variant("No se pudo preguntar", Placement.Surface) {
                HomeFuelSection(
                    slice = HomeFuelSlice(isExpanded = true, failed = true),
                    onToggle = {},
                )
            },
            // ⛔ Tampoco es la vacía: sin combustible en el coche NO hubo pregunta. Con un coche
            // pre-fuelType este estado salía como "no hay ninguna a 5 km", fabricado.
            // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
            Variant("Sin combustible en el coche", Placement.Surface) {
                HomeFuelSection(
                    slice = HomeFuelSlice(isExpanded = true, missingFuelType = true),
                    onToggle = {},
                )
            },
            // ── F5b-2: capa de mapa + peek [FUEL-STATIONS-MAP-AND-PEEK-001] ──
            Variant("Peek de gasolinera (España, con precios)", Placement.Surface) {
                peek(
                    HomeState(
                        fuel = HomeFuelSlice(
                            stations = galleryFuelStations,
                            reportedAt = "09/09/2026 08:30:00",
                        ),
                        selection = HomeSelection.FuelStation("1"),
                        userGpsPoint = sampleGps,
                    ),
                )
            },
            // OSM: la estación existe, ningún precio y ninguna afirmación sobre surtidores.
            Variant("Peek de gasolinera (OSM, sin precios)", Placement.Surface) {
                peek(
                    HomeState(
                        fuel = HomeFuelSlice(stations = listOf(galleryOsmStation)),
                        selection = HomeSelection.FuelStation("osm/node/99"),
                        userGpsPoint = sampleGps,
                    ),
                )
            },
            // La cápsula del marcador: con precio y su combustible, sin precio, y seleccionada.
            Variant("Marcadores de la capa", Placement.Surface) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FuelStationMarker(priceText = "1.879", fuelLabel = "Gasoline", selected = false)
                    FuelStationMarker(priceText = null, selected = false)
                    FuelStationMarker(priceText = "1.759", fuelLabel = "Diesel", selected = true)
                }
            },
            // Con 0 plazas la sección de plazas arranca PLEGADA (una línea con chevron) y las
            // gasolineras quedan una fila más abajo — el orden NUNCA cambia; lo que se pliega es
            // el cuerpo. [FUEL-STATIONS-MAP-AND-PEEK-001]
            Variant("Sheet con 0 plazas: plazas plegadas, gasolineras a mano") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        fuel = HomeFuelSlice(
                            isExpanded = true,
                            stations = galleryFuelStations,
                            reportedAt = "09/09/2026 08:30:00",
                        ),
                        userGpsPoint = sampleGps,
                    ),
                )
            },
        ),
    ),
)

/** Una gasolinera del suelo mundial: nombre sí, precios no, y ninguna afirmación de surtidor. */
private val galleryOsmStation = NearbyFuelStation(
    station = FuelStation(
        id = "osm/node/99", name = "Shell", address = "Hauptstraße 12", town = "Berlin",
        latitude = 52.52, longitude = 13.405,
        prices = emptyMap(),
        schedule = "24/7",
    ),
    distanceMeters = 890.0,
    price = null,
)

/** Tres surtidores con la forma real del feed: precio a TRES decimales y uno sin marca. */
private val galleryFuelStations: List<NearbyFuelStation> = listOf(
    NearbyFuelStation(
        station = FuelStation(
            id = "1", name = "Repsol", address = "Calle Mayor 12", town = "Madrid",
            latitude = 40.4168, longitude = -3.7038,
            prices = mapOf(FuelType.DIESEL to StationPrice(Money(1.879, "EUR"), SPAIN_FEED_MS)),
        ),
        distanceMeters = 420.0,
        price = StationPrice(Money(1.879, "EUR"), SPAIN_FEED_MS),
    ),
    NearbyFuelStation(
        station = FuelStation(
            id = "2", name = "Petroprix", address = "Avenida de América 48", town = "Madrid",
            latitude = 40.4268, longitude = -3.7038,
            prices = mapOf(FuelType.DIESEL to StationPrice(Money(1.759, "EUR"), SPAIN_FEED_MS)),
        ),
        distanceMeters = 1_240.0,
        price = StationPrice(Money(1.759, "EUR"), SPAIN_FEED_MS),
    ),
    // Sin nombre ni marca: se la ubica, no se le inventa un nombre. Pasa en el feed francés (que no
    // trae nombre ninguno) y en 1 de cada 25 de OSM.
    NearbyFuelStation(
        station = FuelStation(
            id = "3", name = null, address = "Carretera de Burgos km 14", town = "Alcobendas",
            latitude = 40.5400, longitude = -3.6400,
            prices = mapOf(FuelType.DIESEL to StationPrice(Money(1.765, "EUR"), SPAIN_FEED_MS)),
        ),
        distanceMeters = 3_100.0,
        price = StationPrice(Money(1.765, "EUR"), SPAIN_FEED_MS),
    ),
)

/**
 * Francia: el feed no trae nombre — la primera lleva el que OSM le presta por su id oficial, la
 * segunda se queda sin él — y **cada precio trae su hora**, con una rancia de trece meses, que es
 * la cola real medida en el feed. [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
 */
private val galleryFrenchStations: List<NearbyFuelStation> = listOf(
    frenchStation("fr1", "TotalEnergies", "55 Boulevard de Suisse", 1.99, 620.0, TWO_HOURS_MS),
    frenchStation("fr2", null, "231 Faubourg Bonnefoy", 2.121, 1_450.0, FOUR_DAYS_MS),
    frenchStation("fr3", "Esso", "18 Av. Lazare Carnot", 2.29, 2_900.0, THIRTEEN_MONTHS_MS),
)

private fun frenchStation(
    id: String,
    name: String?,
    address: String,
    price: Double,
    distanceMeters: Double,
    ageMs: Long,
): NearbyFuelStation {
    val reportedAtMs = Clock.System.now().toEpochMilliseconds() - ageMs
    return NearbyFuelStation(
        station = FuelStation(
            id = id, name = name, address = address, town = "Toulouse",
            latitude = 43.6047, longitude = 1.4442,
            prices = mapOf(
                FuelType.GASOLINE to StationPrice(Money(price, "EUR"), reportedAtMs),
            ),
        ),
        distanceMeters = distanceMeters,
        price = StationPrice(Money(price, "EUR"), reportedAtMs),
    )
}

private const val TWO_HOURS_MS = 2 * 60 * 60 * 1000L
private const val FOUR_DAYS_MS = 4 * 24 * 60 * 60 * 1000L
private const val THIRTEEN_MONTHS_MS = 395 * 24 * 60 * 60 * 1000L

/** La hora ÚNICA que España sella para toda la respuesta, un par de horas atrás. */
private val SPAIN_FEED_MS: Long
    get() = Clock.System.now().toEpochMilliseconds() - TWO_HOURS_MS

/**
 * Mock-only static gallery: lists every screen's curated states (mirroring the `*Previews.kt`)
 * and renders each full-screen on-device, with a light/dark toggle and back-to-list. Lets you eyeball
 * loading/empty/error/populated/permission-tier variants without driving the backend.
 */
@Composable
fun StateGalleryScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf<Variant?>(null) }
    // Hoisted OUT of the `if (selected == null)` branch so the list's scroll offset survives
    // entering a variant and coming back — otherwise the LazyColumn leaves composition and
    // restarts at the top.
    val listState = rememberLazyListState()

    if (selected == null) {
        BackHandler(onBack = onBack)
        PaparcarTheme(darkTheme = isSystemInDarkTheme()) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    item {
                        Text(
                            "Galería de estados",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                    galleryGroups.forEach { group ->
                        item {
                            Text(
                                group.title.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(group.variants) { variant ->
                            Surface(
                                onClick = { selected = variant },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(
                                    variant.name,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        ElevatedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                            Text("← Volver al catálogo")
                        }
                    }
                }
            }
        }
    } else {
        val current = selected!!
        // Light/dark is driven by the global toggle (DevRoot) via the shadowed configuration.
        val dark = isSystemInDarkTheme()
        // Surface variants default to the contextual (bottom-sheet) presentation; "Solo" isolates.
        var isolated by remember(current) { mutableStateOf(false) }
        BackHandler { selected = null }
        PaparcarTheme(darkTheme = dark) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                when (current.placement) {
                    Placement.FullScreen -> current.content()
                    Placement.Surface -> if (isolated) {
                        // Bare composable, centered with its own bounds visible.
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Box(Modifier.padding(16.dp)) { current.content() }
                            }
                        }
                    } else {
                        // Contextual: anchored at the bottom like Home's sheet.
                        Column(Modifier.fillMaxSize()) {
                            Spacer(Modifier.weight(1f))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            ) {
                                Box(Modifier.padding(16.dp).navigationBarsPadding()) { current.content() }
                            }
                        }
                    }
                }

                // Control row — top-start, below the status bar, drawn last so it's always tappable.
                // (DEV button lives top-end at the DevRoot level.)
                Row(
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ElevatedButton(onClick = { selected = null }) { Text("← Lista") }
                    if (current.placement == Placement.Surface) {
                        ElevatedButton(onClick = { isolated = !isolated }) {
                            Text(if (isolated) "Completa" else "Solo")
                        }
                    }
                }
            }
        }
    }
}
