@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.entitlement.canAddOwnVehicle
import com.rndeveloper.paparcar.domain.entitlement.countOwnVehicles
import com.rndeveloper.paparcar.domain.detection.detectionPause
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.permissions.missingProducerPermissions
import com.rndeveloper.paparcar.presentation.permissions.detectionPermissionGap
import com.rndeveloper.paparcar.domain.entitlement.canJoinAnotherCar
import com.rndeveloper.paparcar.domain.entitlement.canShareAnotherCar
import com.rndeveloper.paparcar.domain.entitlement.canShareVehicle
import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarMember
import com.rndeveloper.paparcar.domain.model.SharedCarSession
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleWithStats
import com.rndeveloper.paparcar.domain.repository.SharedCarRepository
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.sharedcar.asHistoryEntry
import com.rndeveloper.paparcar.domain.usecase.fuel.ResolveFuelPriceUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.usecase.vehicle.DeclareActiveVehicleUseCase
import com.rndeveloper.paparcar.domain.usecase.vehicle.DeleteVehicleUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Las cinco fuentes que pintan esta pestaña, en un tipo con nombres — una quíntupla anónima de
 *  `combine` no se puede leer en el sitio donde se desestructura. */
private data class VehiclesSnapshot(
    val vehicles: List<com.rndeveloper.paparcar.domain.model.Vehicle>,
    val sessions: List<UserParking>,
    val isPlus: Boolean,
    val sharedCars: Map<String, SharedCar>,
    val groupFeed: Map<String, GroupFeed>,
    /** Los sitios que el usuario ha nombrado. [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001] */
    val zones: List<Zone> = emptyList(),
)

/** Las sesiones del grupo y quién no enseña su ruta, que es lo que decide cómo se convierten.
 *
 *  `internal` y no `private` porque aquí vive el invariante de que el historial de un coche
 *  compartido SUMA lo tuyo en vez de sustituirlo, y un invariante sin test es una intención.
 *  [VEH-SHARING-A-CAR-DOES-NOT-ERASE-ITS-PAST-001] */
internal data class GroupFeed(
    val sessions: List<SharedCarSession>,
    val hiddenRouteAuthors: Set<String>,
) {
    /** Las filas del historial del grupo: la geometría de quien la tenga apagada no llega a la UI,
     *  pero su DISTANCIA sí — es una cifra, y las cifras son del coche. [VEH-SHARED-001] */
    fun rows(vehicleId: String): List<UserParking> = sessions
        .map { it.asHistoryEntry(vehicleId, showRoute = it.authorUid !in hiddenRouteAuthors) }
        .sortedByDescending { it.location.timestamp }

    /**
     * El historial del coche: lo tuyo y lo del grupo en UNA lista.
     * [VEH-SHARING-A-CAR-DOES-NOT-ERASE-ITS-PAST-001]
     *
     * ⛔ **Unión, no reemplazo.** Compartir un coche no le da una vida nueva. Esto era
     * `groupRows ?: mine`, y el día que el dueño abría el grupo su página se quedaba en blanco
     * —historial vacío, cifras a cero— porque el espejo nace vacío y jamás recibirá su pasado. El
     * espejo AÑADE las sesiones de los demás; las tuyas no se van a ninguna parte.
     *
     * Quien se UNE sí ve poco al principio, y es correcto: no ha conducido ese coche todavía.
     *
     * Las tuyas ganan el desempate, y por eso van primero: una sesión propia posterior a compartir
     * está en las dos listas con el MISMO id —el espejo copia `UserParking.id` en `sessionId`— y la
     * local es la rica, con su provenance y su ruta, sin pasar por la lista blanca del espejo.
     */
    fun rowsWith(mine: List<UserParking>, vehicleId: String): List<UserParking> =
        (mine + rows(vehicleId))
            .distinctBy { it.id }
            .sortedByDescending { it.location.timestamp }

    /**
     * Los viajes que TIENEN ruta y no se están enseñando — los únicos que la fila comenta.
     *
     * ⛔ Las MÍAS no entran, aunque yo tenga el interruptor apagado. Desde la unión de arriba mi
     * fila viene de la copia local, que sí trae la geometría: comentarla como «ruta no compartida»
     * la contradiría dibujando la ruta al lado. Y que las mías no viajan ya lo dice el interruptor
     * de la sección, una vez, en vez de repetirlo en cada fila. La nota es para lo que OTROS no me
     * enseñan. [VEH-SHARING-A-CAR-DOES-NOT-ERASE-ITS-PAST-001]
     */
    fun hiddenRouteSessionIds(selfUid: String?): Set<String> = sessions
        .filter { it.authorUid != selfUid && it.authorUid in hiddenRouteAuthors && routeHidden(it) }
        .map { it.sessionId }
        .toSet()

    private fun routeHidden(session: SharedCarSession) = !session.routePolyline.isNullOrBlank()
}

