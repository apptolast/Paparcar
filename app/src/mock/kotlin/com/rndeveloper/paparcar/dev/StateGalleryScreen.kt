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
import com.rndeveloper.paparcar.domain.model.SpotTone
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
import com.rndeveloper.paparcar.domain.detection.DetectionPause
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.stats.HeatMeasure
import com.rndeveloper.paparcar.domain.stats.buildParkingHeatmap
import com.rndeveloper.paparcar.domain.stats.lockedIntelligence
import com.rndeveloper.paparcar.domain.usecase.stats.DeriveStatsInsightsUseCase
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.presentation.map.FocusedParking
import com.rndeveloper.paparcar.presentation.map.HistoryDetailSheet
import com.rndeveloper.paparcar.presentation.map.RouteNoticePill
import com.rndeveloper.paparcar.ui.components.PapSpotAlertBanner
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.first_steps_spotlight_caption
import paparcar.composeapp.generated.resources.spot_alert_nearby_in_app
import paparcar.composeapp.generated.resources.zone_alert_in_app
import paparcar.composeapp.generated.resources.parking_detail_route_off_device
import paparcar.composeapp.generated.resources.parking_detail_route_recalculating
import paparcar.composeapp.generated.resources.parking_detail_route_refused
import com.rndeveloper.paparcar.presentation.onboarding.FirstStepExplainerScreen
import com.rndeveloper.paparcar.presentation.onboarding.FirstStepsCard
import com.rndeveloper.paparcar.presentation.onboarding.OnboardingScreen
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.WatchReinforcement
import com.rndeveloper.paparcar.domain.onboarding.resolveFirstSteps
import com.rndeveloper.paparcar.ui.components.PapSpotlight
import com.rndeveloper.paparcar.domain.permissions.SystemPermissionDialog
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
import com.rndeveloper.paparcar.presentation.vehicles.StatsPeriod
import com.rndeveloper.paparcar.presentation.vehicles.HistoryState
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarMember
import com.rndeveloper.paparcar.domain.model.SharedCarRole
import com.rndeveloper.paparcar.domain.model.SharedCarState
import com.rndeveloper.paparcar.presentation.vehicles.JoinSharedCarDialog
import com.rndeveloper.paparcar.presentation.vehicles.components.JoinSharedCarDialogContent
import com.rndeveloper.paparcar.presentation.vehicles.components.SharedCarMembersSheet
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
import com.rndeveloper.paparcar.ui.components.VehicleZoneMark
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
import androidx.compose.runtime.CompositionLocalProvider
import com.rndeveloper.paparcar.presentation.util.DistanceUnit
import com.rndeveloper.paparcar.presentation.util.LocalDistanceUnit
import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import com.rndeveloper.paparcar.presentation.home.model.HomeParkingsSlice
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomeFuelFilterBar
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomeFuelLane
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomeParkingFilterBar
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomeParkingsLane
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.VehicleWithStats
import com.rndeveloper.paparcar.domain.model.Money
import kotlin.time.Clock
import com.rndeveloper.paparcar.presentation.home.model.PublicParkingFilter
import com.rndeveloper.paparcar.presentation.home.model.FuelLaneSort

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
    /** La guía está señalando el control de este paso. [ONB-A-GUIDE-ANSWERS-UNTIL-YOU-ACT-001] */
    pointingAtStep: FirstStep? = null,
) {
    FirstStepsCard(
        pointingAtStep = pointingAtStep,
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
    SearchResult("Grafton Street, Dublin", 53.3412, -6.2600),
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
/**
 * Las mismas sesiones, [days] días atrás — para que la galería pueda enseñar un periodo CERRADO.
 * [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]
 *
 * ⛔ Mover el reloj y no la geometría: los sitios, las rutas y el tiempo buscando plaza siguen
 * siendo los mismos, así que lo único que cambia entre una variante y otra es el PERIODO, que es
 * exactamente lo que estas variantes existen para enseñar.
 */
private fun List<UserParking>.shiftedBack(days: Int): List<UserParking> {
    val delta = days * MS_PER_DAY
    return mapIndexed { index, session ->
        session.copy(
            id = "${session.id}-${days}d-$index",
            location = session.location.copy(timestamp = session.location.timestamp - delta),
            // ⛔ Y su FINAL con él. Moviendo sólo el comienzo, una sesión empezaba hace dos años y
            // terminaba hoy: la galería enseñaba «9686 h de duración media», que no es un estado de
            // la app sino un fixture roto — y un fixture roto en la galería se lee como un bug.
            endedAtMs = session.endedAtMs?.minus(delta),
        )
    }
}

private const val MS_PER_DAY = 24L * 60 * 60 * 1000
private const val DAYS_IN_MONTH = 31
private const val DAYS_IN_HALF_YEAR = 183
private const val DAYS_IN_YEAR = 365

private fun resolvedHistory(
    sessions: List<UserParking>,
    // El MISMO periodo con el que abre la pantalla real: si aqui fuera otro, la barra de la galeria
    // encenderia un chip y la lista de debajo estaria filtrada por otro. [STATS-FIRST-CHIP-ANSWERS-NOW-001]
    filter: StatsPeriod = StatsPeriod.Default,
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

/**
 * El mismo sitio, dos meses: cuatro visitas baratas en el mes ANTERIOR y cuatro caras en el actual.
 * Es lo único que hace saltar la vigilancia de sitio, que compara un racimo consigo mismo.
 * [STATS-A-PLACE-CAN-GET-WORSE-001]
 */
private fun List<UserParking>.withAPlaceThatGotWorse(): List<UserParking> {
    val anchor = firstOrNull()?.location?.timestamp ?: return this
    return take(GALLERY_PLACE_TREND_PARKINGS).mapIndexed { i, s ->
        val thisMonth = i % 2 == 0
        val weeksBack = i / 2
        s.copy(
            location = s.location.copy(
                timestamp = anchor - weeksBack * WEEK_MS - if (thisMonth) 0L else MONTH_MS,
                latitude = ZONE_LAT,
                longitude = ZONE_LON,
            ),
            isActive = false,
            endedAtMs = null,
            searchTimeMs = if (thisMonth) EXPENSIVE_HUNT_MS else CHEAP_HUNT_MS,
        )
    }
}

private const val GALLERY_PLACE_TREND_PARKINGS = 8
private const val MONTH_MS = 31L * 86_400_000L
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
    filter: StatsPeriod = StatsPeriod.Default,
    isPlus: Boolean = true,
    sessions: List<UserParking> = FakeData.allSessions,
    vehiclesOverride: List<VehicleWithStats>? = null,
    zones: List<Zone> = emptyList(),
    /** Por que la deteccion no va a medir el proximo viaje; null = si va a medirlo.
     *  [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001] */
    detectionPause: DetectionPause? = null,
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
    // La misma historia leída SIN el recorte del plan, que es lo que decide qué cards salen bajo
    // cristal. Solo se le preguntan booleanos. [MONEY-A-BLUR-IS-ONLY-HONEST-WHEN-THERE-IS-SOMETHING-BEHIND-IT-001]
    val asPlus = resolvedHistory(owned, StatsPeriod.All, true, vehicles.firstOrNull()?.vehicle, zones)
    return StatsState(
        vehicles = vehicles,
        selectedVehicleIndex = 0,
        filter = filter,
        // ⛔ La galería arma el estado A MANO —no pasa por el ViewModel—, así que los insights hay
        // que derivarlos aquí igual que las cifras: con el estado por defecto la card no se componía
        // NUNCA, y la variante se veía verde sin enseñar su caso (visto en el Oppo, 15-09).
        // [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001] [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]
        insights = DeriveStatsInsightsUseCase()(resolved.filteredSessions, resolved.periodStats),
        // ⛔ Y por la MISMA razón, las cards bajo cristal: la galería arma el estado a mano, así que
        // sin esta línea la variante free se vería verde sin un solo borrón — justo lo que el ticket
        // añade. Se calcula con la misma función pura que usa el ViewModel, sobre una resolución
        // «como si fuera Plus» cuyos valores mueren aquí.
        // [MONEY-A-BLUR-IS-ONLY-HONEST-WHEN-THERE-IS-SOMETHING-BEHIND-IT-001]
        // [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]
        locked = lockedIntelligence(
            isPlus = isPlus,
            allTime = asPlus.periodStats,
            insightCount = DeriveStatsInsightsUseCase()(asPlus.filteredSessions, asPlus.periodStats).size,
            heatmapHasData = !buildParkingHeatmap(asPlus.filteredSessions, HeatMeasure.PARKINGS).isEmpty,
            thisMonth = resolvedHistory(
                owned,
                StatsPeriod.ThisMonth,
                true,
                vehicles.firstOrNull()?.vehicle,
                zones,
            ).periodStats,
            // El calendario no cabe en una galería: la variante del mes cerrado lo enciende a mano,
            // igual que la de Plus se pasa su `closedMonth` ya resuelto.
            closedMonth = null,
        ),
        filteredSessions = resolved.filteredSessions,
        costPrice = resolved.costPrice,
        periodStats = resolved.periodStats,
        // ⛔ Del `resolved`, NO el del parámetro: es el periodo que las cifras cubren de verdad, y la
        // card de ritmo hace su aritmética con él. Dejándolo al valor por defecto, las variantes de
        // ritmo se verían verdes sin componer su card.
        // [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001] [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
        statsPeriod = resolved.statsPeriod,
        lifetimeParkings = resolved.lifetimeParkings,
        lifetimeSpotsReleased = resolved.lifetimeSpotsReleased,
        isPlus = isPlus,
        detectionPause = detectionPause,
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
    detectionPause: DetectionPause? = null,
) {
    HistoryContent(
        state = state,
        contentPadding = PaddingValues(0.dp),
        onViewOnMap = { _, _, _ -> },
        onFilterSelected = {},
        // ⛔ Se PASA, aunque no navegue: sin él `onOpenPanel` es null y la fila «Ver todas tus
        // cifras» no se componía en NINGUNA variante, ni Plus ni free — justo la fila cuya regla de
        // visibilidad acaba de cambiar. Un no-op es lo correcto en un catálogo de estados; omitirlo
        // era dejar la fila sin vigilar. [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]
        onOpenStatsPanel = {},
        isPlus = isPlus,
        detectionPause = detectionPause,
    )
}

/**
 * Un historial que SÍ responde al selector de periodo.
 * [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]
 *
 * ⛔ La galería es un catálogo de ESTADOS y por eso su `onFilterSelected` no hace nada: cada
 * variante enseña uno, y tocar no debe llevarte a otro. Pero un selector cuyo trabajo entero es
 * CAMBIAR de periodo no se puede mirar quieto — «Por años» se despliega y no se puede comprobar que
 * lo elegido llega hasta las barras. Así que las variantes de periodo, y sólo ésas, se resuelven de
 * nuevo con la elección: sigue sin haber ViewModel, porque `HistoryTimeline.resolve` es una función
 * pura y el estado es un `remember` de esta pantalla.
 */
@Composable
private fun interactiveHistory(sessions: List<UserParking>, initial: StatsPeriod) {
    var filter by remember(initial) { mutableStateOf(initial) }
    HistoryContent(
        state = HistoryState(
            timeline = resolvedHistory(sessions, filter),
            activePeriod = filter,
        ),
        contentPadding = PaddingValues(0.dp),
        onViewOnMap = { _, _, _ -> },
        onFilterSelected = { filter = it },
        isPlus = true,
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
    name = "Home garage",
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
        // [MAP-A-PARKED-CAR-INSIDE-A-ZONE-WEARS-THE-ZONE-001] Aparcado DENTRO de una zona: el
        // coche lleva el glifo de la zona en su esquina y la zona deja de rotularse en el mapa.
        // Las cuatro combinaciones que importan, porque la esquina es disputada: pública y privada,
        // con y sin duda — con duda, la diana se queda la derecha y la zona se va a la izquierda.
        MarkerSectionLabel("VehicleBadge · DENTRO DE UNA ZONA — pública · privada · privada+diana · pública+pregunta")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            VehicleBadgeMarker(
                carbodyType = CarbodyType.SUV_MEDIUM,
                zoneMark = VehicleZoneMark(zoneIconFor("home"), isPrivate = false),
            )
            VehicleBadgeMarker(
                carbodyType = CarbodyType.SUV_MEDIUM,
                zoneMark = VehicleZoneMark(zoneIconFor("home"), isPrivate = true),
            )
            VehicleBadgeMarker(
                carbodyType = CarbodyType.SEDAN,
                doubt = VehicleMarkerDoubt.APPROXIMATE,
                zoneMark = VehicleZoneMark(zoneIconFor("home"), isPrivate = true),
            )
            VehicleBadgeMarker(
                carbodyType = CarbodyType.SEDAN,
                doubt = VehicleMarkerDoubt.ASKING,
                zoneMark = VehicleZoneMark(zoneIconFor("work"), isPrivate = false),
            )
        }

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
            FreeSpotMarker(reliability = SpotTone.FRESH)
            FreeSpotMarker(reliability = SpotTone.RECENT)
            FreeSpotMarker(reliability = SpotTone.STALE)
            FreeSpotMarker(isManual = true)
            FreeSpotMarker(reliability = SpotTone.FRESH, selected = true)
        }
        // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] La plaza que TODAVÍA NO está libre: alguien
        // acaba de subirse al coche que la ocupa. Violeta, glifo de coche en vez del de plaza
        // regalada, insignia de salida en vez de megáfono, y sin anillo de TTL.
        MarkerSectionLabel("FreeSpot · se libera ahora — normal · seleccionado")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            FreeSpotMarker(reliability = SpotTone.RELEASING)
            FreeSpotMarker(reliability = SpotTone.RELEASING, selected = true)
        }
        // [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] Area spot: the ground dot becomes the target ⊙.
        MarkerSectionLabel("FreeSpot · zona — FRESH · RECENT · seleccionado")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            FreeSpotMarker(reliability = SpotTone.FRESH, approximate = true)
            FreeSpotMarker(reliability = SpotTone.RECENT, approximate = true)
            FreeSpotMarker(reliability = SpotTone.FRESH, selected = true, approximate = true)
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
            ZoneChip(label = "Home garage", iconKey = ZoneIcon.WORK, isPrivate = true, onClick = {}, onEdit = {})
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
            // Las dos frases que explican por qué el mapa NO dibuja una línea. Viven sobre el mapa,
            // no en la ficha, así que la galería las enseña sueltas — que es lo que hay sin un mapa
            // que pintar. [TRIP-A-ROUTE-THAT-ONLY-LIVES-HERE-SAYS-SO-001]
            // El aviso in-app de plaza nueva: el canal que elige el reparto cuando el mapa está
            // delante. [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
            Variant("Aviso · plaza nueva en tu zona") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    PapSpotAlertBanner(
                        text = stringResource(Res.string.zone_alert_in_app, "Casa"),
                        onDismiss = {},
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                }
            },
            // El OTRO carril, y por eso tiene su propia variante: mismo componente, frase distinta y
            // sin nombre de zona que dar. Verlas juntas es la forma de notar si alguna vez vuelven a
            // decir lo mismo. [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
            Variant("Aviso · plaza nueva cerca de ti") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    PapSpotAlertBanner(
                        text = stringResource(Res.string.spot_alert_nearby_in_app),
                        onDismiss = {},
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                }
            },
            Variant("Ruta · recalculando") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RouteNoticePill(
                        text = stringResource(Res.string.parking_detail_route_recalculating),
                        spinning = true,
                    )
                }
            },
            Variant("Ruta · dijiste que no fuiste por ahí") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RouteNoticePill(
                        text = stringResource(Res.string.parking_detail_route_refused),
                        spinning = false,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            },
            Variant("Ruta · se midió aquí y ya no está") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RouteNoticePill(
                        text = stringResource(Res.string.parking_detail_route_off_device),
                        spinning = false,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
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
            // …pero al CERRAR sobre un carril vacío la cabecera pasa al coche, que es lo único que
            // Home siempre tiene que decir en reposo. El latch ya viene decidido de ese cierre — por
            // eso aquí va en `false` y no en `true`: la galería pinta ESTADOS, no transiciones.
            // [HOME-AN-EMPTY-LANE-HANDS-THE-HEADER-BACK-TO-THE-CAR-001]
            // [HOME-THE-SUBJECT-IS-CHOSEN-WHEN-THE-SHEET-CLOSES-001]
            Variant("PapSheet · cerrado sobre carril PARKINGS vacío → coche", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan),
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                        nearbyLens = HomeNearbyLens.PARKINGS,
                        parkings = HomeParkingsSlice(
                            parkings = emptyList(),
                            // Con ancla y sin resultados: preguntado y vacío, que es distinto de
                            // «nadie preguntó». [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
                            anchorLat = sampleGps.latitude,
                            anchorLon = sampleGps.longitude,
                        ),
                    ),
                    laneHeaderLatched = false,
                )
            },
            // ⛔ El caso que la regla protege: el carril se quedó la cabecera al cerrar y DESPUÉS el
            // paneo lo vació. Sigue hablando él, con su propio cero — no se invoca al coche bajo el
            // dedo del usuario. Mismo estado que el de arriba salvo el latch, que es justo la
            // diferencia. [HOME-THE-SUBJECT-IS-CHOSEN-WHEN-THE-SHEET-CLOSES-001]
            Variant("PapSheet · el paneo vació el carril (sigue el carril, en 0)", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan),
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                        nearbyLens = HomeNearbyLens.PARKINGS,
                        parkings = HomeParkingsSlice(
                            parkings = emptyList(),
                            anchorLat = sampleGps.latitude,
                            anchorLon = sampleGps.longitude,
                        ),
                    ),
                    laneHeaderLatched = true,
                )
            },
            // El coche existe pero NO está aparcado: la cabecera es suya igual, y dice de quién es,
            // que no está marcado y qué hacer. Sin botón — el gesto del peek es abrir la hoja.
            // [HOME-AN-UNPARKED-CAR-STILL-HAS-SOMETHING-TO-SAY-001]
            Variant("PapSheet · cerrado sobre carril vacío, coche SIN aparcar", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan),
                        // ⛔ Sin sesión activa: es justo lo que distingue esta variante de la de arriba.
                        activeSessions = emptyList(),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                        nearbyLens = HomeNearbyLens.PARKINGS,
                        parkings = HomeParkingsSlice(
                            parkings = emptyList(),
                            anchorLat = sampleGps.latitude,
                            anchorLon = sampleGps.longitude,
                        ),
                    ),
                    laneHeaderLatched = false,
                )
            },
            // La única excepción que queda: SIN NINGÚN vehículo no hay a quién devolvérsela, así que
            // el carril vacío se queda la cabecera aunque se cierre sobre él.
            // [HOME-AN-EMPTY-LANE-HANDS-THE-HEADER-BACK-TO-THE-CAR-001]
            Variant("PapSheet · cerrado sobre carril vacío, sin ningún vehículo", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                        nearbyLens = HomeNearbyLens.PARKINGS,
                        parkings = HomeParkingsSlice(
                            parkings = emptyList(),
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
            // ⛔ La MISMA cabecera mientras el carril sigue preguntando sobre filas que YA se
            // pintan (la respuesta recordada del disco). La cápsula enseña su cifra: pestaña, filas
            // y contador dicen lo mismo. El latido se reserva para cuando no hay nada debajo.
            // [HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001]
            // [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
            Variant("PapSheet · browse expandido (PARKINGS recordados, aún preguntando)", Placement.Surface) {
                peek(
                    HomeState(
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                        nearbyLens = HomeNearbyLens.PARKINGS,
                        parkings = HomeParkingsSlice(
                            parkings = galleryParkings,
                            isLoading = true,
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
                        pinCameraLat = sampleGps.latitude,
                        pinCameraLon = sampleGps.longitude,
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
                        pinCameraLat = sampleGps.latitude,
                        pinCameraLon = sampleGps.longitude,
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
                        pinCameraLat = sampleGps.latitude,
                        pinCameraLon = sampleGps.longitude,
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
                        pinCameraLat = sampleGps.latitude,
                        pinCameraLon = sampleGps.longitude,
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
                        pinCameraLat = sampleGps.latitude,
                        pinCameraLon = sampleGps.longitude,
                        editingParkingId = FakeData.activeSession.id,
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        vehicles = listOf(FakeData.vehicleSedan),
                    ),
                )
            },
            // ⛔ Nadie ha posado la cámara: sin fix, sin coche y sin zona el mapa se abre en una
            // semilla que no es ningún sitio, así que el pin no puede confirmarse. La razón sustituye
            // al banner y los dos confirms se apagan — el camino de salida es el buscador.
            // [HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001]
            Variant("PapSheet · add parking sin sitio (cámara sin posar)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.AddingParking,
                        vehicles = listOf(FakeData.vehicleSedan),
                        addingParkingVehicleId = FakeData.vehicleSedan.id,
                    ),
                )
            },
            // El pin CAE DENTRO del radio de reporte: banner de instrucción y confirmar vivo.
            // [SPOT-A-REPORT-YOU-CANNOT-SEE-IS-NOT-A-REPORT-001]
            Variant("PapSheet · add spot (chips tamaño)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.Reporting,
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        userGpsPoint = sampleGps,
                        pinCameraLat = sampleGps.latitude,
                        pinCameraLon = sampleGps.longitude,
                    ),
                )
            },
            // El mapa paneado lejos: el banner cambia a la razón y "Confirmar plaza aquí" se apaga.
            Variant("PapSheet · add spot (demasiado lejos)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.Reporting,
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        userGpsPoint = sampleGps,
                        // Barcelona con el fix en Madrid — el caso que el gate existe para cortar.
                        pinCameraLat = 41.3874,
                        pinCameraLon = 2.1686,
                    ),
                )
            },
            // Sin fix: otra frase, porque "estás lejos" sería adivinar dónde estás.
            Variant("PapSheet · add spot (sin ubicación)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.Reporting,
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        pinCameraLat = sampleGps.latitude,
                        pinCameraLon = sampleGps.longitude,
                    ),
                )
            },
            // Zone form: banner + icon picker + radius slider + privacy toggle. The NAME is not
            // here — it is asked in the confirm dialog. [HOME-ATOMIZE-001 F3] [UI-ZONE-MANAGE-001]
            Variant("PapSheet · add zona (formulario)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.AddingZone,
                        pinCameraLat = sampleGps.latitude,
                        pinCameraLon = sampleGps.longitude,
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
                        pinCameraLat = sampleGps.latitude,
                        pinCameraLon = sampleGps.longitude,
                        cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
                        addingZoneName = "Casa",
                        addingZoneIconKey = ZoneIcon.HOME,
                        addingZoneRadius = 180f,
                        addingZoneIsPrivate = true,
                        editingZoneId = "zone-1",
                    ),
                )
            },
            // La misma ausencia en el formulario de zona: guardar está apagado hasta que la cámara
            // se pose en algún sitio. [HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001]
            Variant("PapSheet · add zona sin sitio (cámara sin posar)", Placement.Surface) {
                peek(
                    HomeState(
                        mode = HomeMode.AddingZone,
                        addingZoneName = "Casa",
                    ),
                )
            },
            // ⛔ El esqueleto lo dibuja la PREGUNTA EN VUELO, no la falta de datos: por eso esta
            // variante enciende el geocodificador en vez de dejar el estado vacío.
            // [HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001]
            Variant("Peek · preguntando (skeleton)", Placement.Surface) {
                peek(HomeState(isCameraGeocoding = true))
            },
            // …y la misma pantalla cuando esa pregunta ya CONTESTÓ sin nombre (sin red, sin vecino
            // en caché que prestar): habla, no late. Es el estado de la captura del 26-09 00:59, que
            // se quedaba latiendo para siempre. [HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001]
            Variant("Peek · la respuesta llegó sin nombre", Placement.Surface) { peek(HomeState()) },
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
            // ⛔ QUIÉN HOSPEDA LA NOTICIA, que no estaba en esta galería. Con UN coche, la card
            // hospeda las PREGUNTAS de ese coche —la que tiene plazo, el aviso sin plazo y el
            // «¿dónde está tu coche?»— y entonces el badge suelto no se dibuja. Antes se apilaban
            // las dos tarjetas sobre el mismo coche, una preguntando y la otra afirmando «En ruta».
            // Issue #129. [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001]
            Variant("Sheet · 1 coche + pregunta abierta (la card pregunta)") {
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
                        promptWindow = com.rndeveloper.paparcar.domain.detection.PendingPromptWindow(
                            shownAtMs = 1_758_567_840_000L,
                            vehicleName = "${FakeData.vehicleSedan.brand} ${FakeData.vehicleSedan.model}",
                            street = "Dame Street 30",
                        ),
                    ),
                )
            },
            // ── Las PREGUNTAS que la card hospeda [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001] ──
            //
            // Una por caso, todas con UN coche, porque es la condición que hace que la card pueda
            // contestarlas. Sin estas variantes el cambio sólo se podía comprobar leyendo el código:
            // la galería enseñaba las filas SUELTAS (grupo «HOME · DETECCIÓN»), que es justo lo que
            // dejó de pintarse cuando hay coche.
            //
            // ⛔ Y NO están aquí los avisos de SISTEMA —permiso de ubicación, detección apagada,
            // vigilancia frágil—: desde la corrección del user (23-09) se quedan en el badge, así que
            // una variante suya en este grupo enseñaría exactamente la misma fila suelta que el grupo
            // «HOME · DETECCIÓN» ya cataloga. Tres variantes que enseñan lo mismo no cubren más: se
            // leen como tres casos distintos y hay que abrir las tres para descubrir que no lo son.
            // El contraste con la card se ve en la de vigilancia INTERRUMPIDA, que se queda.
            Variant("Sheet · 1 coche · ¿dónde está tu coche? (sin aparcar aún)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(FakeData.vehicleSedan),
                        nearbySpots = FakeData.nearbySpots,
                        detectionReadiness = DetectionReadiness.Ready(
                            com.rndeveloper.paparcar.domain.detection.ParkingStrategy.COORDINATOR,
                        ),
                    ),
                )
            },
            // Aparcado + el FGS MUERTO: la vigilancia que se prometía ya no existe. El aviso sale
            // ENCIMA de la card, no dentro — es salud del sistema, le pasa a todos los coches y
            // dentro taparía la información de éste con un botón rojo a ancho completo. Esta
            // variante es el contraste que lo enseña: badge arriba, card intacta debajo.
            // [DET-WATCH-HONEST-001] [DET-WATCH-REACTIVATE-001]
            Variant("Sheet · 1 coche · vigilancia INTERRUMPIDA (aviso FUERA de la card)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(FakeData.vehicleSedan),
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        nearbySpots = FakeData.nearbySpots,
                        detectionReadiness = DetectionReadiness.Parked(
                            FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id),
                        ),
                        servicePresence = com.rndeveloper.paparcar.domain.detection.ServicePresence.Dead,
                    ),
                )
            },
            // El aviso SIN plazo: un aparcamiento que la detección nominó y no pudo colocar. No es
            // la pregunta con deadline — dice otra cosa y ofrece otra salida. [DET-NUDGE-PERSIST-001]
            Variant("Sheet · 1 coche · aviso sin plazo (aparcamiento perdido)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(FakeData.vehicleSedan),
                        nearbySpots = FakeData.nearbySpots,
                        pendingParkNudge = com.rndeveloper.paparcar.domain.detection.PendingParkNudge(
                            createdAtMs = 1_758_567_840_000L,
                            source = "gallery",
                            vehicleId = FakeData.vehicleSedan.id,
                        ),
                    ),
                )
            },
            // Y el único que SIGUE siendo del badge: sin coche registrado no hay card que gestione
            // nada, y eso es lo que justifica que el badge exista.
            Variant("Sheet · SIN coche · el badge es lo único que queda") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = emptyList(),
                        nearbySpots = FakeData.nearbySpots,
                        detectionReadiness = DetectionReadiness.Disabled(
                            com.rndeveloper.paparcar.domain.model.DisabledReason.NO_VEHICLE,
                        ),
                    ),
                )
            },
            // Y el contraste, que es lo que impide el fallo peor: con 2+ coches la tira son chips
            // estrechos en un scroll horizontal, donde no cabe un título con sublínea y dos botones
            // y donde el coche del que se habla puede estar fuera de pantalla. Ahí el badge se
            // queda entero. Apartarse sin que nadie recoja la noticia la haría DESAPARECER.
            Variant("Sheet · 2+ coches + pregunta abierta (la fila pregunta)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(FakeData.vehicleSedan, FakeData.vehicleCorolla),
                        nearbySpots = FakeData.nearbySpots,
                        promptWindow = com.rndeveloper.paparcar.domain.detection.PendingPromptWindow(
                            shownAtMs = 1_758_567_840_000L,
                            vehicleName = "${FakeData.vehicleSedan.brand} ${FakeData.vehicleSedan.model}",
                            street = "Dame Street 30",
                        ),
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
            Variant("Free · cifras de 7 días, inteligencia bajo cristal") {
                StatsContent(
                    state = statsPanelState(isPlus = false),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // ⛔ La otra mitad de la regla, y la que de verdad vigila: un borrón sin nada detrás
            // sería publicidad. Un coche que todavía no ha concluido nada no enseña ni una card
            // bloqueada — el panel se ve como se veía antes de que el cristal existiera.
            // [MONEY-A-BLUR-IS-ONLY-HONEST-WHEN-THERE-IS-SOMETHING-BEHIND-IT-001]
            Variant("Free · sin nada que bloquear") {
                StatsContent(
                    state = statsPanelState(isPlus = false, sessions = emptyList()),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // ⛔ La silueta de LISTA no la alcanzaba ninguna variante: con las sesiones por defecto
            // ningún racimo llega a tres, así que «Tus sitios» no se bloqueaba y su preview no lo
            // miraba nadie. Mismo racimo y misma zona que la variante de Plus, con el plan apagado.
            // [UI-A-BLUR-MUST-LOOK-LIKE-THE-CARD-IT-HIDES-001] [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]
            Variant("Free · tus sitios, bajo cristal") {
                StatsContent(
                    state = statsPanelState(
                        isPlus = false,
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
            // El mes cerrado bajo cristal. Se enciende a mano por la misma razón que su gemela de
            // Plus se pasa el `closedMonth` ya resuelto: la card real sólo existe durante los
            // primeros días del mes nuevo, y una galería no puede esperar al día 1.
            Variant("Free · el mes cerrado, bajo cristal") {
                val base = statsPanelState(isPlus = false)
                StatsContent(
                    state = base.copy(locked = base.locked.copy(recap = true)),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // ⛔ Las dos poblaciones que NO estan midiendo, y que necesitan remedios opuestos: una
            // apaga el interruptor, a la otra le falta un permiso y darle un interruptor no le
            // arregla nada. El aviso va ENCIMA de las cifras sin taparlas — quien lo lee tiene
            // cifras de cuando la deteccion si corria.
            // [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]
            Variant("Deteccion apagada · las cifras no crecen") {
                StatsContent(
                    state = statsPanelState(detectionPause = DetectionPause.TURNED_OFF),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            Variant("Deteccion bloqueada · falta un permiso") {
                StatsContent(
                    state = statsPanelState(detectionPause = DetectionPause.MISSING_PERMISSIONS),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // ⛔ Y el aviso sobre la card VACIA, que es el otro sitio donde aparece: sin cifras que
            // respetar, el aviso y el estado vacio tienen que leerse como una sola frase.
            Variant("Deteccion apagada · sin cifras todavia") {
                StatsContent(
                    state = statsPanelState(
                        sessions = listOf(FakeData.activeSession),
                        detectionPause = DetectionPause.TURNED_OFF,
                    ),
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
                                id = "z2", userId = "u", name = "Home garage",
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
                    // ⛔ El precio también, o la variante no ejercita su caso: la fila de gasto de
                    // esta card sólo se compone con `costEstimate`, y `costEstimate` sólo existe si
                    // hay precio. Sin esta línea la galería enseñaba la card SIN la fila que el
                    // ticket acaba de hacer alcanzable. [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]
                    // [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
                    state = base.copy(
                        closedMonth = base.periodStats,
                        closedMonthPrice = base.costPrice,
                    ),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001] La proyección existe sólo en un periodo de
            // CALENDARIO y con recorrido suficiente: «Este mes» la enseña, «Todos» —ventana sin
            // final— no puede, y así se ven las dos caras.
            Variant("Ritmo · a dónde va el mes") {
                StatsContent(
                    state = statsPanelState(filter = StatsPeriod.ThisMonth),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // ⛔ La MISMA card con otro periodo, que es el caso que el ticket arregla: la fila se
            // llamaba «cierre de mes» proyectara lo que proyectara. Aquí tiene que leerse «cierre
            // de año», y la fila «un año a este ritmo» NO puede aparecer: sería la misma cifra
            // dicha dos veces. [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
            Variant("Ritmo · a dónde va el año") {
                StatsContent(
                    state = statsPanelState(filter = StatsPeriod.ThisYear),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            Variant("Ritmo · un periodo sin final (no proyecta)") {
                StatsContent(
                    state = statsPanelState(filter = StatsPeriod.All),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // ⛔ El caso que estaba MAL, y que sólo se ve con el chip de un mes encendido sobre una
            // cuenta free: las cifras son siempre sus últimos 7 días, así que la card de ritmo
            // dividía siete días de gasto por la fracción del MES transcurrida y lo llamaba «cierre
            // de mes». Aquí tiene que verse el título «últimos 7 días» y NINGUNA card de ritmo.
            // [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001] [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]
            Variant("Ritmo · free con el chip del mes (no proyecta nada)") {
                StatsContent(
                    state = statsPanelState(filter = StatsPeriod.ThisMonth, isPlus = false),
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
                        filter = StatsPeriod.All,
                    ),
                    onNavigateBack = {},
                    onIntent = {},
                )
            },
            // [STATS-A-PLACE-CAN-GET-WORSE-001] El MISMO sitio en dos meses: barato el mes pasado,
            // caro este. Sin las dos mitades la regla no tiene con qué comparar y no salta.
            Variant("Insights · un sitio que ha empeorado") {
                StatsContent(
                    state = statsPanelState(
                        sessions = FakeData.allSessions.withAPlaceThatGotWorse(),
                        filter = StatsPeriod.ThisMonth,
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
            // El aviso de deteccion pausada tambien vive en la FICHA, encima de las cifras: es el
            // mismo bloque, y una variante solo en el panel dejaria sin vigilar la mitad de sus
            // sitios. [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]
            Variant("Cifras · deteccion apagada") {
                history(
                    HistoryState(timeline = resolvedHistory(FakeData.allSessions)),
                    detectionPause = DetectionPause.TURNED_OFF,
                )
            },
            Variant("Filtro: esta semana") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(FakeData.allSessions, StatsPeriod.ThisWeek),
                        activePeriod = StatsPeriod.ThisWeek,
                    ),
                )
            },
            // [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001] Los periodos que CIERRAN. El
            // mes pasado sólo se podía mirar por la card del resumen, que se retira el día 8.
            Variant("Filtro: mes pasado (periodo cerrado)") {
                interactiveHistory(
                    sessions = FakeData.allSessions + FakeData.allSessions.shiftedBack(DAYS_IN_MONTH),
                    initial = StatsPeriod.LastMonth,
                )
            },
            // Con más de un trimestre por detrás aparece «Este año» — y es el único periodo que
            // despierta la proyección anual. [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001]
            Variant("Filtro: este año") {
                interactiveHistory(
                    sessions = FakeData.allSessions + FakeData.allSessions.shiftedBack(DAYS_IN_HALF_YEAR),
                    initial = StatsPeriod.ThisYear,
                )
            },
            // Tres años de historia: el chip de años aparece, y «Todos» pasa a dibujar AÑOS. Antes
            // decía «Todos» y enseñaba los últimos doce meses sin decirlo.
            Variant("Tres años: chip de años y «Todos» por años") {
                interactiveHistory(
                    sessions = FakeData.allSessions +
                        FakeData.allSessions.shiftedBack(DAYS_IN_YEAR) +
                        FakeData.allSessions.shiftedBack(2 * DAYS_IN_YEAR),
                    initial = StatsPeriod.All,
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
                        timeline = resolvedHistory(FakeData.allSessions, StatsPeriod.ThisMonth),
                        activePeriod = StatsPeriod.ThisMonth,
                        chartMetric = ChartMetric.DISTANCE,
                    ),
                )
            },
            Variant("Gráfico · serie de tiempo buscando plaza") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(FakeData.allSessions, StatsPeriod.ThisMonth),
                        activePeriod = StatsPeriod.ThisMonth,
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
                            StatsPeriod.ThisMonth,
                        ),
                        activePeriod = StatsPeriod.ThisMonth,
                    ),
                )
            },
            // ── Tarjeta de cifras: la SEGUNDA card de Actividad. [STATS-UI-001] ──────────────
            // Titulada por su periodo, como la del gráfico lo está por su cuenta: las dos viven
            // bajo el mismo encabezado porque el mismo filtro manda sobre ambas.
            Variant("Cifras · Plus (todo visible)") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(FakeData.allSessions, StatsPeriod.ThisMonth),
                        activePeriod = StatsPeriod.ThisMonth,
                    ),
                    isPlus = true,
                )
            },
            // En free se ven TODAS las cifras, sobre sus últimos 7 días, y tiene que verse LA FILA DE
            // ENTRADA AL PANEL igual que un Plus: la de debajo, la Plus, vende el periodo. Estaban
            // enfrentadas —«nunca las dos»— y eso escondía un botón por plan.
            // [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001] [MONEY-001]
            Variant("Cifras · free (todas, últimos 7 días)") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(
                            FakeData.allSessions, StatsPeriod.ThisMonth, isPlus = false,
                        ),
                        activePeriod = StatsPeriod.ThisMonth,
                    ),
                    isPlus = false,
                )
            },
            // [STATS-A-VOLUME-BURNED-IS-QUOTED-IN-LITRES-EVERYWHERE-001] Las mismas cifras leidas
            // por alguien en imperial: distancia en millas, consumo en mpg y —lo que este ticket
            // arregla— el deposito en GALONES y el CO2 en LIBRAS. Es la unica superficie donde se
            // puede comprobar con los ojos que la fila CIERRA: millas / mpg = galones. El fake de
            // preferencias arranca en metrico e ignora el locale, asi que sin proveer la unidad a
            // mano este carril no se puede mirar en el catalogo.
            Variant("Cifras · en imperial (galones y libras)") {
                CompositionLocalProvider(LocalDistanceUnit provides DistanceUnit.IMPERIAL) {
                    history(
                        HistoryState(
                            timeline = resolvedHistory(FakeData.allSessions, StatsPeriod.ThisMonth),
                            activePeriod = StatsPeriod.ThisMonth,
                        ),
                        isPlus = true,
                    )
                }
            },
            // Coche recién dado de alta: nada llega a su umbral, así que la tarjeta enseña sólo la
            // cifra donde el cero SÍ es una respuesta (plazas cedidas). Ningún "0" de relleno.
            Variant("Cifras · por debajo de los umbrales") {
                history(
                    HistoryState(
                        timeline = resolvedHistory(FakeData.allSessions.take(2), StatsPeriod.ThisMonth),
                        activePeriod = StatsPeriod.ThisMonth,
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
                            StatsPeriod.ThisMonth,
                        ),
                        activePeriod = StatsPeriod.ThisMonth,
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
                                        street = if (i % 2 == 0) "Pearse Street" else "Thomas Street",
                                    ),
                                )
                            },
                            StatsPeriod.All,
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
            // [MONEY-A-CARD-THAT-NEVER-APPEARS-IS-NEVER-SOLD-001] Las tres filas con gate blando
            // vistas desde una cuenta FREE: se pintan apagadas —la vista las fuerza con
            // `isPlus && …`— y encenderlas es lo que abre el paywall. Sin esta variante la fila
            // nueva del resumen del mes sólo se veía en su estado de Plus, que es el que NO tiene
            // el problema que la hizo nacer.
            Variant("Notificaciones de Plus · cuenta free") {
                SettingsContent(state = SettingsState(userProfile = sampleProfile, isPlus = false))
            },
            Variant("Notificaciones de Plus · cuenta Plus") {
                SettingsContent(state = SettingsState(userProfile = sampleProfile, isPlus = true))
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
            // Con planes: la lista de lo que añade Plus + las dos suscripciones con precio. Los
            // precios salen del FakeEntitlementRepository — en prod los pone la tienda, nunca el
            // código. [MONEY-001]
            //
            // Esta es la entrada GENERAL (fila de Ajustes): no hay gate que nombrar, así que no
            // lleva tira de motivo. [UI-PAYWALL-THE-FREE-COLUMN-IS-A-LINE-001]
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
            // [FREE-LIMITS-001] [UI-PAYWALL-THE-FREE-COLUMN-IS-A-LINE-001] El MOTIVO que trajo al
            // usuario se lee arriba, bajo la promesa, y se lee SIEMPRE — con tienda y sin ella.
            // Esta variante es la que faltaba: antes el motivo solo se pintaba en la rama sin
            // tienda, así que el caso de la vida real (tienda viva + gate tocado) no se podía ver
            // en la galería porque no existía en la pantalla.
            Variant("Paywall · venía del límite de coches") {
                PaywallContent(
                    state = PaywallState(offers = samplePlusOffers),
                    onIntent = {},
                    reason = com.rndeveloper.paparcar.domain.entitlement.PaywallReason.VEHICLES,
                )
            },
            // Otro gate, otro glifo y otra frase: la tira apunta al sitio del que vienes.
            Variant("Paywall · venía de la ventana de 7 días del historial") {
                PaywallContent(
                    state = PaywallState(offers = samplePlusOffers),
                    onIntent = {},
                    reason = com.rndeveloper.paparcar.domain.entitlement.PaywallReason.HISTORY,
                )
            },
            // Sin tienda: la lista sigue en pie y NO aparece ni un precio. Es lo que ve un release
            // sin key todavía, y lo que se vería si las ofertas no cargaran.
            Variant("Paywall · sin tienda (sin ningún precio)") {
                PaywallContent(state = PaywallState(offers = emptyList()), onIntent = {})
            },
            // Sin tienda Y con gate: la tira de motivo arriba, la línea honesta abajo. El motivo
            // se dice UNA vez — el pie ya no lo repite.
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
                        pinCameraLat = sampleGps.latitude,
                        pinCameraLon = sampleGps.longitude,
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
            // ⛔ La variante que faltaba, y por eso el recorte llegó a device: coche compartido
            // (dueño, con código) Y historial vacío A LA VEZ. Cada mitad se veía por separado —seis
            // variantes de `SharedCarSection` y un «Vacío (historial resuelto)» sin header—, y el
            // fallo solo existe cuando coinciden: el header pasa a no caber en el hueco del estado
            // vacío. Es el estado de una cuenta que acaba de abrir el grupo.
            // [UI-AN-EMPTY-HISTORY-MUST-NOT-CROP-ITS-HEADER-001]
            // [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]
            Variant("Compartido + historial vacío (header que no cabe)") {
                VehiclesContent(
                    state = VehiclesState(
                        vehicles = FakeData.vehiclesWithStats,
                        isLoading = false,
                        historyCache = vehiclesHistoryCache(emptyList()),
                        // Dueño: es quien ve la card del código, la que se cortaba por la mitad.
                        currentUid = sharedCarKamiq.ownerUid,
                        sharedCars = mapOf(
                            FakeData.vehicleSedan.id to sharedCarKamiq.copy(
                                carId = FakeData.vehicleSedan.id,
                                activeInviteCode = "K7QP3M",
                            ),
                        ),
                        sharedMembers = sharedCarGroup,
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
            // ── Estados que solo existen en iOS, pero que pinta la MISMA pantalla ──────────
            // [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
            Variant("Ubicación APROXIMADA (iOS 14+)") {
                // Todo concedido y aun así la detección no puede funcionar: el único estado en que
                // el usuario cree estar servido y no lo está. La fila de precisión va la primera.
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true, hasBackgroundLocation = true,
                        hasActivityRecognition = true, hasNotifications = true,
                        isLocationServicesEnabled = true,
                        hasReducedLocationAccuracy = true,
                        producerGranted = false,
                    ),
                    onRequestPermissions = {},
                    focus = PermissionsFocus.Producer,
                )
            },
            Variant("Sin coprocesador de movimiento") {
                // La fila de actividad NO se pinta y el tier se da por completo: un permiso que el
                // aparato no puede conceder no es una tarea pendiente del usuario.
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true, hasBackgroundLocation = true,
                        hasActivityRecognition = false, isActivityRecognitionAvailable = false,
                        hasNotifications = true, isLocationServicesEnabled = true,
                        producerGranted = true,
                    ),
                    onRequestPermissions = {},
                    focus = PermissionsFocus.Producer,
                )
            },
            Variant("Diálogo de ubicación GASTADO → Ajustes") {
                // Denegado o restringido: el sistema ya no pregunta, así que el CTA es ámbar y
                // lleva a los Ajustes desde el primer frame, sin dos taps a la nada.
                PermissionsContent(
                    state = PermissionsState(
                        isLocationServicesEnabled = true,
                        spentDialogs = setOf(SystemPermissionDialog.LOCATION),
                    ),
                    onRequestPermissions = {},
                    focus = PermissionsFocus.Core,
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
            // [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001] El campo de precio en un móvil
            // estadounidense: la etiqueta dice «gallon» y el texto ya viene convertido, que es el
            // par que NO se puede romper — una etiqueta que cambia sin que cambie el número es el
            // bug entero. El estado se teclea aquí a mano porque el ViewModel no vive en la galería.
            Variant("Edición · precio por galón (EE. UU.)") {
                // ⛔ Lo que pone el campo en galones es `priceCurrency = "USD"`, no un
                // `LocalPumpGallon` a mano: la unidad la nombra la moneda en la que se teclea.
                // [FUEL-A-PRICE-IS-QUOTED-WHERE-IT-IS-SOLD-001]
                VehicleRegistrationContent(
                    state = VehicleRegistrationState(
                        editingVehicleId = "v-us",
                        brand = "Ford",
                        model = "F-150",
                        vehicleType = com.rndeveloper.paparcar.domain.model.VehicleType.CAR,
                        carbodyType = com.rndeveloper.paparcar.domain.model.CarbodyType.PICKUP,
                        sizeCategory = VehicleSize.VAN_HIGH,
                        fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE,
                        priceCurrency = "USD",
                        fuelPriceText = "3.499",
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
                        vehicleName = "Second car",
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
                    state = BluetoothConfigState(vehicleName = "Second car", isBluetoothEnabled = false, isLoading = false),
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
            // El paso ANUNCIA que va a señalar arriba, antes de que la hoja baje a enseñar el chip.
            // [ONB-A-GUIDE-ANSWERS-UNTIL-YOU-ACT-001]
            Variant("Paso 2 · señalando el chip de zona", Placement.Surface) {
                firstStepsCard(
                    done = setOf(FirstStep.MARK_PARKING),
                    hasPrivateZone = false,
                    pointingAtStep = FirstStep.PRIVATE_ZONE,
                )
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
            // ⛔ La SECCION se quedo con lo que se MIRA: quien conduce, y el alcance de las cifras
            // de debajo. El codigo y el interruptor de rutas se mudaron a la hoja, y «Donde esta el
            // coche» se borro —lo decia por segunda vez, contradiciendo a la hero card—.
            // [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]
            Variant("Sin compartir · la fila que lo ofrece", Placement.Surface) {
                SharedCarSection(
                    sharedCar = null,
                    members = emptyList(),
                    currentUid = "mock_user_001",
                    isOwner = true,
                    mineOnly = false,
                    working = false,
                    onOfferShare = {}, onOpenMembers = {}, onSetScope = {},
                )
            },
            Variant("Compartido · quien conduce y el alcance", Placement.Surface) {
                SharedCarSection(
                    sharedCar = sharedCarKamiq.copy(activeInviteCode = "K7QP3M", state = sharedCarState()),
                    members = sharedCarGroup,
                    currentUid = "mock_user_001",
                    isOwner = true,
                    mineOnly = false,
                    working = false,
                    onOfferShare = {}, onOpenMembers = {}, onSetScope = {},
                )
            },
            Variant("Compartido · alcance en «solo yo»", Placement.Surface) {
                SharedCarSection(
                    sharedCar = sharedCarKamiq.copy(state = sharedCarState()),
                    members = sharedCarGroup,
                    currentUid = "mock_user_002",
                    isOwner = false,
                    mineOnly = true,
                    working = false,
                    onOfferShare = {}, onOpenMembers = {}, onSetScope = {},
                )
            },
            // La HOJA se quedo con lo que se GESTIONA. Se compone como modal, igual que en la app.
            Variant("Hoja · dueño, con codigo vivo") {
                SharedCarMembersSheet(
                    members = sharedCarGroup,
                    currentUid = "mock_user_001",
                    isOwner = true,
                    working = false,
                    onRemove = {}, onLeave = {}, onStopSharing = {}, onDismiss = {},
                    inviteCode = "K7QP3M",
                    shareRoutes = true,
                    showPairingHint = false,
                )
            },
            Variant("Hoja · pidiendo codigo nuevo (shimmer)") {
                // El hueco del codigo reserva su altura: codigo, shimmer y «sin codigo» miden lo
                // mismo, o la card se estira al cambiar de uno a otro.
                // [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]
                SharedCarMembersSheet(
                    members = sharedCarGroup,
                    currentUid = "mock_user_001",
                    isOwner = true,
                    working = true,
                    onRemove = {}, onLeave = {}, onStopSharing = {}, onDismiss = {},
                    inviteCode = "K7QP3M",
                    regenerating = true,
                    shareRoutes = true,
                    showPairingHint = false,
                )
            },
            Variant("Hoja · dueño recien compartido (aun sin codigo)") {
                SharedCarMembersSheet(
                    members = sharedCarGroup.take(1),
                    currentUid = "mock_user_001",
                    isOwner = true,
                    working = false,
                    onRemove = {}, onLeave = {}, onStopSharing = {}, onDismiss = {},
                    inviteCode = null,
                    shareRoutes = true,
                    showPairingHint = false,
                )
            },
            Variant("Hoja · miembro sin emparejar y sin enseñar rutas") {
                SharedCarMembersSheet(
                    members = sharedCarGroup,
                    currentUid = "mock_user_002",
                    isOwner = false,
                    working = false,
                    onRemove = {}, onLeave = {}, onStopSharing = {}, onDismiss = {},
                    inviteCode = null,
                    shareRoutes = false,
                    showPairingHint = true,
                )
            },
            Variant("Unirse · el dialogo del codigo") {
                JoinSharedCarDialogContent(
                    dialog = JoinSharedCarDialog(),
                    onCodeChanged = {}, onSubmit = {}, onDismiss = {}, onShareRoutesChanged = {},
                )
            },
            // [COPY-A-CARD-THAT-PROMISES-ROUTES-STAY-HOME-001] Entrar decide qué compartes: la
            // pregunta nace en NO y esta es la otra mitad, la que se contestó que sí.
            Variant("Unirse · compartiendo también las rutas", Placement.Surface) {
                JoinSharedCarDialogContent(
                    dialog = JoinSharedCarDialog(code = "AB3K9P", shareRoutes = true),
                    onCodeChanged = {}, onSubmit = {}, onDismiss = {}, onShareRoutesChanged = {},
                )
            },
            Variant("Unirse · codigo caducado") {
                JoinSharedCarDialogContent(
                    dialog = JoinSharedCarDialog(
                        code = "K7QP3M",
                        error = PaparcarError.SharedCar.InviteExpired,
                    ),
                    onCodeChanged = {}, onSubmit = {}, onDismiss = {}, onShareRoutesChanged = {},
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
            // [FUEL-A-PRICE-IS-QUOTED-WHERE-IT-IS-SOLD-001] Las mismas filas en un mercado que cobra
            // por galon: el precio guardado sigue siendo por litro y lo que cambia es el borde.
            // ⛔ Lo que hace galones a estas filas es la MONEDA del precio (USD), no un
            // `LocalPumpGallon` puesto a mano: la variante tiene que mentir exactamente igual que
            // miente produccion, o deja de servir para verlo. Hoy no hay fuente per-surtidor en
            // EE. UU., asi que este es el UNICO sitio donde se puede mirar esa conversion con los ojos.
            // La barra de filtros: los dos órdenes y los productos que la respuesta cotiza.
            // ⛔ Con `availableProducts` VACÍO tambien, que es el caso de un pais sin feed de
            // precios: la barra tiene que seguir dejando ordenar y no debe enseñar chips de
            // productos que nadie vende. [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001]
            // ⛔ La barra se pide APARTE del carril, porque en Home vive en la cabecera fijada
            // junto a los tabs. La galería la compone igual que la hoja, como ya hace la de
            // parkings. [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001]
            Variant("Filtros · orden y producto", Placement.Surface) {
                Column {
                    HomeFuelFilterBar(
                        sort = FuelLaneSort.CHEAPEST,
                        product = FuelType.DIESEL,
                        availableProducts = listOf(FuelType.DIESEL, FuelType.GASOLINE, FuelType.LPG_CNG),
                        onSortSelect = {},
                        onProductSelect = {},
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    HomeFuelLane(
                        slice = HomeFuelSlice(
                            stations = galleryFuelStations,
                            reportedAt = "09/09/2026 08:30:00",
                            sort = FuelLaneSort.CHEAPEST,
                            product = FuelType.DIESEL,
                            availableProducts = listOf(FuelType.DIESEL, FuelType.GASOLINE, FuelType.LPG_CNG),
                        ),
                    )
                }
            },
            Variant("Filtros · sin productos que ofrecer", Placement.Surface) {
                Column {
                    HomeFuelFilterBar(
                        sort = FuelLaneSort.NEAREST,
                        product = null,
                        availableProducts = emptyList(),
                        onSortSelect = {},
                        onProductSelect = {},
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    HomeFuelLane(
                        slice = HomeFuelSlice(stations = galleryFuelStations, availableProducts = emptyList()),
                    )
                }
            },
            Variant("Con precios · por galon (EE. UU.)", Placement.Surface) {
                HomeFuelLane(
                    slice = HomeFuelSlice(
                        stations = galleryFuelStationsUsd,
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
            // Italia: el MISMO surtidor cotiza self y servito, y medido se separan hasta 0,35 €/L.
            // La primera fila es self (no lleva marca, que es lo normal), la SEGUNDA es servito y
            // por eso la lleva, y la tercera existe para que las dos se vean juntas: sin la marca
            // sus dos cifras se leerían como la misma clase de precio.
            // [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
            Variant("Precio SELF vs SERVITO (Italia)", Placement.Surface) {
                HomeFuelLane(
                    slice = HomeFuelSlice(
                        stations = galleryItalianStations,
                        reportedAt = null,
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
                    // Italia: el modo entra en la MISMA línea de cualificación que ya dice de qué
                    // carburante es la cifra, porque contesta la otra mitad de la misma pregunta —
                    // no sólo para qué es este precio, sino CUÁL de los dos es.
                    // [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
                    FuelStationMarker(
                        priceText = "2.434",
                        fuelLabel = "Gasoline · Atendido",
                        selected = false,
                    )
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
            // Los filtros del carril: los tres, y el vacío que distingue «no hay» de «ninguno
            // cumple lo que pediste». [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001]
            // ⛔ La barra se pide APARTE del carril, porque en Home vive en la cabecera fijada junto
            // a los tabs y no dentro de la lista. La galería la compone igual que la hoja: barra
            // encima, carril debajo. [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001]
            Variant("Carril con filtros", Placement.Surface) {
                Column {
                    HomeParkingFilterBar(
                        selected = PublicParkingFilter.COVERED,
                        onSelect = {},
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    HomeParkingsLane(
                        slice = HomeParkingsSlice(
                            parkings = galleryParkings,
                            hasAnyBeforeFilter = true,
                            filter = PublicParkingFilter.COVERED,
                        ),
                    )
                }
            },
            // El cuarto chip: DESCARTA los de gálibo bajo, no promete que quepas. Un parking sin
            // altura declarada —casi todos— se queda en la lista.
            // [VEH-A-HEIGHT-UNKNOWN-IS-NOT-A-FIT-001]
            Variant("Carril · sin gálibo bajo", Placement.Surface) {
                Column {
                    HomeParkingFilterBar(
                        selected = PublicParkingFilter.NO_LOW_CLEARANCE,
                        onSelect = {},
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    HomeParkingsLane(
                        slice = HomeParkingsSlice(
                            parkings = galleryParkings,
                            hasAnyBeforeFilter = true,
                            filter = PublicParkingFilter.NO_LOW_CLEARANCE,
                        ),
                    )
                }
            },
            Variant("Carril · el filtro no deja ninguno", Placement.Surface) {
                Column {
                    HomeParkingFilterBar(
                        selected = PublicParkingFilter.FREE,
                        onSelect = {},
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    HomeParkingsLane(
                        slice = HomeParkingsSlice(
                            parkings = emptyList(),
                            hasAnyBeforeFilter = true,
                            filter = PublicParkingFilter.FREE,
                        ),
                    )
                }
            },
            Variant("Peek de parking (sin nombre, sin datos)", Placement.Surface) {
                peek(
                    HomeState(
                        parkings = HomeParkingsSlice(parkings = galleryParkings),
                        selection = HomeSelection.PublicParking("osm/way/908575097"),
                        userGpsPoint = sampleGps,
                    ),
                )
            },
            // La capsula del parking: glifo + tarifa, la misma gramatica que la gasolinera.
            // ⛔ Las TRES tarifas, y la desconocida es la que importa: es el 71% de los parkings y
            // la unica que no puede inventarse una palabra. Sin cifra, porque la unica que ofrece
            // OSM es el aforo TOTAL y esa ranura es donde ira el numero de plazas LIBRES en F6b.
            // [MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001]
            Variant("Marcadores de la capa (las tres tarifas)", Placement.Surface) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PublicParkingMarker(selected = false, feeLabel = null)
                    PublicParkingMarker(selected = false, feeLabel = "Gratuito")
                    PublicParkingMarker(selected = false, feeLabel = "De pago")
                    PublicParkingMarker(selected = true, feeLabel = "De pago")
                }
            },
            // Uno al lado del otro: la misma forma, dos acentos y dos frases. Si algun dia se
            // separan, se ve aqui. [MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001]
            Variant("Parking y gasolinera, la misma capsula", Placement.Surface) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    PublicParkingMarker(selected = false, feeLabel = "De pago")
                    FuelStationMarker(priceText = "1.879", fuelLabel = "Gasolina", selected = false)
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
 * Las mismas gasolineras cotizadas en DOLARES — lo que convierte la fila a galones.
 * [FUEL-A-PRICE-IS-QUOTED-WHERE-IT-IS-SOLD-001]
 *
 * El importe no se toca: el dominio guarda euros-por-litro y dolares-por-litro igual, y el borde de
 * salida es el que multiplica por 3,785 al escribirlo. Cambiar aqui el numero ademas de la moneda
 * escondería justo la conversion que esta variante existe para enseñar.
 */
private val galleryFuelStationsUsd: List<NearbyFuelStation> = galleryFuelStations.map { nearby ->
    nearby.copy(
        station = nearby.station.copy(
            prices = nearby.station.prices.mapValues { (_, p) -> p.copy(perUnit = p.perUnit.copy(currency = "USD")) },
        ),
        price = nearby.price?.let { p -> p.copy(perUnit = p.perUnit.copy(currency = "USD")) },
    )
}

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

/**
 * Italia: el feed sella la ESTACIÓN (no la respuesta ni el precio) y, sobre todo, dice QUIÉN sirve.
 * Los números son los medidos en Roma el 15-09: 2,084 self contra 2,434 servito en la misma
 * estación son los 0,35 €/L que obligan a marcar la cifra.
 * [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
 */
private val galleryItalianStations: List<NearbyFuelStation> = listOf(
    italianStation("it1", "Api-Ip", 2.084, 520.0, isSelf = true),
    italianStation("it2", "Api-Ip", 2.149, 1_320.0, isSelf = false),
    // ⛔ La misma estación que la primera pero servida: 0,35 € más cara, y sin la marca las dos
    // cifras se ordenarían como si compararan lo mismo.
    italianStation("it3", "AgipEni", 2.434, 2_600.0, isSelf = false),
)

private fun italianStation(
    id: String,
    name: String,
    price: Double,
    distanceMeters: Double,
    isSelf: Boolean,
): NearbyFuelStation {
    // La hora de la ESTACIÓN, que es la única que este feed da — p50 medido 16,8 h.
    val reportedAtMs = Clock.System.now().toEpochMilliseconds() - SIXTEEN_HOURS_MS
    val stationPrice = StationPrice(Money(price, "EUR"), reportedAtMs, isSelf)
    return NearbyFuelStation(
        station = FuelStation(
            // ⛔ Sin dirección: el zone search no trae ninguna, en 229 de 229.
            id = id, name = name, address = null, town = null,
            latitude = 41.9028, longitude = 12.4964,
            prices = mapOf(FuelType.GASOLINE to stationPrice),
        ),
        distanceMeters = distanceMeters,
        stationKey = "italy|$id",
        source = "italy",
        price = stationPrice,
    )
}

private const val SIXTEEN_HOURS_MS = 16 * 60 * 60 * 1000L
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
    displayName = "Family car",
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
    address = AddressInfo(street = "Leeson Street, 14", city = "Madrid", region = null, country = "España"),
    isParked = parked,
    updatedAtMs = 1_757_900_000_000L,
)

private fun sharedMockVehicle(shareRoutes: Boolean = true, paired: Boolean = true) = Vehicle(
    id = "mock_vehicle_001",
    userId = "mock_user_002",
    name = "Family car",
    sizeCategory = VehicleSize.MEDIUM_SUV,
    bluetoothDeviceId = if (paired) "AA:BB:CC:DD:EE:FF" else null,
    sharedCarId = "mock_vehicle_001",
    ownerUid = "mock_user_001",
    shareRoutesInGroup = shareRoutes,
)
