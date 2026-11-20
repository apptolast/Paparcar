package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.detection.ParkingAskBudget
import com.rndeveloper.paparcar.domain.detection.ParkingAskKind
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RaiseParkingAskUseCaseTest {
    private val config = ParkingDetectionConfig()
    private class Budget : ParkingAskBudget {
        val stamps = mutableMapOf<String, Long>()
        override fun lastAskAtMs(vehicleId: String) = stamps[vehicleId]
        override fun stampAsk(vehicleId: String, atMs: Long) { stamps[vehicleId] = atMs }
    }

    @Test
    fun should_shareBudgetAcrossKindsAndRestart_when_vehicleIsTheSame() = runTest {
        for (first in ParkingAskKind.entries) {
            val budget = Budget()
            var shown = 0
            RaiseParkingAskUseCase(budget, config)("car", first, 1_000L) { shown++ }
            val restarted = RaiseParkingAskUseCase(budget, config)
            for (next in ParkingAskKind.entries) {
                assertIs<ParkingAskOutcome.Suppressed>(restarted("car", next, 2_000L) { shown++ })
            }
            assertEquals(1, shown)
            assertIs<ParkingAskOutcome.Raised>(
                restarted("car", first, 1_000L + config.safetyNetAskThrottleMs) { shown++ },
            )
            assertEquals(2, shown)
        }
    }

    @Test
    fun should_emitOnlyOneAsk_when_flowsRaceOnDifferentThreads() = runTest {
        val budget = Budget()
        val ask = RaiseParkingAskUseCase(budget, config)
        var shown = 0
        val outcomes = (0 until 100).map { index ->
            async(Dispatchers.Default) {
                ask("car", ParkingAskKind.entries[index % 3], 1_000L) { shown++ }
            }
        }.awaitAll()
        assertEquals(1, shown)
        assertEquals(1, outcomes.count { it is ParkingAskOutcome.Raised })
        assertEquals(99, outcomes.count { it is ParkingAskOutcome.Suppressed })
    }

    @Test
    fun should_keepIndependentBudgets_when_vehiclesDiffer() = runTest {
        val ask = RaiseParkingAskUseCase(Budget(), config)
        var shown = 0
        for (car in listOf("car-a", "car-b")) {
            ask(car, ParkingAskKind.StillParked, 1_000L) { shown++ }
        }
        assertEquals(2, shown)
    }

    @Test
    fun should_notNotify_when_budgetCannotBePersisted() = runTest {
        val budget = object : ParkingAskBudget {
            override fun lastAskAtMs(vehicleId: String): Long? = null
            override fun stampAsk(vehicleId: String, atMs: Long) { error("disk failure") }
        }
        var shown = 0
        assertFailsWith<IllegalStateException> {
            RaiseParkingAskUseCase(budget, config)("car", ParkingAskKind.StillParked, 1_000L) { shown++ }
        }
        assertEquals(0, shown)
    }

    @Test
    fun should_stampBeforeDelivery_when_notificationFails() = runTest {
        val ask = RaiseParkingAskUseCase(Budget(), config)
        assertFailsWith<IllegalStateException> {
            ask("car", ParkingAskKind.StillParked, 1_000L) { error("notification failure") }
        }
        assertIs<ParkingAskOutcome.Suppressed>(
            ask("car", ParkingAskKind.RefineApproximateMark, 2_000L) { error("must not show") },
        )
    }
}
