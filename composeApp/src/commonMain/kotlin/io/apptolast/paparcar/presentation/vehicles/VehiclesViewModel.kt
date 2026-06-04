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
import kotlinx.coroutines.flow.first
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
        // Load first page when selected vehicle changes (skip if already cached)
        state
            .map { it.vehicles.getOrNull(it.selectedVehicleIndex)?.vehicle?.id }
            .distinctUntilChanged()
            .onEach { vehicleId ->
                if (vehicleId == null) return@onEach
                if (state.value.historyCache.containsKey(vehicleId)) return@onEach
                updateState {
                    copy(historyCache = historyCache + (vehicleId to HistoryState(isLoading = true)))
                }
                loadHistoryPage(vehicleId, page = 0, append = false)
            }
            .catch { e -> PaparcarLogger.e(TAG, "observeHistory vehicle switch failed", e) }
            .launchIn(viewModelScope)

        // Keep active session in sync reactively — only ended sessions are paginated
        userParkingRepository.observeActiveSessions()
            .onEach { allActive ->
                val vehicleId = state.value.currentVehicleId ?: return@onEach
                mergeActiveIntoHistory(vehicleId, allActive.filter { it.vehicleId == vehicleId })
            }
            .catch { e -> PaparcarLogger.e(TAG, "observeActiveSessions failed", e) }
            .launchIn(viewModelScope)
    }

    private fun mergeActiveIntoHistory(vehicleId: String, activeSessions: List<UserParking>) {
        val current = state.value.historyCache[vehicleId] ?: return
        val ended = current.sessions.filter { !it.isActive }
        val merged = activeSessions + ended
        val nowMs = Clock.System.now().toEpochMilliseconds()
        updateState {
            val updated = current.copy(
                sessions = merged,
                filteredSessions = applyHistoryFilter(merged, current.activeFilter, nowMs),
                statsData = computeHistoryStats(merged, nowMs),
            )
            copy(historyCache = historyCache + (vehicleId to updated))
        }
    }

    private fun loadHistoryPage(vehicleId: String, page: Int, append: Boolean) {
        viewModelScope.launch {
            try {
                val offset = page * PAGE_SIZE
                val raw = userParkingRepository.getSessionsByVehiclePaged(vehicleId, PAGE_SIZE + 1, offset)
                val hasMore = raw.size > PAGE_SIZE
                val newEnded = raw.take(PAGE_SIZE)
                val current = state.value.historyCache[vehicleId] ?: HistoryState()
                val activeSessions = userParkingRepository.observeActiveSessions().first()
                    .filter { it.vehicleId == vehicleId }
                val prevEnded = if (append) current.sessions.filter { !it.isActive } else emptyList()
                val allEnded = prevEnded + newEnded
                val merged = activeSessions + allEnded
                val filter = current.activeFilter
                val nowMs = Clock.System.now().toEpochMilliseconds()
                updateState {
                    val updated = HistoryState(
                        isLoading = false,
                        isLoadingNextPage = false,
                        sessions = merged,
                        activeFilter = filter,
                        filteredSessions = applyHistoryFilter(merged, filter, nowMs),
                        statsData = computeHistoryStats(merged, nowMs),
                        hasMorePages = hasMore,
                        currentPage = page,
                    )
                    copy(historyCache = historyCache + (vehicleId to updated))
                }
            } catch (e: Exception) {
                PaparcarLogger.e(TAG, "Failed to load history page $page for $vehicleId", e)
                val current = state.value.historyCache[vehicleId] ?: return@launch
                updateState {
                    copy(historyCache = historyCache + (vehicleId to current.copy(
                        isLoading = false,
                        isLoadingNextPage = false,
                    )))
                }
                sendEffect(VehiclesEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
        }
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
            is VehiclesIntent.LoadNextHistoryPage -> {
                val vehicleId = state.value.currentVehicleId ?: return
                val h = state.value.historyCache[vehicleId] ?: return
                if (!h.hasMorePages || h.isLoadingNextPage || h.isLoading) return
                updateState {
                    val updated = h.copy(isLoadingNextPage = true)
                    copy(historyCache = historyCache + (vehicleId to updated))
                }
                loadHistoryPage(vehicleId, h.currentPage + 1, append = true)
            }
        }
    }

    private fun setActiveVehicle(vehicleId: String) {
        viewModelScope.launch {
            runCatching { vehicleRepository.setActiveVehicle(vehicleId) }
                .onFailure { e ->
                    PaparcarLogger.e(TAG, "Failed to set default vehicle", e)
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
        const val PAGE_SIZE = 30
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        const val MONTHS_3_MS = 90L * 24 * 60 * 60 * 1000
        const val MIN_WEEKS_FOR_AVG = 2f
        const val MIN_SESSIONS_FOR_PEAK = 5
        const val PERCENT = 100f
    }
}
