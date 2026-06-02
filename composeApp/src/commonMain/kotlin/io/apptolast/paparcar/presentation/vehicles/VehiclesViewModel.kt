@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.vehicles

import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleWithStats
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class VehiclesViewModel(
    private val vehicleRepository: VehicleRepository,
    private val userParkingRepository: UserParkingRepository,
) : BaseViewModel<VehiclesState, VehiclesIntent, VehiclesEffect>() {

    override fun initState(): VehiclesState = VehiclesState()

    init {
        observeVehicles()
        observeHistory()
    }

    private fun observeVehicles() {
        combine(
            vehicleRepository.observeVehicles(),
            userParkingRepository.observeAllSessions(),
        ) { vehicles, sessions ->
            val sessionsByVehicle = sessions.groupBy { it.vehicleId }
            vehicles.map { vehicle ->
                val vehicleSessions = sessionsByVehicle[vehicle.id].orEmpty()
                VehicleWithStats(
                    vehicle = vehicle,
                    sessionCount = vehicleSessions.size,
                    lastSession = vehicleSessions.firstOrNull(),
                )
            }
        }
            .onEach { vehiclesWithStats ->
                updateState {
                    val clampedIndex = selectedVehicleIndex
                        .coerceIn(0, (vehiclesWithStats.size - 1).coerceAtLeast(0))
                    copy(vehicles = vehiclesWithStats, isLoading = false, selectedVehicleIndex = clampedIndex)
                }
            }
            .catch { e ->
                PaparcarLogger.e(TAG, "Failed to observe vehicles", e)
                updateState { copy(isLoading = false) }
                sendEffect(VehiclesEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)
    }

    private fun observeHistory() {
        state
            .map { it.vehicles.getOrNull(it.selectedVehicleIndex)?.vehicle?.id }
            .distinctUntilChanged()
            .flatMapLatest { vehicleId ->
                if (vehicleId == null) {
                    flowOf<Pair<String?, HistoryState>>(null to HistoryState(isLoading = false))
                } else {
                    flow<Pair<String?, HistoryState>> {
                        val cached = state.value.historyCache[vehicleId]
                        val currentFilter = cached?.activeFilter ?: HistoryFilter.All
                        if (cached == null) {
                            emit(vehicleId to HistoryState(isLoading = true, activeFilter = currentFilter))
                        }
                        userParkingRepository.observeSessionsByVehicle(vehicleId).collect { sessions ->
                            val filter = state.value.historyCache[vehicleId]?.activeFilter ?: HistoryFilter.All
                            val nowMs = Clock.System.now().toEpochMilliseconds()
                            emit(
                                vehicleId to HistoryState(
                                    isLoading = false,
                                    sessions = sessions,
                                    activeFilter = filter,
                                    filteredSessions = applyHistoryFilter(sessions, filter, nowMs),
                                    statsData = computeHistoryStats(sessions, nowMs),
                                )
                            )
                        }
                    }
                }
            }
            .onEach { (vehicleId, newHistoryState) ->
                val id = vehicleId ?: return@onEach
                updateState { copy(historyCache = historyCache + (id to newHistoryState)) }
            }
            .catch { e ->
                PaparcarLogger.e(TAG, "Failed to load history", e)
                val vid = state.value.currentVehicleId ?: return@catch
                updateState { copy(historyCache = historyCache + (vid to HistoryState(isLoading = false))) }
                sendEffect(VehiclesEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: VehiclesIntent) {
        when (intent) {
            is VehiclesIntent.SetActiveVehicle -> setActiveVehicle(intent.vehicleId)
            is VehiclesIntent.BluetoothVehicleConnected ->
                updateState { copy(bluetoothConnectedVehicleId = intent.vehicleId) }
            is VehiclesIntent.RequestDeleteVehicle -> {
                if (state.value.vehicles.size <= 1) {
                    sendEffect(VehiclesEffect.ShowCannotDeleteLastVehicle)
                } else {
                    updateState { copy(pendingDeleteVehicleId = intent.vehicleId) }
                }
            }
            is VehiclesIntent.DismissDeleteConfirmation ->
                updateState { copy(pendingDeleteVehicleId = null) }
            is VehiclesIntent.ConfirmDeleteVehicle -> {
                updateState { copy(pendingDeleteVehicleId = null) }
                deleteVehicle(intent.vehicleId)
            }
            is VehiclesIntent.SelectVehicle -> updateState {
                val clamped = intent.index.coerceIn(0, (vehicles.size - 1).coerceAtLeast(0))
                copy(selectedVehicleIndex = clamped)
            }
            is VehiclesIntent.EditVehicle ->
                sendEffect(VehiclesEffect.NavigateToEditVehicle(intent.vehicleId))
            is VehiclesIntent.AddVehicle -> sendEffect(VehiclesEffect.NavigateToAddVehicle)
            is VehiclesIntent.SetHistoryFilter -> {
                val vehicleId = state.value.currentVehicleId ?: return
                val currentHistory = state.value.historyCache[vehicleId] ?: return
                val filtered = applyHistoryFilter(currentHistory.sessions, intent.filter)
                updateState {
                    val updated = currentHistory.copy(activeFilter = intent.filter, filteredSessions = filtered)
                    copy(historyCache = historyCache + (vehicleId to updated))
                }
            }
            is VehiclesIntent.ViewOnMap ->
                sendEffect(VehiclesEffect.NavigateToMap(intent.lat, intent.lon, intent.sessionId))
        }
    }

    private fun setActiveVehicle(vehicleId: String) {
        viewModelScope.launch {
            runCatching { vehicleRepository.setDefaultVehicle(vehicleId) }
                .onFailure { e ->
                    PaparcarLogger.e(TAG, "Failed to set default vehicle", e)
                    sendEffect(VehiclesEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
                }
        }
    }

    private fun deleteVehicle(vehicleId: String) {
        viewModelScope.launch {
            runCatching { vehicleRepository.deleteVehicle(vehicleId) }
                .onFailure { e ->
                    PaparcarLogger.e(TAG, "Failed to delete vehicle", e)
                    sendEffect(VehiclesEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
                }
        }
    }

    private fun applyHistoryFilter(
        sessions: List<UserParking>,
        filter: HistoryFilter,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): List<UserParking> = when (filter) {
        HistoryFilter.All -> sessions
        HistoryFilter.ThisWeek -> {
            val tz = TimeZone.currentSystemDefault()
            val nowLocal = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz)
            val daysFromMonday = nowLocal.date.dayOfWeek.isoDayNumber - 1
            val weekStartMs = nowLocal.date
                .minus(daysFromMonday, DateTimeUnit.DAY)
                .atStartOfDayIn(tz)
                .toEpochMilliseconds()
            sessions.filter { it.location.timestamp >= weekStartMs }
        }
        HistoryFilter.ThisMonth -> {
            val tz = TimeZone.currentSystemDefault()
            val nowLocal = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz)
            sessions.filter {
                val dt = Instant.fromEpochMilliseconds(it.location.timestamp).toLocalDateTime(tz)
                dt.year == nowLocal.year && dt.month == nowLocal.month
            }
        }
        HistoryFilter.Last3Months -> sessions.filter {
            it.location.timestamp >= nowMs - MONTHS_3_MS
        }
    }

    private fun computeHistoryStats(
        sessions: List<UserParking>,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): HistoryStatsData? {
        if (sessions.isEmpty()) return null
        val ended = sessions.filter { !it.isActive }

        val avgPerWeek: Float? = run {
            val oldest = ended.minOfOrNull { it.location.timestamp } ?: return@run null
            val weeks = (nowMs - oldest).toFloat() / WEEK_MS
            if (weeks < MIN_WEEKS_FOR_AVG) null else ended.size / weeks
        }

        val peakDay: Int? = run {
            if (ended.size < MIN_SESSIONS_FOR_PEAK) return@run null
            val tz = TimeZone.currentSystemDefault()
            ended
                .groupBy<UserParking, Int> {
                    Instant.fromEpochMilliseconds(it.location.timestamp)
                        .toLocalDateTime(tz).date.dayOfWeek.isoDayNumber
                }
                .maxByOrNull { it.value.size }
                ?.key
        }

        val topStreet: String? = ended
            .mapNotNull { it.address?.street?.takeIf { s -> s.isNotBlank() } }
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key

        val avgReliabilityPct: Int? = ended
            .mapNotNull { it.detectionReliability }
            .takeIf { it.isNotEmpty() }
            ?.let { (it.sum() / it.size * PERCENT).toInt() }

        return HistoryStatsData(
            avgSessionsPerWeek = avgPerWeek,
            mostActiveDayOfWeek = peakDay,
            favoriteStreet = topStreet,
            avgReliabilityPct = avgReliabilityPct,
        )
    }

    private companion object {
        const val TAG = "VehiclesViewModel"
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        const val MONTHS_3_MS = 90L * 24 * 60 * 60 * 1000
        const val MIN_WEEKS_FOR_AVG = 2f
        const val MIN_SESSIONS_FOR_PEAK = 5
        const val PERCENT = 100f
    }
}
