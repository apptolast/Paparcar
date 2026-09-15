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
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.rndeveloper.paparcar.presentation.home.model.FirstStepGuide
import com.rndeveloper.paparcar.presentation.home.sections.map.components.HomeLocationPill
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
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.stats.HeatMeasure
import com.rndeveloper.paparcar.domain.usecase.stats.DeriveStatsInsightsUseCase
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
import com.rndeveloper.paparcar.domain.model.DetectionReadiness
import com.rndeveloper.paparcar.domain.permissions.RequiredPermission
import com.rndeveloper.paparcar.presentation.licenses.LicenseDetailContent
import com.rndeveloper.paparcar.presentation.licenses.LicensesContent
import com.rndeveloper.paparcar.presentation.licenses.LicensesState
import com.rndeveloper.paparcar.presentation.settings.SettingsContent
import com.rndeveloper.paparcar.presentation.settings.SettingsState
import com.rndeveloper.paparcar.presentation.vehicleregistration.VehicleRegistrationContent
import com.rndeveloper.paparcar.presentation.vehicleregistration.VehicleRegistrationState
import com.rndeveloper.paparcar.presentation.vehicles.HistoryContent
import com.rndeveloper.paparcar.presentation.vehicles.ChartMetric
import com.rndeveloper.paparcar.presentation.vehicles.HistoryFilter
import com.rndeveloper.paparcar.presentation.vehicles.HistoryState
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarMember
import com.rndeveloper.paparcar.domain.model.SharedCarRole
import com.rndeveloper.paparcar.domain.model.SharedCarState
import com.rndeveloper.paparcar.presentation.vehicles.JoinSharedCarDialog
import com.rndeveloper.paparcar.presentation.vehicles.components.JoinSharedCarDialogContent
import com.rndeveloper.paparcar.presentation.vehicles.components.SharedCarSection
import com.rndeveloper.paparcar.presentation.stats.StatsContent
import com.rndeveloper.paparcar.presentation.stats.StatsState
import com.rndeveloper.paparcar.presentation.vehicles.HistoryTimeline
import com.rndeveloper.paparcar.presentation.vehicles.VehiclesContent
import com.rndeveloper.paparcar.presentation.vehicles.VehiclesState
import com.apptolast.baselogin.presentation.screens.components.DefaultAuthContainer
import com.apptolast.baselogin.presentation.slots.defaultslots.DefaultDivider
import com.rndeveloper.paparcar.di.paparcarLoginConfig
import com.rndeveloper.paparcar.di.paparcarSocialProviders
import com.rndeveloper.paparcar.domain.model.ZoneIcon
import com.rndeveloper.paparcar.presentation.paywall.PaywallContent
import com.rndeveloper.paparcar.presentation.paywall.PaywallState
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.presentation.util.zoneIconFor
import com.rndeveloper.paparcar.ui.auth.paparcarAuthSlots
import com.rndeveloper.paparcar.ui.components.ConnectivityBanner
import com.rndeveloper.paparcar.ui.components.FreeSpotClusterMarker
import com.rndeveloper.paparcar.ui.components.FuelStationMarker
import com.rndeveloper.paparcar.ui.components.PublicParkingMarker
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
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.domain.detection.DetectionPath
import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import com.rndeveloper.paparcar.presentation.home.model.HomeParkingsSlice
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomeFuelLane
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomeParkingsLane
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
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
    hasExploredNearby: Boolean = false,
    // Por defecto con zona privada, para que las variantes de los OTROS pasos sigan enseñando su paso.
    // [FIRST-STEPS-A-PRIVATE-GARAGE-IS-A-PRIVATE-ZONE-001]
    hasPrivateZone: Boolean = true,
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
            // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] El quinto paso se cierra con haber ENTRADO
            // en un carril complementario, asi que la galeria lo ejercita por su senal real y no
            // metiendolo a mano en `done`.
            hasExploredNearby = hasExploredNearby,
            hasPrivateZone = hasPrivateZone,
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
private fun peek(state: HomeState, showsZoneHeader: Boolean = false, laneHeaderLatched: Boolean = false) =
    HomePeekHandle(
        slice = state.toPeekSlice(),
        browseShowsZoneHeader = showsZoneHeader,
        browseLaneHeaderLatched = laneHeaderLatched,
    )

/** Renders the expanded Home sheet (spots list + own-parking card) via homeSheetItems. */
@Composable
private fun sheet(state: HomeState, firstStepGuide: FirstStepGuide? = null) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        homeSheetItems(
            slice = state.toBrowseListSlice(),
            onIntent = {},
            onAction = {},
            firstStepGuide = firstStepGuide,
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
    // Sin zonas los sitios existen igual — se llaman por su calle en vez de "Trabajo".
    // [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]
    zones: List<Zone> = emptyList(),
) = HistoryTimeline.resolve(sessions, filter, isPlus, vehicle, zones)

/**
 * Las sesiones de FakeData, apiladas en DOS sitios reales — y con el reloj de la búsqueda puesto en
 * el mismo par de celdas, para que la rejilla de "buscando plaza" tenga su media.
 * [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001] [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001]
 *
 * Los fakes nacieron repartidos por medio Madrid para que el MAPA tuviera marcadores separados, que
 * es lo correcto para el mapa y lo que impide que exista un solo sitio habitual.
 */
/**
 * Seis aparcamientos colocados para que las reglas de insight lleguen a su umbral
 * [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001]: tres en un sitio caro, siempre el MISMO
 * día de la semana y la misma franja, y tres en otro barato tres días después.
 *
 * ⛔ Las marcas de tiempo se mueven en múltiplos de SIETE DÍAS, que es lo que conserva el día de la
 * semana y la hora — y por tanto la celda de la rejilla. Un desplazamiento cualquiera repartiría los
 * seis en seis celdas de una muestra cada una, ninguna llegaría al mínimo de dos, y la card no se
 * compondría: exactamente lo que pasó al primer intento, en el móvil.
 * [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]
 */
private fun List<UserParking>.withAnExpensiveSlot(): List<UserParking> {
    val anchor = firstOrNull()?.location?.timestamp ?: return this
    return take(GALLERY_INSIGHT_PARKINGS).mapIndexed { i, s ->
        val expensive = i % 2 == 0
        val weeksBack = i / 2
        s.copy(
            location = s.location.copy(
                timestamp = anchor - weeksBack * WEEK_MS - if (expensive) 0L else THREE_DAYS_MS,
                latitude = if (expensive) ZONE_LAT else OTHER_LAT,
                longitude = if (expensive) ZONE_LON else OTHER_LON,
            ),
            endedAtMs = null,
            isActive = false,
            searchTimeMs = if (expensive) EXPENSIVE_HUNT_MS else CHEAP_HUNT_MS,
        )
    }
}

