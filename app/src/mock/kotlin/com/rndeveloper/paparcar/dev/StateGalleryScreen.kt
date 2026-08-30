package com.rndeveloper.paparcar.dev

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.rndeveloper.paparcar.domain.model.UserProfile
import com.rndeveloper.paparcar.domain.model.VehicleColor
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
import com.rndeveloper.paparcar.presentation.home.sections.header.components.HomeSearchBar
import com.rndeveloper.paparcar.ui.components.ConfirmationBottomSheet
import com.rndeveloper.paparcar.presentation.home.model.DetectionStory
import com.rndeveloper.paparcar.presentation.home.model.ParkedWatchBadge
import com.rndeveloper.paparcar.presentation.home.toBrowseListSlice
import com.rndeveloper.paparcar.presentation.home.toPeekSlice
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomeDetectionSurface
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomePeekHandle
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.SpotFitRow
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.homeSheetItems
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.presentation.map.HistoryDetailSheet
import com.rndeveloper.paparcar.presentation.onboarding.OnboardingScreen
import com.rndeveloper.paparcar.presentation.permissions.PermissionsContent
import com.rndeveloper.paparcar.presentation.permissions.PermissionsState
import com.rndeveloper.paparcar.presentation.preview.FakeData
import com.rndeveloper.paparcar.domain.model.DetectionTier
import com.rndeveloper.paparcar.domain.permissions.RequiredPermission
import com.rndeveloper.paparcar.presentation.settings.SettingsContent
import com.rndeveloper.paparcar.presentation.settings.SettingsState
import com.rndeveloper.paparcar.presentation.vehicleregistration.VehicleRegistrationContent
import com.rndeveloper.paparcar.presentation.vehicleregistration.VehicleRegistrationState
import com.rndeveloper.paparcar.presentation.vehicles.HistoryContent
import com.rndeveloper.paparcar.presentation.vehicles.HistoryFilter
import com.rndeveloper.paparcar.presentation.vehicles.HistoryState
import com.rndeveloper.paparcar.presentation.vehicles.VehicleHistoryCalculator
import com.rndeveloper.paparcar.presentation.vehicles.VehiclesContent
import com.rndeveloper.paparcar.presentation.vehicles.VehiclesState
import com.apptolast.customlogin.presentation.screens.components.DefaultAuthContainer
import com.apptolast.customlogin.presentation.slots.defaultslots.DefaultDivider
import com.rndeveloper.paparcar.di.paparcarLoginConfig
import com.rndeveloper.paparcar.di.paparcarSocialProviders
import com.rndeveloper.paparcar.domain.model.ZoneIcon
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.presentation.util.zoneIconFor
import com.rndeveloper.paparcar.ui.auth.paparcarAuthSlots
import com.rndeveloper.paparcar.ui.components.ConnectivityBanner
import com.rndeveloper.paparcar.ui.components.FreeSpotClusterMarker
import com.rndeveloper.paparcar.ui.components.FreeSpotMarker
import com.rndeveloper.paparcar.ui.components.LicensePlateMarker
import com.rndeveloper.paparcar.ui.components.MyVehicleMarker
import com.rndeveloper.paparcar.ui.components.ParkingCenterPin
import com.rndeveloper.paparcar.ui.components.ReportCenterPin
import com.rndeveloper.paparcar.ui.components.ZoneCenterPin
import com.rndeveloper.paparcar.ui.components.ZoneMarker
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.domain.detection.DetectionPath

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