class VehiclesViewModel(
    private val vehicleRepository: VehicleRepository,
    private val userParkingRepository: UserParkingRepository,
    /** The user's named places — what turns "Calle Serrano" into "Trabajo" in the figures.
     *  [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001] */
    private val zoneRepository: ZoneRepository,
    // Declaring the active vehicle also swaps the OS geofences to it. [VEH-ACTIVE-FENCE-001]
    private val declareActiveVehicle: DeclareActiveVehicleUseCase,
    // The Plus gate over the history window — observed LIVE so losing or regaining Plus
    // re-resolves every timeline in place, no restart needed. [MONEY-001]
    private val observeIsPlus: ObserveIsPlusUseCase,
    /**
     * The price ladder behind the cost figures. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
     *
     * Injected rather than built inside `HistoryTimeline.resolve`, because the national average is
     * no longer a constant compiled into the app: it is a table a refresh can replace, held by one
     * repository so every screen quotes the same one.
     */
    private val resolveFuelPrice: ResolveFuelPriceUseCase,
    /** Read for the detection toggle only. [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001] */
    private val appPreferences: AppPreferences,
    /** Borrar un coche disuelve o abandona el grupo que hubiera detrás.
     *  [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001] */
    private val deleteVehicle: DeleteVehicleUseCase,
    /**
     * El grupo de los coches compartidos. [VEH-SHARED-001]
     *
     * ⛔ Aquí y no en un ViewModel aparte, porque no hay pantalla aparte: un coche compartido es un
     * coche más del pager, y lo que el grupo añade —quién está, dónde lo dejaron, el código, el
     * interruptor de rutas— son secciones de SU página. Un segundo ViewModel obligaría además a
     * resolver dos veces el historial del mismo coche, que es justo lo que la regla de «una
     * superficie, un alcance» existe para impedir.
     */
    private val sharedCarRepository: SharedCarRepository,
    private val authRepository: AuthRepository,
    /** The other half of "is detection going to measure anything": the switch says what the user
     *  WANTS, this says whether it can run. [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001] */
    private val permissionManager: PermissionManager,
) : BaseViewModel<VehiclesState, VehiclesIntent, VehiclesEffect>() {

    override fun initState(): VehiclesState = VehiclesState()

    /** El coche compartido que se está mirando: de ese, y solo de ese, se traen miembros y
     *  sesiones. El pager enseña uno. */
    private val watchedCarId = MutableStateFlow<String?>(null)

    /**
     * Lo que el grupo del coche a la vista aporta al historial: sus sesiones y quién NO enseña sus
     * rutas ahora mismo. [VEH-SHARED-001]
     *
     * ⛔ Las dos cosas juntas y no en dos flujos: la visibilidad se resuelve al LEER, así que si
     * llegaran por separado una lista nueva podría pintarse un instante con la respuesta vieja — y
     * el interruptor se nota justo en ese instante.
     */
    private val groupFeed = MutableStateFlow<Map<String, GroupFeed>>(emptyMap())

    /** Las sesiones PROPIAS por coche, tal como llegaron de Room — a las que vuelve «solo yo». */
    private var personalSessions: Map<String?, List<UserParking>> = emptyMap()

    init {
        viewModelScope.launch {
            val uid = authRepository.getCurrentSession()?.userId
            updateState { copy(currentUid = uid) }
        }
        observeVehicles()
        observeWatchedGroup()
        observeDissolvedSharedCars()
        // ⛔ The INTENT and the BLOCKERS together — see the panel's twin. A switch that cannot run
        // is not on, and the two halves need opposite remedies.
        // [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]
        combine(
            appPreferences.observeAutoDetectParking(),
            permissionManager.permissionState,
        ) { on, perms -> detectionPause(on, perms.missingProducerPermissions()) }
            .distinctUntilChanged()
            .onEach { pause -> updateState { copy(detectionPause = pause) } }
            .catch { e -> PaparcarLogger.e(TAG, "Failed to observe the detection state", e) }
            .launchIn(viewModelScope)
    }

    /** Las zonas de la última emisión — el intent del filtro re-resuelve fuera del stream y tiene
     *  que nombrar los sitios exactamente igual que los acaba de nombrar la emisión.
     *  [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001] */
    private var lastZones: List<Zone> = emptyList()

    /**
     * Miembros y sesiones del coche compartido a la vista. [VEH-SHARED-001]
     *
     * Solo del que se mira: suscribirse a los cinco coches del garaje abriría cinco listeners de
     * Firestore para pintar una página. Al deslizar el pager, el nuevo coche trae los suyos.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeWatchedGroup() {
        watchedCarId
            .flatMapLatest { carId ->
                if (carId == null) {
                    flowOf(emptyList<SharedCarMember>() to emptyList<SharedCarSession>())
                } else combine(
                    sharedCarRepository.observeMembers(carId),
                    sharedCarRepository.observeSessions(carId),
                ) { members, sessions -> members to sessions }
            }
            .onEach { (members, sessions) ->
                val carId = watchedCarId.value
                // Quién tiene el interruptor apagado AHORA: la respuesta se lee viva, así que
                // volver a encenderlo devuelve todos sus viajes de golpe.
                val hiddenAuthors = members.filterNot { it.sharesRoutes }.map { it.uid }.toSet()
                val feed = GroupFeed(sessions, hiddenAuthors)
                groupFeed.value = if (carId == null) emptyMap() else mapOf(carId to feed)
                updateState {
                    copy(
                        sharedMembers = members,
                        // Quién firmó cada fila, y de quién no se dibuja la ruta: las dos cosas que
                        // una lista de varios conductores necesita para no mentir.
                        sharedAuthorByUid = members.mapNotNull { m -> m.displayName?.let { m.uid to it } }.toMap(),
                        sharedWithheldRouteIds = feed.hiddenRouteSessionIds(currentUid),
                    )
                }
            }
            .catch { e -> PaparcarLogger.w(TAG, "shared group stopped observing", e) }
            .launchIn(viewModelScope)
    }

    private fun observeVehicles() {
        // ⛔ Zones belong IN the combine, not in a field updated on the side: they arrive after the
        // first sessions emission, and a place named "Calle Serrano" that only becomes "Trabajo" on
        // the next unrelated emission is the kind of staleness nobody reports and everybody sees.
        // [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]
        combine(
            vehicleRepository.observeVehicles(),
            userParkingRepository.observeAllSessions(),
            observeIsPlus(),
            sharedCarRepository.observeSharedCars(),
            groupFeed,
        ) { vehicles, allSessions, isPlus, sharedCars, group ->
            VehiclesSnapshot(vehicles, allSessions, isPlus, sharedCars.associateBy { it.carId }, group)
        }
            // ⛔ Las zonas van DENTRO del flujo, no en un campo actualizado por fuera: llegan
            // después de las sesiones, y un sitio que se llama «Calle Serrano» y sólo pasa a
            // «Trabajo» en la siguiente emisión ajena es caducidad que nadie reporta y todo el
            // mundo ve. Se enganchan con un `combine` encadenado porque `combine` tipado se acaba
            // en cinco flujos y aquí ya hay cinco. [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]
            .combine(zoneRepository.observeZones()) { snapshot, zones -> snapshot.copy(zones = zones) }
            .onEach { (vehicles, allSessions, isPlus, sharedCars, group, zones) ->
                lastZones = zones
                val sessionsByVehicle = allSessions.groupBy { it.vehicleId }
                personalSessions = sessionsByVehicle
                val nowMs = Clock.System.now().toEpochMilliseconds()
                val vehiclesWithStats = vehicles.map { vehicle ->
                    val vehicleSessions = sessionsByVehicle[vehicle.id].orEmpty()
                    VehicleWithStats(
                        vehicle = vehicle,
                        sessionCount = vehicleSessions.size,
                        lastSession = vehicleSessions.firstOrNull(),
                    )
                }
                updateState {
                    // A focus request that arrived before the list did is honoured HERE, on the
                    // first emission that actually contains the car — never dropped on the race.
                    // [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
                    val focusedIndex = pendingFocusVehicleId?.let { wanted ->
                        vehiclesWithStats.indexOfFirst { it.vehicle.id == wanted }.takeIf { it >= 0 }
                    }
                    val clampedIndex = (focusedIndex ?: selectedVehicleIndex)
                        .coerceIn(0, (vehiclesWithStats.size - 1).coerceAtLeast(0))
                    val updatedCache = vehiclesWithStats.associate { vws ->
                        val vId = vws.vehicle.id
                        val vSessions = sessionsByVehicle[vId].orEmpty()
                        val existingFilter = historyCache[vId]?.activePeriod ?: StatsPeriod.Default
                        // This emission IS the answer to "¿ha llegado el historial?" — resolving it
                        // here is what keeps the skeleton from being replaced by a false "no hay
                        // nada". [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001]
                        // ⛔ El historial y las cifras de un coche COMPARTIDO son los del grupo
                        // (user, 15-09) — mismas filas, mismo agregador, otra lista de origen. El
                        // filtro «solo yo» vuelve a la personal. [VEH-SHARED-001]
                        //
                        // «Del grupo» = las de TODOS, tuyas incluidas: `rowsWith` suma, no
                        // sustituye. Lo contrario dejaba la página del dueño en blanco el día que
                        // compartía. [VEH-SHARING-A-CAR-DOES-NOT-ERASE-ITS-PAST-001]
                        val groupRows = group[vId]
                            ?.takeIf { vws.vehicle.isShared && !sharedHistoryMineOnly }
                            ?.rowsWith(vSessions, vId)
                        vId to HistoryState(
                            timeline = HistoryTimeline.resolve(
                                sessions = groupRows ?: vSessions,
                                filter = existingFilter,
                                isPlus = isPlus,
                                // Consumption, cost and CO₂ are computed with THIS car's figures
                                // (its override, or the named FuelDefaults). [STATS-UI-001]
                                vehicle = vws.vehicle,
                                zones = zones,
                                nowMs = nowMs,
                                resolveFuelPrice = resolveFuelPrice,
                            ),
                            activePeriod = existingFilter,
                        )
                    }
                    // El coche a la vista manda a quién se escucha.
                    vehiclesWithStats.getOrNull(clampedIndex)?.vehicle?.let { onScreen ->
                        watchedCarId.value = onScreen.sharedCarId
                    }
                    copy(
                        vehicles = vehiclesWithStats,
                        sharedCars = sharedCars,
                        isLoading = false,
                        selectedVehicleIndex = clampedIndex,
                        // Resolved (or the car is gone): either way the request is spent.
                        pendingFocusVehicleId = if (focusedIndex != null) null else pendingFocusVehicleId,
                        historyCache = updatedCache,
                        isPlus = isPlus,
                    )
                }
            }
            .catch { e ->
                PaparcarLogger.e(TAG, "Failed to observe vehicles", e)
                updateState { copy(isLoading = false) }
                sendEffect(VehiclesEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: VehiclesIntent) {
        when (intent) {
            is VehiclesIntent.SetActiveVehicle -> setActiveVehicle(intent.vehicleId)
            is VehiclesIntent.BluetoothVehicleConnected ->
                updateState { copy(bluetoothConnectedVehicleId = intent.vehicleId) }
            is VehiclesIntent.SelectVehicle -> updateState {
                val clamped = intent.index.coerceIn(0, (vehicles.size - 1).coerceAtLeast(0))
                copy(selectedVehicleIndex = clamped)
            }
            // Asked from outside by ID: land on it now if the list is here, wait if it is not.
            // [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
            is VehiclesIntent.FocusVehicle -> updateState {
                val index = vehicles.indexOfFirst { it.vehicle.id == intent.vehicleId }
                if (index >= 0) copy(selectedVehicleIndex = index, pendingFocusVehicleId = null)
                else copy(pendingFocusVehicleId = intent.vehicleId)
            }
            // [DET-TOGGLE-001] The SAME single action Home's banner and Settings' switch perform:
            // bank the intent, and open the permissions flow when something is still in the way.
            is VehiclesIntent.EnableAutoDetection -> {
                appPreferences.setAutoDetectParking(true)
                detectionPermissionGap(permissionManager.permissionState.value)?.let { gap ->
                    sendEffect(VehiclesEffect.NavigateToPermissions(gap))
                }
            }
            is VehiclesIntent.EditVehicle ->
                sendEffect(VehiclesEffect.NavigateToEditVehicle(intent.vehicleId))
            // [FREE-LIMITS-001] Soft gate at the ONE chokepoint (FAB and pill send this same
            // intent): free with a car already → paywall. Nothing existing is ever touched.
            // [VEH-SHARED-001] `.size` counted a car somebody SHARED with the user against their
            // one free slot — accepting an invitation would have spent it.
            is VehiclesIntent.AddVehicle ->
                if (canAddOwnVehicle(
                        ownedCount = countOwnVehicles(state.value.vehicles.map { it.vehicle }),
                        isPlus = state.value.isPlus,
                    )
                ) {
                    sendEffect(VehiclesEffect.NavigateToAddVehicle)
                } else {
                    sendEffect(VehiclesEffect.NavigateToPaywall(PaywallReason.VEHICLES))
                }
            is VehiclesIntent.SetStatsPeriod -> {
                val vehicleId = state.value.currentVehicleId ?: return
                val currentHistory = state.value.historyCache[vehicleId] ?: return
                // Nothing to re-scope until the history has arrived: an unresolved timeline has no
                // sessions to filter, and must not be turned into a resolved-but-empty one.
                val resolved = currentHistory.timeline as? HistoryTimeline.Resolved ?: return
                val nowMs = Clock.System.now().toEpochMilliseconds()
                // ⛔ Same gate as the panel's, from the same predicate: a period this account
                // cannot measure opens the paywall and moves nothing. Re-resolving it would scope
                // the chart to a window the account cannot see and leave the figures on another.
                // [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001] [MONEY-001]
                if (!intent.filter.isMeasurableBy(state.value.isPlus, nowMs)) {
                    sendEffect(VehiclesEffect.NavigateToPaywall(PaywallReason.STATS))
                    return
                }
                updateState {
                    val updated = currentHistory.copy(
                        // From the FULL list, never the visible one: re-gating a gated list would
                        // report zero hidden and quietly erase the footer. [MONEY-001]
                        timeline = HistoryTimeline.resolve(
                            sessions = resolved.sessions,
                            filter = intent.filter,
                            isPlus = isPlus,
                            vehicle = vehicles.firstOrNull { it.vehicle.id == vehicleId }?.vehicle,
                            zones = lastZones,
                            nowMs = nowMs,
                            resolveFuelPrice = resolveFuelPrice,
                        ),
                        activePeriod = intent.filter,
                    )
                    copy(historyCache = historyCache + (vehicleId to updated))
                }
            }
            // Only the chart changes, so nothing is re-resolved: the sessions, the window and the
            // figures are the same ones: what moves is which of their numbers the bars draw. And
            // the choice is re-checked against what the period HAS, because a filter change can
            // take the data out from under it. [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001]
            is VehiclesIntent.SetChartMetric -> {
                val vehicleId = state.value.currentVehicleId ?: return
                val currentHistory = state.value.historyCache[vehicleId] ?: return
                val resolved = currentHistory.timeline as? HistoryTimeline.Resolved ?: return
                updateState {
                    val updated = currentHistory.copy(
                        chartMetric = resolveChartMetric(intent.metric, resolved.filteredSessions),
                    )
                    copy(historyCache = historyCache + (vehicleId to updated))
                }
            }
            is VehiclesIntent.ViewLiveParkingOnMap ->
                sendEffect(VehiclesEffect.NavigateToLiveParking(intent.sessionId))
            is VehiclesIntent.ViewOnMap ->
                sendEffect(VehiclesEffect.NavigateToMap(intent.lat, intent.lon, intent.sessionId))
            is VehiclesIntent.OpenPlusPaywall -> sendEffect(VehiclesEffect.NavigateToPaywall(PaywallReason.HISTORY))
            is VehiclesIntent.OpenStatsPaywall -> sendEffect(VehiclesEffect.NavigateToPaywall(PaywallReason.STATS))
            is VehiclesIntent.OpenStatsPanel -> sendEffect(VehiclesEffect.NavigateToStats(intent.vehicleId))
            // ── Coche compartido [VEH-SHARED-001] ────────────────────────────
            // ⛔ Gate BLANDO, como todos: el botón se ve siempre y es la ACCIÓN la que abre el
            // paywall. Esconderlo escondería la feature que Plus vende. [FREE-LIMITS-001]
            is VehiclesIntent.ConfirmShareCar ->
                when {
                    intent.vehicleId == null -> updateState { copy(confirmShareCarId = null) }
                    !canShareVehicle(state.value.isPlus) ->
                        sendEffect(VehiclesEffect.NavigateToPaywall(PaywallReason.SHARED_CAR))
                    // ⛔ Y este NO es un paywall: quien llega aquí YA es Plus. Es un tope de
                    // producto, y el mensaje tiene que decir eso y no ofrecer nada que comprar.
                    // [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001]
                    // ⛔ `isShared` incluye los PRESTADOS (`sharedCarId != null` lo cumple una fila
                    // vinculada): contar eso gastaría el tope del dueño con coches ajenos, que es
                    // justo lo contrario del trato.
                    !canShareAnotherCar(
                        state.value.vehicles.count { it.vehicle.isShared && !it.vehicle.isLinked },
                    ) ->
                        sendEffect(VehiclesEffect.ShowError(PaparcarError.SharedCar.TooManySharedCars))
                    else -> updateState { copy(confirmShareCarId = intent.vehicleId) }
                }
            is VehiclesIntent.ShareCar -> {
                // ⛔ La respuesta de rutas se lee ANTES de cerrar el diálogo: cerrarlo primero la
                // borraba y el grupo nacía con el default que este ticket vino a quitar.
                // [COPY-A-CARD-THAT-PROMISES-ROUTES-STAY-HOME-001]
                val shareRoutes = state.value.confirmShareRoutes
                updateState { copy(confirmShareCarId = null, confirmShareRoutes = false) }
                sharedAction {
                    vehicle(intent.vehicleId)?.let { v ->
                        sharedCarRepository.shareVehicle(v)
                            .mapCatching { car ->
                                sharedCarRepository.setShareRoutes(car.carId, shareRoutes).getOrThrow()
                            }
                    }
                }
            }
            is VehiclesIntent.CreateInvite -> sharedAction { sharedCarRepository.createInvite(intent.carId).map { } }
            is VehiclesIntent.StopSharing -> sharedAction(closeSheet = true) {
                sharedCarRepository.stopSharing(intent.carId)
            }
            is VehiclesIntent.LeaveSharedCar -> sharedAction(closeSheet = true) {
                sharedCarRepository.leave(intent.carId)
            }
            is VehiclesIntent.RemoveMember -> sharedAction {
                sharedCarRepository.removeMember(intent.carId, intent.uid)
            }
            is VehiclesIntent.SetShareRoutes -> sharedAction {
                sharedCarRepository.setShareRoutes(intent.carId, intent.share)
            }
            is VehiclesIntent.ShowMembers -> updateState { copy(membersSheetCarId = intent.carId) }
            is VehiclesIntent.SetSharedHistoryScope -> setSharedHistoryScope(intent.mineOnly)
            VehiclesIntent.KeepDissolvedCar -> answerDissolved(deleteIt = false)
            VehiclesIntent.DeleteDissolvedCar -> answerDissolved(deleteIt = true)
            is VehiclesIntent.SetConfirmShareRoutes ->
                updateState { copy(confirmShareRoutes = intent.share) }
            is VehiclesIntent.SetJoinShareRoutes ->
                updateState { copy(joinDialog = joinDialog?.copy(shareRoutes = intent.share)) }
            is VehiclesIntent.ShowJoinDialog ->
                updateState { copy(joinDialog = if (intent.open) JoinSharedCarDialog() else null) }
            is VehiclesIntent.JoinCodeChanged -> updateState {
                // Escribir borra el rechazo anterior: la respuesta era del código que lo produjo.
                copy(joinDialog = joinDialog?.copy(code = intent.code.uppercase(), error = null))
            }
            VehiclesIntent.SubmitJoinCode -> submitJoinCode()
        }
    }

    private fun vehicle(vehicleId: String) =
        state.value.vehicles.firstOrNull { it.vehicle.id == vehicleId }?.vehicle

    /**
     * Una acción de grupo cada vez, con su fallo dicho en voz alta. [VEH-SHARED-001]
     *
     * Todas son remotas, así que un segundo toque acuñaría dos códigos o echaría a dos personas;
     * y todas pueden fallar de una forma que el usuario puede arreglar (código caducado, grupo
     * lleno), por eso el error viaja como [PaparcarError] y la pantalla lo traduce a su remedio.
     */
    private fun sharedAction(closeSheet: Boolean = false, block: suspend () -> Result<Unit>?) {
        if (state.value.sharedWorking) return
        updateState { copy(sharedWorking = true) }
        viewModelScope.launch {
            val result = block() ?: Result.failure(PaparcarError.SharedCar.SyncFailed)
            result
                .onSuccess { if (closeSheet) updateState { copy(membersSheetCarId = null) } }
                .onFailure { e ->
                    PaparcarLogger.w(TAG, "shared car action failed", e)
                    sendEffect(VehiclesEffect.ShowError(e as? PaparcarError ?: PaparcarError.SharedCar.SyncFailed))
                }
            updateState { copy(sharedWorking = false) }
        }
    }

    /** El alcance del historial del coche a la vista. Re-resuelve en el sitio, sin esperar a una
     *  emisión nueva: el usuario acaba de tocar un chip. */
    /**
     * La pregunta que deja un grupo cerrado por otro.
     * [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001]
     *
     * Se resuelve contra la lista de coches y no contra la preferencia sola: un id anotado cuyo
     * coche ya no existe —porque lo borró desde otra pantalla— no es una pregunta, es un resto, y
     * se limpia en silencio. Se pregunta de UNA en una aunque se cierren dos grupos el mismo día:
     * cada respuesta descubre la siguiente.
     */
    private fun observeDissolvedSharedCars() {
        combine(
            appPreferences.observeDissolvedSharedCars(),
            vehicleRepository.observeVehicles(),
            observeIsPlus(),
        ) { pending, vehicles, isPlus ->
            val vehicle = pending.firstNotNullOfOrNull { id -> vehicles.firstOrNull { it.id == id } }
            if (vehicle == null) {
                pending.forEach { appPreferences.clearDissolvedSharedCar(it) }
                null
            } else {
                DissolvedSharedCarPrompt(
                    vehicleId = vehicle.id,
                    carName = vehicle.displayName(fallback = vehicle.sizeCategory.name),
                    endedParkings = vehicleRepository.getParkingFootprint(vehicle.id).endedParkings,
                    // Conservarlo ocupa plaza solo si ya estaba en el tope: con Plus no hay tope, y
                    // un free sin coche propio se lo queda sin pagar nada.
                    takesTheFreeSlot = !canAddOwnVehicle(
                        ownedCount = countOwnVehicles(vehicles) - 1,
                        isPlus = isPlus,
                    ),
                )
            }
        }
            .distinctUntilChanged()
            .onEach { prompt -> updateState { copy(dissolvedCar = prompt) } }
            .launchIn(viewModelScope)
    }

    private fun answerDissolved(deleteIt: Boolean) {
        val prompt = state.value.dissolvedCar ?: return
        viewModelScope.launch {
            if (deleteIt) {
                val uid = state.value.currentUid
                val vehicle = uid?.let { vehicleRepository.getVehicleById(it, prompt.vehicleId) }
                // El caso de uso limpia la pregunta al borrar; si la fila ya no está, la limpiamos
                // aquí para no dejar la pregunta dando vueltas sin sujeto.
                vehicle?.let { deleteVehicle(it) } ?: appPreferences.clearDissolvedSharedCar(prompt.vehicleId)
            } else {
                appPreferences.clearDissolvedSharedCar(prompt.vehicleId)
            }
        }
    }

    private fun setSharedHistoryScope(mineOnly: Boolean) {
        val vehicleId = state.value.currentVehicleId ?: return
        val vws = state.value.vehicles.firstOrNull { it.vehicle.id == vehicleId } ?: return
        val personal = state.value.historyCache[vehicleId]
        val nowMs = Clock.System.now().toEpochMilliseconds()
        // El MISMO `rowsWith` que la emisión de arriba, y por la misma razón: «todo el grupo» son
        // las de todos, tuyas incluidas. Aquí vivía la segunda copia del reemplazo — el chip
        // devolvía una lista que empezaba en el día que se creó el grupo.
        // [VEH-SHARING-A-CAR-DOES-NOT-ERASE-ITS-PAST-001]
        val mine = personalSessions[vehicleId].orEmpty()
        val groupRows = groupFeed.value[vehicleId]
            ?.takeIf { !mineOnly }
            ?.rowsWith(mine, vehicleId)
        val source = groupRows ?: mine
        val filter = personal?.activePeriod ?: StatsPeriod.Default
        updateState {
            copy(
                sharedHistoryMineOnly = mineOnly,
                historyCache = historyCache + (
                    vehicleId to HistoryState(
                        timeline = HistoryTimeline.resolve(
                            sessions = source,
                            filter = filter,
                            isPlus = isPlus,
                            vehicle = vws.vehicle,
                            nowMs = nowMs,
                            resolveFuelPrice = resolveFuelPrice,
                        ),
                        activePeriod = filter,
                    )
                    ),
            )
        }
    }

    private fun submitJoinCode() {
        val dialog = state.value.joinDialog ?: return
        if (!dialog.canSubmit) return
        // ⛔ Techo tecnico y no paywall: entrar siempre fue gratis porque lo paga el Plus del dueño.
        // Se responde en el propio dialogo, no navegando a ningun sitio que vender.
        // [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001]
        if (!canJoinAnotherCar(state.value.vehicles.count { it.vehicle.isLinked })) {
            updateState {
                copy(joinDialog = dialog.copy(error = PaparcarError.SharedCar.TooManyJoinedCars))
            }
            return
        }
        updateState { copy(joinDialog = dialog.copy(working = true, error = null)) }
        viewModelScope.launch {
            // ⛔ Sin dirección Bluetooth aquí a propósito: la MAC del dueño no viaja, y pedir a
            // alguien que empareje en mitad de una invitación apila un flujo de permisos sobre un
            // campo de texto. La fila vinculada es un coche normal, así que empareja después desde
            // la pantalla de siempre — y la sección se lo recuerda. [VEH-SHARED-001]
            sharedCarRepository.joinByCode(dialog.code, bluetoothDeviceId = null)
                .onSuccess { car ->
                    // Lo contestado en el diálogo, aplicado al entrar: nadie entra en un grupo sin
                    // haber dicho qué comparte. [COPY-A-CARD-THAT-PROMISES-ROUTES-STAY-HOME-001]
                    sharedCarRepository.setShareRoutes(car.carId, dialog.shareRoutes)
                    updateState { copy(joinDialog = null) }
                    // El pager aterriza en el coche recién entrado. Si la lista aún no lo tiene,
                    // FocusVehicle lo deja pendiente y se resuelve en la primera emisión que sí.
                    handleIntent(VehiclesIntent.FocusVehicle(car.carId))
                }
                .onFailure { e ->
                    PaparcarLogger.w(TAG, "join failed", e)
                    updateState {
                        copy(
                            joinDialog = dialog.copy(
                                working = false,
                                error = e as? PaparcarError.SharedCar ?: PaparcarError.SharedCar.SyncFailed,
                            ),
                        )
                    }
                }
        }
    }

    private fun setActiveVehicle(vehicleId: String) {
        if (state.value.settingActiveVehicleId != null) return
        updateState { copy(settingActiveVehicleId = vehicleId) }
        viewModelScope.launch {
            declareActiveVehicle(vehicleId)
                .onSuccess { updateState { copy(settingActiveVehicleId = null) } }
                .onFailure { e ->
                    PaparcarLogger.e(TAG, "Failed to set default vehicle", e)
                    updateState { copy(settingActiveVehicleId = null) }
                    sendEffect(VehiclesEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
                }
        }
    }
    private companion object {
        const val TAG = "VehiclesViewModel"
    }
}
