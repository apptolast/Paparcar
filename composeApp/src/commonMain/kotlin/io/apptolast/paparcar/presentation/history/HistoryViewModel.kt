@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.history

import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class HistoryViewModel(
    private val vehicleId: String? = null,
    private val userParkingRepository: UserParkingRepository,
) : BaseViewModel<HistoryState, HistoryIntent, HistoryEffect>() {

    init {
        val sessionsFlow = if (vehicleId != null) {
            userParkingRepository.observeSessionsByVehicle(vehicleId)
        } else {
            userParkingRepository.observeAllSessions()
        }
        sessionsFlow
            .onEach { sessions ->
                val filter = state.value.activeFilter
                updateState {
                    copy(
                        isLoading = false,
                        sessions = sessions,
                        filteredSessions = applyFilter(sessions, filter),
                        statsData = computeStats(sessions),
                    )
                }
            }
            .catch { e ->
                updateState { copy(isLoading = false) }
                sendEffect(HistoryEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)
    }

    override fun initState(): HistoryState = HistoryState()

    override fun handleIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.ViewOnMap -> sendEffect(
                HistoryEffect.NavigateToMap(intent.lat, intent.lon)
            )
            is HistoryIntent.SetFilter -> {
                val filtered = applyFilter(state.value.sessions, intent.filter)
                updateState { copy(activeFilter = intent.filter, filteredSessions = filtered) }
            }
        }
    }

    private fun applyFilter(sessions: List<UserParking>, filter: HistoryFilter): List<UserParking> {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        return when (filter) {
            HistoryFilter.All -> sessions
            HistoryFilter.ThisWeek -> sessions.filter {
                it.location.timestamp >= nowMs - WEEK_MS
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
    }

    private fun computeStats(sessions: List<UserParking>): HistoryStatsData? {
        if (sessions.isEmpty()) return null
        val ended = sessions.filter { !it.isActive }

        val avgPerWeek: Float? = run {
            val oldest = ended.minOfOrNull { it.location.timestamp } ?: return@run null
            val nowMs = Clock.System.now().toEpochMilliseconds()
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
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        const val MONTHS_3_MS = 90L * 24 * 60 * 60 * 1000
        const val MIN_WEEKS_FOR_AVG = 2f
        const val MIN_SESSIONS_FOR_PEAK = 5
        const val PERCENT = 100f
    }
}