@Composable
private fun history(state: HistoryState, watch: VehicleWatch = VehicleWatch.Assisted) {
    HistoryContent(
        state = state,
        contentPadding = PaddingValues(0.dp),
        onViewOnMap = { _, _, _ -> },
        watch = watch,
        onFilterSelected = {},
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
) {
    Box(Modifier.fillMaxSize()) {
        HistoryDetailSheet(
            session = session,
            vehicle = vehicle,
            hasOlder = hasOlder,
            hasNewer = hasNewer,
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
) {
    val slots = paparcarAuthSlots().login
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
        MarkerSectionLabel("MyVehicle — normal · seleccionado")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MyVehicleMarker()
            MyVehicleMarker(selected = true)
        }
        MarkerSectionLabel("FreeSpot — HIGH · MEDIUM · LOW · MANUAL · seleccionado")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            FreeSpotMarker(reliability = SpotFreshness.FRESH)
            FreeSpotMarker(reliability = SpotFreshness.RECENT)
            FreeSpotMarker(reliability = SpotFreshness.STALE)
            FreeSpotMarker(isManual = true)
            FreeSpotMarker(reliability = SpotFreshness.FRESH, selected = true)
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
            ZoneMarker(name = "Casa", icon = zoneIconFor(ZoneIcon.HOME))
            ZoneMarker(name = "Trabajo", icon = zoneIconFor(ZoneIcon.WORK), isPrivate = true)
            ZoneMarker(name = "Gimnasio del barrio", icon = zoneIconFor(ZoneIcon.GYM))
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
        MarkerSectionLabel("Pin central · Zona (reposo · elevado)")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CenterPinSlot { ZoneCenterPin(icon = Icons.Rounded.LocalParking, cameraMoving = false) }
            CenterPinSlot { ZoneCenterPin(icon = Icons.Rounded.LocalParking, cameraMoving = true) }
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
                detectionSurface(DetectionStory.Watching("Škoda Kamiq", isParked = true, viaBluetooth = false))
            },
            // [DET-WATCH-HONEST-001] Honesto: frágil (avisa) e interrumpido (el OS mató la FGS).
            Variant("Vigilando FRÁGIL (sin exención batería)", Placement.Surface) {
                detectionSurface(
                    DetectionStory.Watching(
                        "Škoda Kamiq", isParked = true, viaBluetooth = false,
                        watchBadge = ParkedWatchBadge.WATCHING_FRAGILE,
                    ),
                )
            },
            // [DET-WATCH-REACTIVATE-001] Su CTA reactiva el vigilante (no pide batería): en el build
            // mock el fake pasa la presencia a Sentry, así que la fila se cura sola al tocarla.
            Variant("Vigilancia en PAUSA (el sistema paró el vigilante)", Placement.Surface) {
                detectionSurface(
                    DetectionStory.Watching(
                        "Škoda Kamiq", isParked = true, viaBluetooth = false,
                        watchBadge = ParkedWatchBadge.WATCH_INTERRUPTED,
                    ),
                )
            },
            Variant("Vigilando (BT armado, sin sesión)", Placement.Surface) {
                detectionSurface(DetectionStory.Watching("Škoda Kamiq", isParked = false, viaBluetooth = true))
            },
            Variant("Conduciendo (detección activa → verde)", Placement.Surface) {
                detectionSurface(DetectionStory.Driving("Škoda Kamiq", isCandidate = false))
            },
            // [UI-COLOR-DOCTRINE-001] La fila viste el color del MÉTODO del coche: BT → azul.
            Variant("Conduciendo (BT → azul)", Placement.Surface) {
                detectionSurface(DetectionStory.Driving("Škoda Kamiq", isCandidate = false, viaBluetooth = true))
            },
            Variant("Aparcando… (candidate)", Placement.Surface) {
                detectionSurface(DetectionStory.Driving("Škoda Kamiq", isCandidate = true))
            },
            // [DET-NUDGE-PERSIST-001] Nudge pendiente "¿dónde has dejado el coche?" — la fila
            // sustituye a la del estado normal hasta que el usuario marca plaza o descarta.
            Variant("Nudge pendiente — ¿dónde has dejado el coche?", Placement.Surface) {
                detectionSurface(DetectionStory.PendingAsk)
            },
            // [DET-ASK-STATE-001] La pregunta viva: manda sobre todo salvo el bloqueo de ubicación,
            // y sus dos botones son los mismos comandos que los de la notificación.
            Variant("¿Has aparcado? — con coche (AwaitingAnswer)", Placement.Surface) {
                detectionSurface(DetectionStory.AwaitingAnswer("Škoda Kamiq"))
            },
            Variant("¿Has aparcado? — sin nombre de coche", Placement.Surface) {
                detectionSurface(DetectionStory.AwaitingAnswer(null))
            },
        ),
    ),
    ScreenGroup(
        "Home · búsqueda",
        listOf(
            Variant("Con resultados") { searchBar(results = sampleSearchResults) },
            // [UX-PARK-FLOW-001 H3] Éxito con 0 resultados ≠ fallo del geocoder: fila explícita.
            Variant("Sin resultados (fila explícita)") { searchBar(showNoResults = true) },
            Variant("Buscando (spinner)") { searchBar(isSearching = true) },
        ),
    ),
    ScreenGroup(
        "Detección · confirmación",
        listOf(
            // [UX-PARK-FLOW-001 C5+C4] Auto-confirm con cuenta atrás visible (arranca en 4:00)
            // y la MISMA voz que la notificación: pregunta con coche + "Sí, he aparcado".
            Variant("Sheet ¿Has aparcado? + cuenta atrás") {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
                ConfirmationBottomSheet(
                    onConfirm = {},
                    onDismiss = {},
                    addressLine = "Calle de Alcalá, 12",
                    vehicleName = "Škoda Kamiq",
                )
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
                        detectionPath = "vehicle-exit",
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
        "Historial",
        listOf(
            // El raíl, el punto pulsante y el wash de la sesión viva llevan la identidad del coche
            // de esa página del pager — no un verde fijo. [UI-HISTORY-IDENTITY-AND-SOURCE-001]
            Variant("Lista · coche asistido (verde)") {
                history(HistoryState(
                    sessions = FakeData.allSessions,
                    filteredSessions = FakeData.allSessions,
                    statsData = VehicleHistoryCalculator.computeStats(FakeData.allSessions),
                ))
            },
            Variant("Lista · coche Bluetooth (azul)") {
                history(
                    HistoryState(
                    sessions = FakeData.allSessions,
                    filteredSessions = FakeData.allSessions,
                    statsData = VehicleHistoryCalculator.computeStats(FakeData.allSessions),
                ),
                    watch = VehicleWatch.Bluetooth,
                )
            },
            Variant("Lista · coche sin vigilancia (gris)") {
                history(
                    HistoryState(
                    sessions = FakeData.allSessions,
                    filteredSessions = FakeData.allSessions,
                    statsData = VehicleHistoryCalculator.computeStats(FakeData.allSessions),
                ),
                    watch = VehicleWatch.Off,
                )
            },
            Variant("Filtro: esta semana") {
                history(
                    HistoryState(
                        sessions = FakeData.allSessions,
                        filteredSessions = FakeData.allSessions,
                        activeFilter = HistoryFilter.ThisWeek,
                        statsData = VehicleHistoryCalculator.computeStats(FakeData.allSessions),
                    ),
                )
            },
            Variant("Vacío") { history(HistoryState()) },
            Variant("Cargando") { history(HistoryState(isLoading = true)) },
        ),
    ),
    ScreenGroup(
        "Settings",
        listOf(
            Variant("Con perfil") { SettingsContent(state = SettingsState(userProfile = sampleProfile)) },
            Variant("Sin perfil") { SettingsContent(state = SettingsState(userProfile = null)) },
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
            Variant("Permisos incompletos") {
                SettingsContent(
                    state = SettingsState(
                        userProfile = sampleProfile,
                        // Amber health row + "Fix"
                        missingDetectionPermissions = setOf(RequiredPermission.BACKGROUND_LOCATION),
                        isLocationServicesEnabled = true,
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
            Variant("Diálogo enviar diagnóstico") {
                SettingsContent(state = SettingsState(userProfile = sampleProfile, showSendDiagnosticsConfirmation = true))
            },
            Variant("Enviando diagnóstico") {
                SettingsContent(
                    state = SettingsState(
                        userProfile = sampleProfile,
                        showSendDiagnosticsConfirmation = true,
                        isSendingDiagnostics = true,
                    ),
                )
            },
        ),
    ),
    ScreenGroup(
        "Vehicles",
        listOf(
            // Full history per vehicle so the activity chart + filter bar + timeline actually render
            // (the hero card alone doesn't exercise the History section). [VEHICLES-REDESIGN-001]
            Variant("Lista") {
                val history = FakeData.vehiclesWithStats.associate { vws ->
                    vws.vehicle.id to HistoryState(
                        sessions = FakeData.allSessions,
                        activeFilter = HistoryFilter.All,
                        filteredSessions = FakeData.allSessions,
                        // Same aggregation the ViewModel runs — the gallery must exercise the
                        // plazas-cedidas cell and the facts footer. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
                        statsData = VehicleHistoryCalculator.computeStats(FakeData.allSessions),
                    )
                }
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
                val oneSession = FakeData.endedSessions.take(1)
                val history = FakeData.vehiclesWithStats.associate { vws ->
                    vws.vehicle.id to HistoryState(
                        sessions = oneSession,
                        activeFilter = HistoryFilter.All,
                        filteredSessions = oneSession,
                    )
                }
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
                val history = FakeData.vehiclesWithStats.associate { vws ->
                    vws.vehicle.id to HistoryState(
                        sessions = FakeData.allSessions,
                        activeFilter = HistoryFilter.All,
                        filteredSessions = FakeData.allSessions,
                        // Same aggregation the ViewModel runs — the gallery must exercise the
                        // plazas-cedidas cell and the facts footer. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
                        statsData = VehicleHistoryCalculator.computeStats(FakeData.allSessions),
                    )
                }
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
                val history = FakeData.vehiclesWithStats.associate { vws ->
                    vws.vehicle.id to HistoryState(
                        sessions = FakeData.allSessions,
                        activeFilter = HistoryFilter.All,
                        filteredSessions = FakeData.allSessions,
                        // Same aggregation the ViewModel runs — the gallery must exercise the
                        // plazas-cedidas cell and the facts footer. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
                        statsData = VehicleHistoryCalculator.computeStats(FakeData.allSessions),
                    )
                }
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
        ),
    ),
    ScreenGroup(
        "Permisos",
        listOf(
            Variant("Todo denegado") { PermissionsContent(state = PermissionsState(), onRequestPermissions = {}) },
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
                )
            },
            Variant("Prompt de ajustes") {
                PermissionsContent(state = PermissionsState(showSettingsPrompt = true), onRequestPermissions = {})
            },
            // [DET-TOGGLE-002] Diálogo educativo "Maybe later" — core+GPS concedidos, producer pendiente.
            Variant("Diálogo saltar detección") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        isLocationServicesEnabled = true,
                        hasBackgroundLocation = false,
                        showSkipDetectionDialog = true,
                    ),
                    onRequestPermissions = {},
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
        ),
    ),
    ScreenGroup(
        "Mapa · marcadores",
        listOf(
            Variant("Showcase completo (sin tiles)") { markersShowcase() },
        ),
    ),
)

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
