package com.rndeveloper.paparcar.presentation.vehicles
import paparcar.composeapp.generated.resources.shared_car_routes_question_title
import paparcar.composeapp.generated.resources.shared_car_routes_question_subtitle
import com.rndeveloper.paparcar.ui.components.PapSwitchRow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleColor
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.ui.components.PapCollapsingTopBarScaffold
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.presentation.permissions.PermissionsFocus
import com.rndeveloper.paparcar.presentation.vehicles.components.PapVehicleSelectorPill
import com.rndeveloper.paparcar.presentation.vehicles.components.VEHICLE_PILL_HEIGHT_DP
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
import com.rndeveloper.paparcar.ui.components.PapDialogAccent
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.ui.components.chips.PaparcarAddChip
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_unknown
import com.rndeveloper.paparcar.presentation.vehicles.components.JoinSharedCarDialogContent
import com.rndeveloper.paparcar.presentation.vehicles.components.SharedCarMembersSheet
import paparcar.composeapp.generated.resources.my_car_add_vehicle
import paparcar.composeapp.generated.resources.my_car_delete_cancel
import paparcar.composeapp.generated.resources.shared_car_dissolved_body
import paparcar.composeapp.generated.resources.shared_car_dissolved_body_free_slot
import paparcar.composeapp.generated.resources.shared_car_dissolved_delete
import paparcar.composeapp.generated.resources.shared_car_dissolved_delete_with_history
import paparcar.composeapp.generated.resources.shared_car_dissolved_keep
import paparcar.composeapp.generated.resources.shared_car_dissolved_title
import paparcar.composeapp.generated.resources.shared_car_not_shared_body
import paparcar.composeapp.generated.resources.shared_car_share_cta
import paparcar.composeapp.generated.resources.shared_car_title
import paparcar.composeapp.generated.resources.vehicles_join_shared_car
import paparcar.composeapp.generated.resources.my_car_empty_subtitle
import paparcar.composeapp.generated.resources.my_car_empty_why_link
import paparcar.composeapp.generated.resources.my_car_no_vehicle
import paparcar.composeapp.generated.resources.my_car_title
import paparcar.composeapp.generated.resources.my_car_unnamed_vehicle
import paparcar.composeapp.generated.resources.vehicle_set_active_confirm_body
import paparcar.composeapp.generated.resources.vehicle_set_active_confirm_cancel
import paparcar.composeapp.generated.resources.vehicle_set_active_confirm_cta
import paparcar.composeapp.generated.resources.vehicle_set_active_confirm_title
import paparcar.composeapp.generated.resources.vehicle_status_active_cd
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PapColor

