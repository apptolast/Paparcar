@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.stats

import com.rndeveloper.paparcar.domain.entitlement.gateHistoryToEntitlement
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.stats.PeriodStats
import com.rndeveloper.paparcar.domain.stats.MonthRecapGate
import com.rndeveloper.paparcar.domain.stats.closedMonthWindow
import com.rndeveloper.paparcar.domain.stats.monthRecapGate
import com.rndeveloper.paparcar.domain.stats.previousToClosedMonthWindow
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * *Is it time to tell the user how their month went, and with what?* — the verdict behind the recap
 * notification. [STATS-PLUS-THE-RECAP-KNOCKS-001]
 *
 * The worker that posts the notification decides nothing: it turns this answer into words, which is
 * a locale decision and therefore a platform one — the same split `FuelPriceWatchWorker` already
 * lives by.
 *
 * ⛔ **One gate, not two.** The figures are computed over the history the ENTITLEMENT lets the user
 * see, exactly like the card in the panel does. A separate `if (isPlus)` here would be a second
 * place deciding the plan, and two such places drift; and with a free window of seven days a closed
 * month cannot survive the gate at all, so the data itself is the door.
 */
class RunMonthRecapUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val vehicleRepository: VehicleRepository,
    private val observeIsPlus: ObserveIsPlusUseCase,
    private val appPreferences: AppPreferences,
    private val computeStats: ComputePeriodStatsUseCase = ComputePeriodStatsUseCase(),
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    suspend operator fun invoke(): MonthRecapOutcome {
        val nowMs = clock()
        val closed = closedMonthWindow(nowMs)
        // The cheap gates first, before touching a repository: most ticks of a daily worker are
        // outside the window and must cost nothing.
        when (val early = monthRecapGate(nowMs, appPreferences.lastMonthRecapAnnounced, parkingsInClosedMonth = 1)) {
            is MonthRecapGate.Hold -> return MonthRecapOutcome.Skipped(early.reason)
            is MonthRecapGate.Tell -> Unit
        }

        val vehicle = vehicleRepository.observeActiveVehicle().first()
            ?: return MonthRecapOutcome.Skipped("no-vehicle")
        val isPlus = observeIsPlus().first()
        val visible = gateHistoryToEntitlement(
            sessions = userParkingRepository.observeSessionsByVehicle(vehicle.id).first(),
            isPlus = isPlus,
            nowMs = nowMs,
        ).visible

        val closedSessions = visible.filter { it.location.timestamp in closed }
        // …and now the same gate with the real count, which is the only part that needed the data.
        val gate = monthRecapGate(nowMs, appPreferences.lastMonthRecapAnnounced, closedSessions.size)
        if (gate is MonthRecapGate.Hold) return MonthRecapOutcome.Skipped(gate.reason)

        val before = previousToClosedMonthWindow(nowMs)
        val stats = computeStats(
            sessions = closedSessions,
            vehicles = listOf(vehicle),
            previousSessions = visible.filter { it.location.timestamp in before },
        ) ?: return MonthRecapOutcome.Skipped("nothing")

        // Written BEFORE the notification leaves, not after: a process killed between the two would
        // otherwise tell the same month again on the next tick.
        appPreferences.setLastMonthRecapAnnounced((gate as MonthRecapGate.Tell).yearMonth)
        return MonthRecapOutcome.Tell(stats)
    }
}

sealed interface MonthRecapOutcome {
    /** Nothing to say, with the reason a diagnostic can quote. */
    data class Skipped(val reason: String) : MonthRecapOutcome

    /** The closed month's figures, with the month before attached for the ▲/▼. */
    data class Tell(val stats: PeriodStats) : MonthRecapOutcome
}