private const val GALLERY_INSIGHT_PARKINGS = 6
private const val WEEK_MS = 7L * 86_400_000L
private const val EXPENSIVE_HUNT_MS = 14L * 60_000L
private const val CHEAP_HUNT_MS = 2L * 60_000L

private fun List<UserParking>.clusteredForTheGallery(): List<UserParking> = mapIndexed { i, s ->
    val atZone = i % 3 != 2
    s.copy(
        location = s.location.copy(
            latitude = if (atZone) ZONE_LAT else OTHER_LAT,
            longitude = if (atZone) ZONE_LON else OTHER_LON,
        ),
        searchTimeMs = s.searchTimeMs ?: ((i % 4) + 2) * 60_000L,
    )
}

private const val ZONE_LAT = 40.4168
private const val ZONE_LON = -3.7038
private const val OTHER_LAT = 40.4090
private const val OTHER_LON = -3.6920

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
    zones: List<Zone> = emptyList(),
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
    val resolved = resolvedHistory(owned, filter, isPlus, vehicles.firstOrNull()?.vehicle, zones)
    return StatsState(
        vehicles = vehicles,
        selectedVehicleIndex = 0,
        filter = filter,
        // ⛔ La galería arma el estado A MANO —no pasa por el ViewModel—, así que los insights hay
        // que derivarlos aquí igual que las cifras: con el estado por defecto la card no se componía
        // NUNCA, y la variante se veía verde sin enseñar su caso (visto en el Oppo, 15-09).
        // [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001] [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]
        insights = DeriveStatsInsightsUseCase()(resolved.filteredSessions, resolved.periodStats),
        filteredSessions = resolved.filteredSessions,
        costPrice = resolved.costPrice,
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
/** Una zona privada dibujada sobre [session], para las variantes de zona privada del detalle.
 *  [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] */
private fun privateZoneOver(session: UserParking) = Zone(
    id = "zone-garage",
    userId = session.userId,
    name = "Garaje",
    lat = session.location.latitude,
    lon = session.location.longitude,
    iconKey = "HOME",
    createdAt = 0L,
    radiusMeters = 50f,
    isPrivate = true,
)

@Composable
private fun parkingDetailSheet(
    session: UserParking,
    vehicle: Vehicle,
    hasOlder: Boolean = true,
    hasNewer: Boolean = true,
    canWithdraw: Boolean = false,
    withdrawing: Boolean = false,
    /** [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] Empty = the park freed a
     *  plaza. Non-empty and covering the session = the private-zone row appears. */
    privateZones: List<Zone> = emptyList(),
) {
    Box(Modifier.fillMaxSize()) {
        HistoryDetailSheet(
            focused = FocusedParking.Resolved(session),
            vehicle = vehicle,
            privateZones = privateZones,
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
            privateZones = emptyList(),
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
        // ⛔ El rótulo de zona ya no es una cápsula: es glifo + nombre con halo de papel, SIN caja,
        // porque una zona no ocupa un punto — lo califica. Aquí se mira junto a los pucks, que es
        // donde se ve si recede como debe. [UI-A-ZONE-IS-AN-AREA-NOT-A-PIN-001]
        MarkerSectionLabel("Zona — pública · privada · nombre largo")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ZoneMarker(name = "Casa", icon = zoneIconFor(ZoneIcon.HOME), isPrivate = false, showName = true)
            ZoneMarker(name = "Trabajo", icon = zoneIconFor(ZoneIcon.WORK), isPrivate = true, showName = true)
            ZoneMarker(
                name = "Gimnasio del barrio",
                icon = zoneIconFor(ZoneIcon.GYM),
                isPrivate = false,
                showName = true,
            )
        }
        // ⛔ Y su otro lado, que es lo único que el rótulo hace SOLO: por debajo del umbral de zoom
        // el nombre se va y queda el glifo. Una variante que solo enseñe el nombre no vigila el
        // umbral. [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]
        MarkerSectionLabel("Zona — bajo el umbral de zoom (solo glifo)")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ZoneMarker(name = "Casa", icon = zoneIconFor(ZoneIcon.HOME), isPrivate = false, showName = false)
            ZoneMarker(name = "Trabajo", icon = zoneIconFor(ZoneIcon.WORK), isPrivate = true, showName = false)
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
        MarkerSectionLabel("Ubicación — 30 m no dice nada · 120 m avisa")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeLocationPill(locationMissing = false, accuracy = 30f, onFixLocation = {})
            HomeLocationPill(locationMissing = false, accuracy = 120f, onFixLocation = {})
        }
        // El segundo mensaje de la misma píldora, y el que trae su propio color: sin ubicación es
        // ÁMBAR («pendiente y lo puedes arreglar»), no rojo, y es lo único pulsable de las dos.
        // Venía de ser un badge en el peek, donde se comía el ancho del nombre y la dirección
        // (issue #27). [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001]
        MarkerSectionLabel("Ubicación — sin ubicación (ámbar, pulsable) vs fix pobre (rojo)")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeLocationPill(locationMissing = true, accuracy = null, onFixLocation = {})
            // ⛔ Sin ubicación MANDA: con los dos a la vez sigue saliendo el ámbar, porque sin fix
            // no hay metros que contar.
            HomeLocationPill(locationMissing = true, accuracy = 120f, onFixLocation = {})
        }
        MarkerSectionLabel("Ubicación — pulsa para verla entrar y salir por la izquierda")
        var gpsDegraded by remember { mutableStateOf(true) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { gpsDegraded = !gpsDegraded }) { Text("Alternar") }
            HomeLocationPill(
                locationMissing = false,
                accuracy = if (gpsDegraded) 120f else 8f,
                onFixLocation = {},
            )
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
            CenterPinSlot { ZoneCenterPin(icon = zoneIconFor(ZoneIcon.HOME), cameraMoving = false, isPrivate = false) }
            CenterPinSlot { ZoneCenterPin(icon = zoneIconFor(ZoneIcon.HOME), cameraMoving = true, isPrivate = false) }
            CenterPinSlot { ZoneCenterPin(icon = zoneIconFor(ZoneIcon.HOME), cameraMoving = false, isPrivate = true) }
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
            // Un viaje vivo ya NO tiene fila en esta superficie: lo cuenta la card del coche con
            // su «Parar» — véanse los variants de sheet «coche candidato» y «chips 2+ con uno en
            // ruta». [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001]
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
            // [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] Los DOS ejes a la
            // vez, que antes se pisaban: la fila de detección dice QUIÉN puso el pin y la nueva
            // dice DÓNDE está y qué pasó por estarlo. Esta ficha decía solo «Zona privada», sin
            // estrategia y sin consecuencia.
            Variant("Zona privada · asistido · furgoneta") {
                parkingDetailSheet(
                    session = FakeData.endedSessions[2].copy(
                        spotType = SpotType.HOME_GEOFENCE,
                        detectionPath = DetectionPath.StepsEgress.label,
                    ),
                    vehicle = FakeData.vehicleVan,
                    privateZones = listOf(privateZoneOver(FakeData.endedSessions[2])),
                )
            },
            // El caso que NO se veía: un pin puesto a mano dentro de la zona conserva
            // MANUAL_REPORT, así que nunca llegaba a decir que estaba en una zona privada — y sí
            // que su salida no publicaría plaza.
            Variant("Zona privada · pin manual") {
                parkingDetailSheet(
                    session = FakeData.endedSessions[2].copy(spotType = SpotType.MANUAL_REPORT),
                    vehicle = FakeData.vehicleVan,
                    privateZones = listOf(privateZoneOver(FakeData.endedSessions[2])),
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
            // Browse ya se abrió una vez: plegado, la cabecera sigue en el CARRIL elegido aunque haya
            // coche aparcado. [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001]
            Variant("PapSheet · plegado tras abrir (carril PARKINGS, coche aparcado)", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan),
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                        nearbyLens = HomeNearbyLens.PARKINGS,
                        parkings = HomeParkingsSlice(
                            parkings = galleryParkings,
                            anchorLat = sampleGps.latitude,
                            anchorLon = sampleGps.longitude,
                        ),
                    ),
                    laneHeaderLatched = true,
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
            // ⛔ Con el sheet ABIERTO la cabecera habla del CARRIL, no de la zona: en Parkings decía
            // «TU ZONA · 6 LIBRES» con 4 parkings en la lista de abajo.
            // [HOME-THE-WATCH-COMES-FIRST-AND-THE-HEADER-FOLLOWS-THE-LANE-001]
            Variant("PapSheet · browse expandido (carril PARKINGS, con cifra)", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        userGpsPoint = sampleGps,
                        // Las plazas SIGUEN existiendo debajo: si la cabecera se dejara llevar por
                        // ellas, esta variante volvería a enseñar la cifra equivocada.
                        nearbySpots = FakeData.nearbySpots,
                        nearbyLens = HomeNearbyLens.PARKINGS,
                        parkings = HomeParkingsSlice(
                            parkings = galleryParkings,
                            // ⛔ El ancla es lo que dice que SE PREGUNTÓ. Sin ella la cifra no sale,
                            // que es justo la variante de abajo.
                            anchorLat = sampleGps.latitude,
                            anchorLon = sampleGps.longitude,
                        ),
                    ),
                    showsZoneHeader = true,
                )
            },
            // ⛔ La que EJERCITA el caso que no se puede leer del otro: nadie ha entrado todavía en
            // Gasolineras, así que la cabecera enseña la MARCA del sujeto y ni un número. Un `0` aquí
            // sería un veredicto sobre el mundo que ninguna fuente ha dado.
            // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
            Variant("PapSheet · browse expandido (carril GASOLINERAS sin preguntar)", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                        nearbyLens = HomeNearbyLens.FUEL,
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
            // Sin ubicación (permiso o GPS): el peek sigue siendo el de siempre y lleva un badge ámbar
            // «SIN UBICACIÓN» que abre permisos — ya no hay bloqueo rojo a pantalla completa.
            // [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
            Variant("PapSheet · browse sin ubicación (badge de aviso)", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        nearbySpots = FakeData.nearbySpots,
                        detectionReadiness = DetectionReadiness.Blocked(setOf(RequiredPermission.FOREGROUND_LOCATION)),
                    ),
                )
            },
            Variant("PapSheet · browse sin ubicación, coche aparcado (badge de aviso)", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan),
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        nearbySpots = FakeData.nearbySpots,
                        detectionReadiness = DetectionReadiness.Blocked(setOf(RequiredPermission.FOREGROUND_LOCATION)),
                    ),
                )
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
            // ⛔ Sin permiso de ubicación el sheet YA NO se vacía: la tira de coches sigue (no tocan
            // la ubicación) y el carril responde por el sitio que miras. Lo único que cambia es que
            // la tarjeta de avisar queda deshabilitada DICIENDO por qué: un aviso comunitario sólo
            // es fiable si sabemos que estabas cerca. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
            // `locationEverAsked = true` NO es relleno: el aviso es para quien YA contestó que no.
            // Sin declararlo, estas dos variantes enseñarían el silencio de la pregunta abierta en
            // vez del aviso que vienen a enseñar. [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
            Variant("Sheet · sin permiso de ubicación, con coche") {
                sheet(
                    HomeState(
                        hasCorePermissions = false,
                        locationEverAsked = true,
                        vehicles = listOf(FakeData.vehicleSedan),
                        nearbySpots = FakeData.nearbySpots,
                        detectionReadiness = DetectionReadiness.Blocked(setOf(RequiredPermission.FOREGROUND_LOCATION)),
                    ),
                )
            },
            // El usuario recién llegado que pulsa «No permitir» y aún no tiene coche: antes veía un
            // mapa mudo y un sheet vacío — ni contenido ni motivo.
            Variant("Sheet · sin permiso de ubicación, sin coche") {
                sheet(
                    HomeState(
                        hasCorePermissions = false,
                        locationEverAsked = true,
                        nearbySpots = FakeData.nearbySpots,
                    ),
                )
            },
            // ⛔ Y el instante ANTERIOR a esos dos: el diálogo del sistema está encima pidiendo la
            // ubicación, y detrás la hoja ya está abierta porque el checklist la abre de golpe. Home
            // se ve entero y CALLADO — sin fila ámbar y sin píldora—, porque acusar de una falta que
            // el usuario está contestando en ese mismo momento es contestar por él. El aviso llega
            // solo cuando se lee la respuesta. [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
            Variant("Sheet · pregunta de ubicación EN EL AIRE (sin aviso)") {
                sheet(
                    HomeState(
                        hasCorePermissions = false,
                        locationEverAsked = true,
                        locationAnswerInFlight = true,
                        vehicles = listOf(FakeData.vehicleSedan),
                        nearbySpots = FakeData.nearbySpots,
                        detectionReadiness = DetectionReadiness.Blocked(setOf(RequiredPermission.FOREGROUND_LOCATION)),
                    ),
                )
            },
            // ⛔ Todavía NADIE ha preguntado: ni fix ni un mapa llevado a ningún sitio, así que no hay
            // centro de consulta. La lista está vacía porque no se ha mirado, y el carril dice eso —
            // «no hay plazas libres cerca» sería un veredicto sobre un sitio que nadie ha visto.
            // [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
            Variant("Sheet · plazas sin preguntar (sin centro)") {
                sheet(
                    HomeState(
                        hasCorePermissions = false,
                        // Ya contestó: el sujeto aquí es el carril sin centro, y se mira con el Home
                        // que de verdad se ve después de la respuesta, con su aviso.
                        // [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
                        locationEverAsked = true,
                        nearbySpots = emptyList(),
                        spotsUnasked = true,
                    ),
                )
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
            // Free entra al panel: ve TODAS las cifras de sus últimos 7 días y la fila Plus que ofrece
            // cualquier periodo. [STATS-PANEL-001] [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]
            Variant("Free · todas las cifras, últimos 7 días") {
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
            // ⛔ El caso que se coló en produccion: recien instalada, el tutorial deja UN
            // aparcamiento —manual, sin ruta, sin cerrar— y con eso la tarjeta se abria para
            // enseñar «0 PLAZAS CEDIDAS» y nada mas. Sin esta variante el estado vacio de la
            // galeria solo cubria la lista VACIA, que no es donde estaba el fallo (user, 14-09).
            // [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]
            Variant("Solo el aparcamiento del tutorial") {
                StatsContent(
                    state = statsPanelState(sessions = listOf(FakeData.activeSession)),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001] El sitio se llama como lo llama
            // el usuario: su zona manda sobre el nombre de la calle.
            //
            // ⛔ Las sesiones se AGRUPAN a mano en dos puntos [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]:
            // las de FakeData están repartidas por medio Madrid (40.409–40.420), así que ningún
            // racimo llega a tres y la tarjeta salía SIEMPRE con su estado vacío — visto en el Oppo
            // el 15-09. Una variante que no puede alcanzar su caso es peor que no tenerla.
            Variant("Sitios · nombrados por tus zonas") {
                StatsContent(
                    state = statsPanelState(
                        sessions = FakeData.allSessions.clusteredForTheGallery(),
                        zones = listOf(
                            Zone(
                                id = "z1", userId = "u", name = "Trabajo",
                                lat = ZONE_LAT, lon = ZONE_LON, iconKey = "WORK", createdAt = 0L,
                            ),
                        ),
                    ),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // Un garaje: la fila lleva candado en vez de pin, porque un sitio privado no libera
            // plaza al salir. [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]
            Variant("Sitios · zona privada (garaje)") {
                StatsContent(
                    state = statsPanelState(
                        sessions = FakeData.allSessions.clusteredForTheGallery(),
                        zones = listOf(
                            Zone(
                                id = "z2", userId = "u", name = "Garaje",
                                lat = ZONE_LAT, lon = ZONE_LON, iconKey = "HOME", createdAt = 0L,
                                isPrivate = true,
                            ),
                        ),
                    ),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // El mismo racimo SIN zona: se llama por la calle que más se repite.
            Variant("Sitios · nombrados por la calle") {
                StatsContent(
                    state = statsPanelState(sessions = FakeData.allSessions.clusteredForTheGallery()),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // Y el estado honesto de un coche que aún no repite: dice qué falta y cuánto, en vez
            // de una lista vacía.
            Variant("Sitios · aún no repites") {
                StatsContent(
                    state = statsPanelState(sessions = FakeData.endedSessions.take(2)),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001] La app concluye: sesiones
            // apiladas en dos sitios y con la búsqueda cara concentrada en una franja, que es lo que
            // hace que las reglas pasen su umbral. Sin esto la card no se compone — y una variante
            // que no alcanza su caso no vigila nada [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001].
            // [STATS-PLUS-REMEMBERS-AND-REPORTS-001] El resumen del mes cerrado. La card real sólo
            // aparece durante los primeros días del mes nuevo, así que aquí se le pasa el estado ya
            // resuelto: la galería enseña la CARD, no el calendario.
            Variant("Resumen · el mes pasado, cerrado") {
                val base = statsPanelState(sessions = FakeData.allSessions.withAnExpensiveSlot())
                StatsContent(
                    state = base.copy(closedMonth = base.periodStats),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001] La proyección existe sólo en un periodo de
            // CALENDARIO y con recorrido suficiente: «Este mes» la enseña, «Todos» —ventana sin
            // final— no puede, y así se ven las dos caras.
            Variant("Ritmo · a dónde va el mes") {
                StatsContent(
                    state = statsPanelState(filter = HistoryFilter.ThisMonth),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            Variant("Ritmo · un periodo sin final (no proyecta)") {
                StatsContent(
                    state = statsPanelState(filter = HistoryFilter.All),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // Las DOS caras del motor, porque las cuatro reglas no caben en un solo periodo: con
            // «Todos» entran los seis aparcamientos y hay dos SITIOS que contrastar; con «Este mes»
            // el sitio barato se queda en dos visitas —y deja de ser un sitio— pero aparece la
            // TENDENCIA contra el periodo anterior. [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001]
            Variant("Insights · el sitio que más te cuesta y tus horas") {
                StatsContent(
                    state = statsPanelState(
                        sessions = FakeData.allSessions.withAnExpensiveSlot(),
                        filter = HistoryFilter.All,
                    ),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            Variant("Insights · la tendencia contra el periodo anterior") {
                StatsContent(
                    state = statsPanelState(sessions = FakeData.allSessions.withAnExpensiveSlot()),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001] La rejilla día × franja, con la medida
            // de tiempo buscando plaza seleccionada — la pregunta que abrió toda la ola.
            Variant("Cuándo · buscando plaza") {
                StatsContent(
                    state = statsPanelState(sessions = FakeData.allSessions.clusteredForTheGallery())
                        .copy(heatMeasure = HeatMeasure.SEARCH_TIME),
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
            // [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001] El mismo periodo, otra serie: los
            // chips eligen QUÉ miden las barras, y el calificador del título sigue a la métrica —
            // una card no puede dibujar una cosa y titularse con otra.
            Variant("Gráfico · serie de kilómetros") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(FakeData.allSessions, HistoryFilter.ThisMonth),
                        activeFilter = HistoryFilter.ThisMonth,
                        chartMetric = ChartMetric.DISTANCE,
                    ),
                )
            },
            Variant("Gráfico · serie de tiempo buscando plaza") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(FakeData.allSessions, HistoryFilter.ThisMonth),
                        activeFilter = HistoryFilter.ThisMonth,
                        chartMetric = ChartMetric.SEARCH_TIME,
                    ),
                )
            },
            // Un coche sin una sola medida no tiene entre qué elegir: la fila de chips no se
            // compone, y la card es exactamente la de siempre.
            Variant("Gráfico · sin métricas que ofrecer (solo aparcamientos)") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(
                            FakeData.allSessions.map {
                                it.copy(routeDistanceMeters = null, searchTimeMs = null)
                            },
                            HistoryFilter.ThisMonth,
                        ),
                        activeFilter = HistoryFilter.ThisMonth,
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
            // En free se ven TODAS las cifras, sobre sus últimos 7 días; la fila Plus de debajo ofrece
            // elegir periodo. [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]
            Variant("Cifras · free (todas, últimos 7 días)") {
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
            // [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001] Las cifras MEDIDAS dicen sobre cuántos
            // viajes hablan, y sin cobertura completa no hay ▲/▼: un delta entre dos subconjuntos
            // distintos compara cuántos viajes se midieron, no cuánto cambió nada.
            Variant("Cifras · cobertura parcial (nota, y sin delta)") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(
                            FakeData.allSessions.mapIndexed { i, session ->
                                if (i % 3 == 0) session
                                else session.copy(routeDistanceMeters = null, searchTimeMs = null)
                            },
                            HistoryFilter.ThisMonth,
                        ),
                        activeFilter = HistoryFilter.ThisMonth,
                    ),
                    isPlus = true,
                )
            },
            // [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001] Un sitio es un racimo de
            // aparcamientos, no una cadena de texto: aquí las mismas sesiones, todas a pocos metros
            // y con el nombre de calle bailando, salen como UN sitio con sus cifras.
            Variant("Sitios · el mismo punto con dos nombres de calle") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(
                            FakeData.allSessions.mapIndexed { i, session ->
                                session.copy(
                                    address = session.address?.copy(
                                        street = if (i % 2 == 0) "Calle de Alcalá" else "Calle de O'Donnell",
                                    ),
                                )
                            },
                            HistoryFilter.All,
                        ),
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
            // [ONBOARDING-SELLS-THE-NEW-PRODUCT-001] La página de valor, con sus tres tarjetas, y la de
            // parkings y gasolineras con su ilustración.
            Variant("Onboarding · qué hace por ti") { OnboardingScreen(onComplete = {}, initialPage = 1) },
            Variant("Onboarding · parkings y gasolineras") { OnboardingScreen(onComplete = {}, initialPage = 2) },
        ),
    ),
    // [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] The guided checklist walked through every
    // position the resolver can put it in. Surface placement: it lives in Home's sheet.
    ScreenGroup(
        "Primeros pasos (checklist)",
        listOf(
            Variant("Paso 1 · marcar aparcamiento", Placement.Surface) {
                firstStepsCard(done = emptySet(), hasPrivateZone = false)
            },
            // [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001] El tutorial SEÑALA en vez de abrir: pestañas de
            // parkings y gasolineras resaltadas (sin seleccionarlas) y la tarjeta de avisar de una plaza.
            Variant("Guía del tutorial · pestañas de parkings y gasolineras") {
                sheet(HomeState(nearbySpots = FakeData.nearbySpots, hasCorePermissions = true), FirstStepGuide.NEARBY_COMPLEMENT_TABS)
            },
            Variant("Guía del tutorial · tarjeta de avisar de una plaza") {
                sheet(HomeState(nearbySpots = FakeData.nearbySpots, hasCorePermissions = true), FirstStepGuide.REPORT_SPOT_CARD)
            },
            // [FIRST-STEPS-A-PRIVATE-GARAGE-IS-A-PRIVATE-ZONE-001] El garaje privado, con sus dos
            // respuestas: «Añadir zona privada» y «No tengo garaje».
            Variant("Paso 2 · zona privada (garaje)", Placement.Surface) {
                firstStepsCard(done = setOf(FirstStep.MARK_PARKING), hasPrivateZone = false)
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
            // El quinto paso: el mapa responde tres preguntas y el onboarding solo presentaba una.
            // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
            Variant("Paso 5 · que mas hay cerca", Placement.Surface) {
                firstStepsCard(
                    done = setOf(
                        FirstStep.MARK_PARKING,
                        FirstStep.UNDERSTAND_WATCH,
                        FirstStep.FIND_SPOT,
                    ),
                )
            },
            // ⛔ La variante que EJERCITA su caso: el paso no se cierra metiendolo en `done`,
            // se cierra por haber entrado en un carril. Si esta se viera igual que la de arriba,
            // la senal no estaria conectada. [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]
            Variant("Paso 5 · cerrado por haber entrado en un carril", Placement.Surface) {
                firstStepsCard(
                    done = setOf(
                        FirstStep.MARK_PARKING,
                        FirstStep.UNDERSTAND_WATCH,
                        FirstStep.FIND_SPOT,
                    ),
                    hasExploredNearby = true,
                )
            },
            Variant("Completado · tarjeta de cierre", Placement.Surface) {
                firstStepsCard(done = FirstStep.entries.toSet())
            },
        ),
    ),
    // [VEH-SHARED-001] El coche que conducen varios, tal como se ve DENTRO de su página: la sección,
    // la hoja de miembros y el diálogo de unirse. No hay pantalla de grupo que enseñar.
    ScreenGroup(
        "Coche compartido",
        listOf(
            Variant("Sin compartir · la fila que lo ofrece", Placement.Surface) {
                SharedCarSection(
                    vehicle = sharedMockVehicle(),
                    sharedCar = null,
                    members = emptyList(),
                    currentUid = "mock_user_001",
                    isOwner = true,
                    mineOnly = false,
                    working = false,
                    onOfferShare = {}, onOpenMembers = {}, onCreateInvite = {},
                    onSetShareRoutes = {}, onSetScope = {},
                )
            },
            Variant("Dueño · código vivo y rutas compartidas", Placement.Surface) {
                SharedCarSection(
                    vehicle = sharedMockVehicle(),
                    sharedCar = sharedCarKamiq.copy(activeInviteCode = "K7QP3M", state = sharedCarState()),
                    members = sharedCarGroup,
                    currentUid = "mock_user_001",
                    isOwner = true,
                    mineOnly = false,
                    working = false,
                    onOfferShare = {}, onOpenMembers = {}, onCreateInvite = {},
                    onSetShareRoutes = {}, onSetScope = {},
                )
            },
            Variant("Miembro · lo aparcó otro, y no comparte rutas", Placement.Surface) {
                SharedCarSection(
                    vehicle = sharedMockVehicle(shareRoutes = false),
                    sharedCar = sharedCarKamiq.copy(state = sharedCarState()),
                    members = sharedCarGroup,
                    currentUid = "mock_user_002",
                    isOwner = false,
                    mineOnly = false,
                    working = false,
                    onOfferShare = {}, onOpenMembers = {}, onCreateInvite = {},
                    onSetShareRoutes = {}, onSetScope = {},
                )
            },
            Variant("Miembro sin emparejar · sus aparcamientos no se detectan", Placement.Surface) {
                SharedCarSection(
                    vehicle = sharedMockVehicle(paired = false),
                    sharedCar = sharedCarKamiq.copy(state = sharedCarState()),
                    members = sharedCarGroup,
                    currentUid = "mock_user_002",
                    isOwner = false,
                    mineOnly = true,
                    working = false,
                    onOfferShare = {}, onOpenMembers = {}, onCreateInvite = {},
                    onSetShareRoutes = {}, onSetScope = {},
                )
            },
            Variant("El coche ya no está aparcado", Placement.Surface) {
                SharedCarSection(
                    vehicle = sharedMockVehicle(),
                    sharedCar = sharedCarKamiq.copy(state = sharedCarState(parked = false)),
                    members = sharedCarGroup,
                    currentUid = "mock_user_002",
                    isOwner = false,
                    mineOnly = false,
                    working = false,
                    onOfferShare = {}, onOpenMembers = {}, onCreateInvite = {},
                    onSetShareRoutes = {}, onSetScope = {},
                )
            },
            Variant("Nadie lo ha aparcado todavía", Placement.Surface) {
                SharedCarSection(
                    vehicle = sharedMockVehicle(),
                    sharedCar = sharedCarKamiq.copy(activeInviteCode = null),
                    members = sharedCarGroup,
                    currentUid = "mock_user_001",
                    isOwner = true,
                    mineOnly = false,
                    working = false,
                    onOfferShare = {}, onOpenMembers = {}, onCreateInvite = {},
                    onSetShareRoutes = {}, onSetScope = {},
                )
            },
            Variant("Unirse · código vacío", Placement.Surface) {
                JoinSharedCarDialogContent(
                    dialog = JoinSharedCarDialog(),
                    onCodeChanged = {}, onSubmit = {}, onDismiss = {},
                )
            },
            Variant("Unirse · el código ha caducado", Placement.Surface) {
                JoinSharedCarDialogContent(
                    dialog = JoinSharedCarDialog(
                        code = "AB3K9P",
                        error = PaparcarError.SharedCar.InviteExpired,
                    ),
                    onCodeChanged = {}, onSubmit = {}, onDismiss = {},
                )
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
            // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] La pagina donde conviven las tres palabras
            // — plaza (de la comunidad), aparcamiento (tuyo) y parking publico (de un tercero) —,
            // asi que es donde mas facil es confundirlas. [COPY-SPOT-IS-NOT-A-PARKING-001]
            Variant("Paso 5 · parkings publicos y gasolineras", Placement.Surface) {
                FirstStepExplainerScreen(FirstStep.EXPLORE_NEARBY, onClose = {})
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
    // Las cuatro frases del carril son DISTINTAS a propósito: lista vacía, fallo y país sin
    // precios publicados se leen igual ("no hay precios") si se les deja colapsar en una.
    // [FUEL-STATIONS-LIST-001]
    //
    // ⛔ Ya no hay variante "plegada": el carril no tiene pliegue — quién está a la vista lo dice la
    // lente, y su fila de chips tiene su propio grupo abajo.
    // [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
    ScreenGroup(
        "Home · dónde repostar",
        listOf(
            Variant("Cargando", Placement.Surface) {
                HomeFuelLane(slice = HomeFuelSlice(isLoading = true))
            },
            // ⛔ La PRIMERA visita, que es la unica que dice de donde sale el precio y que no
            // promete. Va con precios reales debajo a proposito: la linea cualifica unas filas, no
            // sustituye a ninguna frase del carril. [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
            Variant("Primera visita · de donde sale el precio", Placement.Surface) {
                HomeFuelLane(
                    slice = HomeFuelSlice(
                        stations = galleryFuelStations,
                        reportedAt = "09/09/2026 08:30:00",
                    ),
                    isIntroducing = true,
                )
            },
            // España: precio real por surtidor y la hora del feed al pie.
            Variant("Con precios (España)", Placement.Surface) {
                HomeFuelLane(
                    slice = HomeFuelSlice(
                        stations = galleryFuelStations,
                        reportedAt = "09/09/2026 08:30:00",
                    ),
                )
            },
            // El resto del mundo: gasolineras reales de OSM y ni un precio. Se dice ANTES de las
            // filas, para que una columna de distancias sin cifras no se lea como un fallo de carga.
            Variant("Sin precios publicados (resto del mundo)", Placement.Surface) {
                HomeFuelLane(
                    slice = HomeFuelSlice(
                        stations = galleryFuelStations.map { it.copy(price = null) },
                    ),
                )
            },
            // Francia: la fuente NO sella la respuesta, sella cada precio. El pie acota la edad
            // ("el más antiguo") en vez de inventar una hora para toda la lista, y el precio viejo
            // de la tercera fila es justo el que un sello único taparía.
            // [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
            Variant("Precio con hora PROPIA (Francia)", Placement.Surface) {
                HomeFuelLane(
                    slice = HomeFuelSlice(
                        stations = galleryFrenchStations,
                        reportedAt = null,
                    ),
                )
            },
            // Alemania: el feed no sella NADA — ni la respuesta ni el precio — porque el MTS-K es
            // tiempo real por ley. El pie dice esa garantía en vez de callar, que es lo que hacía
            // la primera lista con precios sin ninguna hora que citar.
            // [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
            Variant("Precios en TIEMPO REAL (Alemania)", Placement.Surface) {
                HomeFuelLane(
                    slice = HomeFuelSlice(
                        stations = galleryGermanStations,
                        reportedAt = null,
                        pricesAreLive = true,
                    ),
                )
            },
            Variant("Ninguna a menos de 5 km", Placement.Surface) {
                HomeFuelLane(slice = HomeFuelSlice())
            },
            // ⛔ Recordado del disco: la tercera frase, y la unica que dice que HOY no se pudo
            // preguntar. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
            //
            // Las filas conservan la hora de CADA precio: un precio leido del disco no es menos
            // honesto que uno de la red, porque dice cuando se midio igual que el otro. Lo que
            // anade el disco es que la pregunta de hoy fallo, y eso lo dice el pie.
            //
            // ⛔ Fecha calculada, no el valor por defecto — si no, la variante no ejercita su caso.
            Variant("Recordado del disco (sin conexion)", Placement.Surface) {
                HomeFuelLane(
                    slice = HomeFuelSlice(
                        stations = galleryFrenchStations,
                        reportedAt = null,
                        rememberedAtMs = Clock.System.now().toEpochMilliseconds() - THREE_DAYS_MS,
                    ),
                )
            },
            // ⛔ No es lo mismo que la anterior: decir "no hay ninguna" cuando no se pudo preguntar
            // manda al usuario a conducir sobre algo que nadie comprobó.
            Variant("No se pudo preguntar", Placement.Surface) {
                HomeFuelLane(slice = HomeFuelSlice(failed = true))
            },
            // ⛔ Tampoco es la vacía: sin combustible en el coche NO hubo pregunta. Con un coche
            // pre-fuelType este estado salía como "no hay ninguna a 5 km", fabricado.
            // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
            Variant("Sin combustible en el coche", Placement.Surface) {
                HomeFuelLane(slice = HomeFuelSlice(missingFuelType = true))
            },
            // ⛔ Ni vacía ni fallo: la cámara mira más lejos de lo que el carril sigue, así que no se
            // preguntó nada y el mapa también está vacío. [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
            Variant("Demasiado lejos de ti", Placement.Surface) {
                HomeFuelLane(slice = HomeFuelSlice(tooFar = true))
            },
            // Cargando otra zona CON filas previas: las filas se quedan (casan con las cápsulas que
            // siguen en el mapa), sin esqueleto. [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
            Variant("Cargando otra zona (conserva filas)", Placement.Surface) {
                HomeFuelLane(slice = HomeFuelSlice(isLoading = true, stations = galleryFuelStations))
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
            // La MISMA gasolinera, ya seguida: la estrella se rellena y el botón dice el estado
            // actual, no la acción. ⛔ Sin Plus por ningún lado — seguir es gratis; lo que se paga
            // es que la app la vigile. [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
            Variant("Peek de gasolinera (seguida)", Placement.Surface) {
                peek(
                    HomeState(
                        fuel = HomeFuelSlice(
                            stations = galleryFuelStations,
                            reportedAt = "09/09/2026 08:30:00",
                        ),
                        selection = HomeSelection.FuelStation("1"),
                        userGpsPoint = sampleGps,
                        followedFuelStationKeys = setOf("minetur|1"),
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
            // ⛔ La variante que EJERCITA el ticket, y por eso lleva la lista de plazas LLENA: es el
            // caso en el que repostar quedaba a dos o tres swipes. Con el carril, las gasolineras
            // arrancan a la misma altura tenga la comunidad 0 plazas o 25.
            // [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
            Variant("Sheet en gasolineras CON plazas de sobra encima") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        nearbyLens = HomeNearbyLens.FUEL,
                        nearbySpots = FakeData.nearbySpots,
                        fuel = HomeFuelSlice(
                            stations = galleryFuelStations,
                            reportedAt = "09/09/2026 08:30:00",
                        ),
                        userGpsPoint = sampleGps,
                    ),
                )
            },
        ),
    ),
    // Los parkings publicos NO se parecen a las gasolineras: OSM nombra el 100 % de los surtidores
    // y solo el 16 % de los parkings (2,6 % en Cuenca), asi que la variante SIN NOMBRE es el caso
    // normal, no el borde. [PARKINGS-ON-THE-MAP-001]
    ScreenGroup(
        "Home · parkings publicos",
        listOf(
            Variant("Cargando", Placement.Surface) {
                HomeParkingsLane(slice = HomeParkingsSlice(isLoading = true))
            },
            // ⛔ La PRIMERA visita: de donde salen los parkings y que la ocupacion solo esta
            // donde el ayuntamiento la publica. [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
            Variant("Primera visita · de donde salen los parkings", Placement.Surface) {
                HomeParkingsLane(
                    slice = HomeParkingsSlice(parkings = galleryParkings),
                    isIntroducing = true,
                )
            },
            Variant("Con parkings (nombrados y sin nombre)", Placement.Surface) {
                HomeParkingsLane(slice = HomeParkingsSlice(parkings = galleryParkings))
            },
            Variant("Ninguno a menos de 1,5 km", Placement.Surface) {
                HomeParkingsLane(slice = HomeParkingsSlice())
            },
            // ⛔ No es lo mismo que la anterior: decir "no hay ninguno" cuando no se pudo preguntar
            // manda al usuario a conducir sobre algo que nadie comprobo.
            Variant("No se pudo preguntar", Placement.Surface) {
                HomeParkingsLane(slice = HomeParkingsSlice(failed = true))
            },
            // ⛔ Ni vacía ni fallo: más allá del límite no se pregunta, y el mapa está vacío a la vez.
            // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
            Variant("Demasiado lejos de ti", Placement.Surface) {
                HomeParkingsLane(slice = HomeParkingsSlice(tooFar = true))
            },
            Variant("Cargando otra zona (conserva filas)", Placement.Surface) {
                HomeParkingsLane(slice = HomeParkingsSlice(isLoading = true, parkings = galleryParkings))
            },
            // ⛔ Y esta es la TERCERA frase, que no es un matiz de las dos anteriores: tampoco se
            // pudo preguntar, pero la app ya estuvo aqui y ensena lo que habia, diciendo cuando.
            // [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
            //
            // ⛔ La fecha se calcula AQUI, no se deja en su valor por defecto: una variante que no
            // ejercita su caso se ve identica a la de al lado y no vigila nada — es exactamente lo
            // que paso en DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001.
            Variant("Recordado del disco (sin conexion)", Placement.Surface) {
                HomeParkingsLane(
                    slice = HomeParkingsSlice(
                        parkings = galleryParkings,
                        rememberedAtMs = Clock.System.now().toEpochMilliseconds() - THREE_DAYS_MS,
                    ),
                )
            },
            // Todo declarado: tarifa, aforo, galibo y horario.
            Variant("Peek de parking (todo declarado)", Placement.Surface) {
                peek(
                    HomeState(
                        parkings = HomeParkingsSlice(parkings = galleryParkings),
                        selection = HomeSelection.PublicParking("osm/node/26341193"),
                        userGpsPoint = sampleGps,
                    ),
                )
            },
            // El caso NORMAL en el mundo: sin nombre y sin un solo dato mas. El peek se queda corto
            // a proposito — inventar "gratis" sobre un fee ausente manda al conductor a una barrera.
            Variant("Peek de parking (sin nombre, sin datos)", Placement.Surface) {
                peek(
                    HomeState(
                        parkings = HomeParkingsSlice(parkings = galleryParkings),
                        selection = HomeSelection.PublicParking("osm/way/908575097"),
                        userGpsPoint = sampleGps,
                    ),
                )
            },
            // La capsula: solo la "P". Sin cifra, porque la unica que ofrece OSM es el aforo TOTAL
            // y esa ranura es donde ira el numero de plazas LIBRES en F6b.
            Variant("Marcadores de la capa", Placement.Surface) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PublicParkingMarker(selected = false)
                    PublicParkingMarker(selected = true)
                }
            },
            // ⛔ La P se juzga PEQUEÑA. A 46 dp cualquier dibujo aguanta; donde su ojo se cierra
            // es a 18 dp —la pestaña de carril— y ese es el tamaño al que más gente la ve. Una
            // variante que solo la enseñe grande no vigila nada, que es justo lo que dejó pasar la
            // casita que estuvo aquí un día. [UI-THE-CAR-PARK-WEARS-OUR-OWN-P-001]
            // [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]
            Variant("Nuestra P, de 46 dp a 18 dp", Placement.Surface) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(46.dp, 32.dp, 24.dp, 18.dp).forEach { glyphSize ->
                        Icon(
                            imageVector = PaparcarIcons.CarParkMark,
                            contentDescription = null,
                            tint = PapColor.actionText,
                            modifier = Modifier.size(glyphSize),
                        )
                    }
                }
            },
        ),
    ),
    // El carril de "cerca de ti" y su fila de chips. Las tres variantes llevan la lista de plazas
    // LLENA a propósito: el ticket existe porque con plazas de sobra los complementos se iban a dos
    // o tres swipes, así que una variante con 0 plazas no ejercitaría su caso.
    // [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
    // [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]
    ScreenGroup(
        "Home · carril de cerca de ti",
        listOf(
            // Plazas: la lente por defecto, y la única que lleva cifra desde el primer frame.
            Variant("Lente en PLAZAS (por defecto)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        nearbySpots = FakeData.nearbySpots,
                        userGpsPoint = sampleGps,
                    ),
                )
            },
            // Un complemento YA preguntado lleva su cifra; el otro, ninguna — porque de ese nadie ha
            // preguntado nada. ⛔ Un "· 0" ahí sería un veredicto que ninguna fuente dio.
            // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
            Variant("Lente en PARKINGS (gasolineras aún sin cifra)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        nearbyLens = HomeNearbyLens.PARKINGS,
                        nearbySpots = FakeData.nearbySpots,
                        parkings = HomeParkingsSlice(parkings = galleryParkings),
                        userGpsPoint = sampleGps,
                    ),
                )
            },
            // El caso peor de la fila: los tres chips con su cifra y las etiquetas más largas de los
            // 9 locales (PT "Parques de estacionamento"). La fila SCROLLEA — por eso es un LazyRow.
            Variant("Los tres chips con cifra (fila al límite)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        nearbyLens = HomeNearbyLens.FUEL,
                        nearbySpots = FakeData.nearbySpots,
                        parkings = HomeParkingsSlice(parkings = galleryParkings),
                        fuel = HomeFuelSlice(
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

/** Parkings con la forma REAL del feed de Madrid: uno completo, uno anonimo, uno de superficie. */
private val galleryParkings: List<NearbyPublicParking> = listOf(
    NearbyPublicParking(
        parking = PublicParking(
            id = "osm/node/26341193",
            name = "Parking Benavente",
            operator = "EMT Madrid",
            kind = PublicParkingKind.UNDERGROUND,
            latitude = 40.4157, longitude = -3.7028,
            capacity = 285,
            fee = FeeInfo.PAID,
            maxHeightMeters = 1.8,
            openingHours = "24/7",
        ),
        distanceMeters = 260.0,
    ),
    NearbyPublicParking(
        parking = PublicParking(
            id = "osm/way/908575097",
            name = null,
            operator = null,
            kind = PublicParkingKind.UNKNOWN,
            latitude = 40.4130, longitude = -3.7090,
        ),
        distanceMeters = 540.0,
    ),
    NearbyPublicParking(
        parking = PublicParking(
            id = "osm/node/900002",
            name = null,
            operator = "Ayuntamiento",
            kind = PublicParkingKind.SURFACE,
            latitude = 40.4145, longitude = -3.7025,
            fee = FeeInfo.FREE,
        ),
        distanceMeters = 910.0,
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
    stationKey = "osm|node/99",
    source = "osm",
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
        stationKey = "minetur|1",
        source = "minetur",
        price = StationPrice(Money(1.879, "EUR"), SPAIN_FEED_MS),
    ),
    NearbyFuelStation(
        station = FuelStation(
            id = "2", name = "Petroprix", address = "Avenida de América 48", town = "Madrid",
            latitude = 40.4268, longitude = -3.7038,
            prices = mapOf(FuelType.DIESEL to StationPrice(Money(1.759, "EUR"), SPAIN_FEED_MS)),
        ),
        distanceMeters = 1_240.0,
        stationKey = "minetur|2",
        source = "minetur",
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
        stationKey = "minetur|3",
        source = "minetur",
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
        stationKey = "france|$id",
        source = "france",
        price = StationPrice(Money(price, "EUR"), reportedAtMs),
    )
}

/**
 * Alemania: nombre y marca vienen EN el feed (la fila luce la marca, «ARAL»), y el precio no lleva
 * hora — ninguna existe: el pie del carril dice la garantía de tiempo real del MTS-K.
 * [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
 */
private val galleryGermanStations: List<NearbyFuelStation> = listOf(
    germanStation("de1", "ARAL", "Holzmarktstraße 12/14", 2.329, 540.0),
    germanStation("de2", "HEM", "Wittestr. 16", 2.279, 1_180.0),
    germanStation("de3", "Sprint", "Kniprodestr. 25", 2.309, 2_400.0),
)

private fun germanStation(
    id: String,
    name: String,
    address: String,
    price: Double,
    distanceMeters: Double,
): NearbyFuelStation = NearbyFuelStation(
    station = FuelStation(
        id = id, name = name, address = address, town = "Berlin",
        latitude = 52.521, longitude = 13.438,
        // ⛔ Sin reportedAtMs a propósito: es el caso que la variante existe para ejercitar.
        prices = mapOf(FuelType.GASOLINE to StationPrice(Money(price, "EUR"))),
    ),
    distanceMeters = distanceMeters,
    stationKey = "germany|$id",
    source = "germany",
    price = StationPrice(Money(price, "EUR")),
)

private const val TWO_HOURS_MS = 2 * 60 * 60 * 1000L
private const val THREE_DAYS_MS = 3 * 24 * 60 * 60 * 1000L
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

// ── Coche compartido ─────────────────────────────────────────────────────────
// [VEH-SHARED-001] Fixtures del grupo. Las cifras del espejo viajan siempre; la geometría, solo si
// su autor quiere — por eso hay una variante con el interruptor apagado.

private val sharedCarKamiq = SharedCar(
    carId = "mock_vehicle_001",
    ownerUid = "mock_user_001",
    ownerName = "Marta",
    displayName = "Seat León",
    sizeCategory = VehicleSize.MEDIUM_SUV,
    fuelType = FuelType.GASOLINE,
    consumptionPer100 = 6.4f,
    createdAtMs = 1_757_000_000_000L,
)

private val sharedCarGroup = listOf(
    SharedCarMember("mock_user_001", "Marta", role = SharedCarRole.OWNER, joinedAtMs = 0L),
    SharedCarMember("mock_user_002", "Nacho", joinedAtMs = 0L, sharesRoutes = false),
)

private fun sharedCarState(parked: Boolean = true) = SharedCarState(
    sessionId = "mock_shared_session",
    location = GpsPoint(40.4378, -3.6795, 8f, 1_757_900_000_000L, 0f),
    parkedAtMs = 1_757_900_000_000L,
    authorUid = "mock_user_001",
    authorName = "Marta",
    address = AddressInfo(street = "Calle de Ponzano, 14", city = "Madrid", region = null, country = "España"),
    isParked = parked,
    updatedAtMs = 1_757_900_000_000L,
)

private fun sharedMockVehicle(shareRoutes: Boolean = true, paired: Boolean = true) = Vehicle(
    id = "mock_vehicle_001",
    userId = "mock_user_002",
    name = "Seat León",
    sizeCategory = VehicleSize.MEDIUM_SUV,
    bluetoothDeviceId = if (paired) "AA:BB:CC:DD:EE:FF" else null,
    sharedCarId = "mock_vehicle_001",
    ownerUid = "mock_user_001",
    shareRoutesInGroup = shareRoutes,
)