/**
 * VehiclesScreen (v1 redesign) — Vehicles + History fusionado.
 *
 *  - Cabecera colapsable compartida (`PapCollapsingTopBarScaffold`): el título se retira al
 *    scrollear y las pestañas quedan ancladas bajo la status bar. [UI-TOPBAR-COLLAPSE-001]
 *  - Tabs rediseñadas: pills custom con icono + nombre + dot si activo.
 *  - "+" trailing chip para añadir vehículo además del icon action en top bar.
 *  - Empty state con icono circular 120dp + display title + CTA grande.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(
    onAddVehicle: () -> Unit = {},
    onEditVehicle: (vehicleId: String) -> Unit = {},
    onNavigateToMap: (lat: Double, lon: Double, sessionId: String) -> Unit = { _, _, _ -> },
    /** Volver a Home sobre un aparcamiento que sigue vivo. [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001] */
    onNavigateToLiveParking: (sessionId: String) -> Unit = {},
    onShowExplainer: () -> Unit = {},
    onOpenPaywall: (PaywallReason) -> Unit = {},
    /** Plus opening the full figures panel on a given car. [STATS-PANEL-001] */
    onOpenStats: (vehicleId: String) -> Unit = {},
    /** The focused permissions flow — the same one Home's banner and Settings' switch open.
     *  [DET-TOGGLE-001] */
    onNavigateToPermissions: (PermissionsFocus) -> Unit = {},
    /** Which car to open on, when the screen was entered FROM that car ("Ver vehículo" in Home's
     *  parking modal). Null on a plain tab tap, which keeps whatever was selected.
     *  [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001] */
    focusVehicleId: String? = null,
    viewModel: VehiclesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorFallback = stringResource(Res.string.error_unknown)

    LaunchedEffect(focusVehicleId) {
        focusVehicleId?.let { viewModel.handleIntent(VehiclesIntent.FocusVehicle(it)) }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is VehiclesEffect.NavigateToAddVehicle -> onAddVehicle()
                is VehiclesEffect.NavigateToEditVehicle -> onEditVehicle(effect.vehicleId)
                is VehiclesEffect.NavigateToMap -> onNavigateToMap(effect.lat, effect.lon, effect.sessionId)
                is VehiclesEffect.NavigateToLiveParking -> onNavigateToLiveParking(effect.sessionId)
                is VehiclesEffect.NavigateToPaywall -> onOpenPaywall(effect.reason)
                is VehiclesEffect.NavigateToStats -> onOpenStats(effect.vehicleId)
                is VehiclesEffect.NavigateToPermissions -> onNavigateToPermissions(effect.focus)
                is VehiclesEffect.ShowError -> snackbarHostState.showSnackbar(errorFallback)
            }
        }
    }

    VehiclesContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::handleIntent,
        onShowExplainer = onShowExplainer,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesContent(
    state: VehiclesState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onIntent: (VehiclesIntent) -> Unit = {},
    onShowExplainer: () -> Unit = {},
) {
    val vehicles = state.vehicles
    // El pager se iza aquí porque las pestañas viven en la cabecera (se retiran con el título) y
    // necesitan la misma página que el pager del cuerpo. Cambiar de coche sigue siendo un swipe
    // horizontal en cualquier punto de la página, así que la cabecera no tiene que sobrevivir.
    val pagerState = rememberPagerState(
        initialPage = state.selectedVehicleIndex,
        pageCount = { vehicles.size },
    )
    val scope = rememberCoroutineScope()
    // Volver arriba de un salto no pasa por el nested scroll: hay que decirle a la cabecera que se
    // despliegue o quedaria retirada sobre una lista ya en su inicio. [UI-SCROLL-TO-TOP-001]
    var expandHeader by remember { mutableIntStateOf(0) }

    PapCollapsingTopBarScaffold(
        title = stringResource(Res.string.my_car_title),
        // Match Home's bottom-sheet tone so the page doesn't feel near-black.
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // La página nueva empieza arriba: la cabecera vuelve a desplegarse para que no quede un
        // hueco donde estaba el título. [UI-TOPBAR-COLLAPSE-001]
        expandKey = pagerState.settledPage to expandHeader,
        subHeader = if (vehicles.isEmpty()) {
            null
        } else {
            {
                VehicleTabRow(
                    vehicles = vehicles.map { it.vehicle },
                    selectedIndex = pagerState.currentPage,
                    onTabClick = { index ->
                        onIntent(VehiclesIntent.SelectVehicle(index))
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    onAddVehicle = { onIntent(VehiclesIntent.AddVehicle) },
                )
            }
        },
    ) { headerPadding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(headerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

            vehicles.isEmpty() -> EmptyVehicleState(
                modifier = Modifier.fillMaxSize().padding(headerPadding),
                onAddVehicle = { onIntent(VehiclesIntent.AddVehicle) },
                onShowExplainer = onShowExplainer,
                onJoinSharedCar = { onIntent(VehiclesIntent.ShowJoinDialog(true)) },
            )

            else -> VehiclesPager(
                state = state,
                pagerState = pagerState,
                contentPadding = headerPadding,
                onScrolledToTop = { expandHeader++ },
                onIntent = onIntent,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Multi-vehicle pager with custom pill tab row
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehiclesPager(
    state: VehiclesState,
    pagerState: PagerState,
    contentPadding: PaddingValues,
    onScrolledToTop: () -> Unit,
    onIntent: (VehiclesIntent) -> Unit,
) {
    val vehicles = state.vehicles
    // The vehicle whose set-active is awaiting confirmation. Non-null shows the consequence dialog;
    // making a car active is a declaration ("I drive this"), never a silent switch. [VEH-ACTIVE-FENCE-001]
    var pendingSetActive by remember { mutableStateOf<Vehicle?>(null) }

    // Scroll pager when ViewModel changes the selected index (e.g. restore on back-nav).
    // Skip when the pager is already scrolling — the in-progress animateScrollToPage
    // (started by a tab click) would be cancelled and replaced with an instant jump.
    LaunchedEffect(state.selectedVehicleIndex) {
        if (pagerState.settledPage != state.selectedVehicleIndex && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(state.selectedVehicleIndex)
        }
    }

    // Sync swipe gestures back to ViewModel so the selection survives navigation.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> onIntent(VehiclesIntent.SelectVehicle(page)) }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { index -> vehicles.getOrElse(index) { vehicles.last() }.vehicle.id },
    ) { page ->
        // Defensive guard: during the recomposition where vehicles
        // shrinks and the clamp above hasn't run yet, `page` may
        // briefly point at an out-of-bounds index. Use the clamped
        // page to read the slot so we never crash with IOOBE.
        val safePage = page.coerceAtMost(vehicles.lastIndex)
        val vehicleWithStats = vehicles[safePage]
        // Each page reads its own vehicle's history from the cache directly.
        // Using the shared state.historyState (derived from selectedVehicleIndex)
        // would show the SELECTED vehicle's history on ALL visible pages during a
        // pager slide animation, making the incoming page appear to have the wrong content.
        // A page whose vehicle is not in the cache yet is still WAITING, not empty.
        // [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001]
        val pageHistoryState = state.historyCache[vehicleWithStats.vehicle.id]
            ?: HistoryState(timeline = HistoryTimeline.Unresolved)
        // Carousel polish: the off-centre page eases out (alpha + scale) as you
        // swipe, so the incoming vehicle "settles" into focus instead of a flat slide.
        val pageOffset =
            ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                .absoluteValue.coerceIn(0f, 1f)
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = PAGER_MIN_ALPHA + (1f - pageOffset) * (1f - PAGER_MIN_ALPHA)
                val scale = PAGER_MIN_SCALE + (1f - pageOffset) * (1f - PAGER_MIN_SCALE)
                scaleX = scale
                scaleY = scale
            },
        ) {
            val pageShared = state.sharedCars[vehicleWithStats.vehicle.id]
            // La página que se está MIRANDO es la única que tiene miembros cargados: el pager
            // enseña una y suscribirse a las cinco abriría cinco listeners para pintar una.
            val isOnScreen = vehicleWithStats.vehicle.id == state.currentVehicleId
            VehiclePageContent(
                vehicleWithStats = vehicleWithStats,
                historyState = pageHistoryState,
                isSettingActive = state.settingActiveVehicleId == vehicleWithStats.vehicle.id,
                isPlus = state.isPlus,
                detectionPause = state.detectionPause,
                onRequestSetActive = { pendingSetActive = vehicleWithStats.vehicle },
                contentPadding = contentPadding,
                onScrolledToTop = onScrolledToTop,
                onIntent = onIntent,
                sharedCar = pageShared,
                members = if (isOnScreen) state.sharedMembers else emptyList(),
                currentUid = state.currentUid,
                isOwnerOfShared = pageShared?.ownerUid == state.currentUid && state.currentUid != null,
                sharedHistoryMineOnly = state.sharedHistoryMineOnly,
                sharedWorking = state.sharedWorking,
                sharedNote = if (pageShared != null && !state.sharedHistoryMineOnly) {
                    { session ->
                        SharedRowNote(
                            // Tu propia fila no se firma: en una lista tuya y de otros, el nombre
                            // que hace falta es el del OTRO.
                            authorName = session.userId
                                .takeIf { it != state.currentUid }
                                ?.let { state.sharedAuthorByUid[it] },
                            routeWithheld = session.id in state.sharedWithheldRouteIds,
                        )
                    }
                } else null,
            )
        }
    }

    state.membersSheetCarId?.let { carId ->
        SharedCarMembersSheet(
            members = state.sharedMembers,
            currentUid = state.currentUid,
            isOwner = state.isOwnerOfCurrent,
            working = state.sharedWorking,
            onRemove = { uid -> onIntent(VehiclesIntent.RemoveMember(carId, uid)) },
            onLeave = { onIntent(VehiclesIntent.LeaveSharedCar(carId)) },
            onStopSharing = { onIntent(VehiclesIntent.StopSharing(carId)) },
            onDismiss = { onIntent(VehiclesIntent.ShowMembers(null)) },
        )
    }

    state.joinDialog?.let { dialog ->
        JoinSharedCarDialogContent(
            dialog = dialog,
            onCodeChanged = { onIntent(VehiclesIntent.JoinCodeChanged(it)) },
            onSubmit = { onIntent(VehiclesIntent.SubmitJoinCode) },
            onDismiss = { onIntent(VehiclesIntent.ShowJoinDialog(false)) },
            onShareRoutesChanged = { onIntent(VehiclesIntent.SetJoinShareRoutes(it)) },
        )
    }

    // ⛔ Compartir se CONFIRMA: abre el coche a otras personas y dice, antes de hacerlo, qué van a
    // ver. Un interruptor silencioso para eso sería la clase de sorpresa que no se puede deshacer
    // del todo — los demás ya habrían visto dónde quedó el coche. [VEH-SHARED-001]
    state.confirmShareCarId?.let { carId ->
        PapAlertDialog(
            onDismiss = { onIntent(VehiclesIntent.ConfirmShareCar(null)) },
            icon = Icons.Rounded.Group,
            title = stringResource(Res.string.shared_car_title),
            body = stringResource(Res.string.shared_car_not_shared_body),
            primaryLabel = stringResource(Res.string.shared_car_share_cta),
            onPrimary = { onIntent(VehiclesIntent.ShareCar(carId)) },
            cancelLabel = stringResource(Res.string.my_car_delete_cancel),
            content = {
                // La pregunta, no un default: lo que el grupo verá de tus rutas se decide AQUÍ.
                // [COPY-A-CARD-THAT-PROMISES-ROUTES-STAY-HOME-001]
                PapSwitchRow(
                    label = stringResource(Res.string.shared_car_routes_question_title),
                    description = stringResource(Res.string.shared_car_routes_question_subtitle),
                    checked = state.confirmShareRoutes,
                    onCheckedChange = { onIntent(VehiclesIntent.SetConfirmShareRoutes(it)) },
                )
            },
        )
    }

    // ⛔ El grupo lo cerró OTRO, así que esto no es un aviso: es la decisión que le queda. La fila
    // deja de ser prestada y pasa a ocupar su plaza de coche sin que él haya tocado nada, y lo que
    // se borraría con ella son SUS viajes. [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001]
    state.dissolvedCar?.let { prompt ->
        PapAlertDialog(
            onDismiss = { onIntent(VehiclesIntent.KeepDissolvedCar) },
            icon = Icons.Rounded.Group,
            title = stringResource(Res.string.shared_car_dissolved_title, prompt.carName),
            body = if (prompt.takesTheFreeSlot) {
                stringResource(Res.string.shared_car_dissolved_body_free_slot)
            } else {
                stringResource(Res.string.shared_car_dissolved_body)
            },
            // Conservar es el PRIMARIO y descartar el diálogo hace lo mismo: la salida barata
            // tiene que ser la que no borra nada de nadie.
            primaryLabel = stringResource(Res.string.shared_car_dissolved_keep),
            onPrimary = { onIntent(VehiclesIntent.KeepDissolvedCar) },
            secondaryLabel = if (prompt.endedParkings > 0) {
                pluralStringResource(
                    Res.plurals.shared_car_dissolved_delete_with_history,
                    prompt.endedParkings,
                    prompt.endedParkings,
                )
            } else {
                stringResource(Res.string.shared_car_dissolved_delete)
            },
            onSecondary = { onIntent(VehiclesIntent.DeleteDissolvedCar) },
            secondaryAccent = PapDialogAccent.Destructive,
            secondaryLeadingIcon = Icons.Rounded.Delete,
        )
    }

    pendingSetActive?.let { veh ->
        SetActiveConfirmDialog(
            vehicleName = veh.displayName(fallback = stringResource(Res.string.my_car_unnamed_vehicle)),
            carbody = veh.carbodyType,
            size = veh.sizeCategory,
            color = veh.color,
            onConfirm = {
                onIntent(VehiclesIntent.SetActiveVehicle(veh.id))
                pendingSetActive = null
            },
            onDismiss = { pendingSetActive = null },
        )
    }
}

/**
 * Consequence confirmation before a vehicle becomes the active one: the active vehicle IS the
 * user's declaration of what they drive, so we spell out what activating means and never switch
 * silently. [VEH-ACTIVE-FENCE-001]
 */
@Composable
private fun SetActiveConfirmDialog(
    vehicleName: String,
    carbody: CarbodyType?,
    size: VehicleSize?,
    color: VehicleColor?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PapAlertDialog(
        onDismiss = onDismiss,
        // The hero shows the actual car being declared active — the icon is the semantic fallback.
        icon = Icons.Rounded.DirectionsCar,
        heroContent = {
            com.rndeveloper.paparcar.ui.components.VehicleGlyph(
                carbody = carbody,
                size = size,
                glyphSize = SET_ACTIVE_HERO_GLYPH_DP.dp,
                color = color,
            )
        },
        title = stringResource(Res.string.vehicle_set_active_confirm_title),
        body = stringResource(Res.string.vehicle_set_active_confirm_body, vehicleName),
        primaryLabel = stringResource(Res.string.vehicle_set_active_confirm_cta),
        primaryLeadingIcon = Icons.Rounded.DirectionsCar,
        onPrimary = onConfirm,
        cancelLabel = stringResource(Res.string.vehicle_set_active_confirm_cancel),
    )
}

// Hero pictogram nominal box inside the 56dp dialog icon circle (glyph lays out ~1.5× wide).
private const val SET_ACTIVE_HERO_GLYPH_DP = 32

@Composable
private fun VehicleTabRow(
    vehicles: List<Vehicle>,
    selectedIndex: Int,
    onTabClick: (Int) -> Unit,
    onAddVehicle: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        vehicles.forEachIndexed { index, vehicle ->
            PapVehicleSelectorPill(
                vehicle = vehicle,
                selected = index == selectedIndex,
                onClick = { onTabClick(index) },
            )
        }
        PaparcarAddChip(
            onClick = onAddVehicle,
            modifier = Modifier.height(VEHICLE_PILL_HEIGHT_DP.dp),
            contentDescription = stringResource(Res.string.my_car_add_vehicle),
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Empty state — big circular icon + display title + prominent CTA
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyVehicleState(
    modifier: Modifier = Modifier,
    onAddVehicle: () -> Unit,
    onShowExplainer: () -> Unit,
    /** The OTHER way to get a car into an empty garage: somebody shared theirs. Here and not in a
     *  menu, because a person invited into a car lands on exactly this screen and would otherwise
     *  be asked to register a car that is not theirs. [VEH-SHARED-001] */
    onJoinSharedCar: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(EMPTY_ICON_CIRCLE_DP.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            com.rndeveloper.paparcar.ui.components.VehicleIcon(
                carbody = null,
                size = VehicleSize.MEDIUM_SUV,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(60.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.my_car_no_vehicle),
            style = PaparcarType.current.heroTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(Res.string.my_car_empty_subtitle),
            style = PaparcarType.current.body,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_BODY_ALPHA),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        // The canonical filled CTA, not a hand-rolled twin. The old Surface filled itself with
        // `PapColor.actionText` — the dark READABLE-TEXT leg of the brand green (4.5:1 floor), so in
        // the light theme this was the one filled button darker than every other CTA in the app.
        // Text-only per the button's own icon policy: the label already names the action.
        // [UI-SEVEN-STRAYS-FROM-THE-CANON-001] [UI-BUTTON-ICONS-EARN-THEIR-PLACE-001]
        PapFooterButton(
            label = stringResource(Res.string.my_car_add_vehicle),
            onClick = onAddVehicle,
            style = PapFooterButtonStyle.Filled,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onJoinSharedCar) {
            Text(
                text = stringResource(Res.string.vehicles_join_shared_car),
                style = PaparcarType.current.label,
                color = PapColor.actionText,
            )
        }
        TextButton(onClick = onShowExplainer) {
            Text(
                text = stringResource(Res.string.my_car_empty_why_link),
                style = PaparcarType.current.label,
                textDecoration = TextDecoration.Underline,
                color = PapColor.actionText,
            )
        }
    }
}

private const val PILL_RADIUS_DP = 999
private const val TAB_HEIGHT_DP = 36
// Carousel page transform: off-centre pages dim to this alpha and shrink to this scale.
private const val PAGER_MIN_ALPHA = 0.5f
private const val PAGER_MIN_SCALE = 0.92f
private const val ACTIVE_DOT_DP = 6
private const val SELECTED_FILL_ALPHA = 0.14f
private const val SELECTED_BORDER_ALPHA = 0.45f // muted "green-line", not the neon primary
private const val EMPTY_ICON_CIRCLE_DP = 120
private val EMPTY_BODY_ALPHA = PapAlpha.body
