@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.stats

import app.cash.turbine.test
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.usecase.fuel.ResolveFuelPriceUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import com.rndeveloper.paparcar.presentation.vehicles.HistoryFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The stats panel's own behaviour. What it must NOT have is arithmetic of its own — the window and
 * the figures belong to the same places the ficha reads them from — so these tests are about which
 * car it opens on, what a period change does, and what a free account gets. [STATS-PANEL-001]
 */
class StatsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val nowMs = Clock.System.now().toEpochMilliseconds()

    private lateinit var vehicleRepo: FakeVehicleRepository
    private lateinit var parkingRepo: FakeUserParkingRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vehicleRepo = FakeVehicleRepository()
        parkingRepo = FakeUserParkingRepository()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vehicle(id: String, isActive: Boolean = false) = Vehicle(
        id = id,
        userId = "user-1",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        isActive = isActive,
    )

    private fun session(id: String, vehicleId: String, ageDays: Int) = UserParking(
        id = id,
        vehicleId = vehicleId,
        location = GpsPoint(40.4, -3.7, 8f, nowMs - ageDays * DAY_MS, 0f),
        isActive = false,
        endedAtMs = nowMs - ageDays * DAY_MS + 2 * HOUR_MS,
    )

    private fun viewModel(
        initialVehicleId: String? = null,
        entitlement: Entitlement = Entitlement.PLUS,
        prefs: com.rndeveloper.paparcar.fakes.FakeAppPreferences =
            com.rndeveloper.paparcar.fakes.FakeAppPreferences(),
        permissions: com.rndeveloper.paparcar.domain.permissions.PermissionManager =
            com.rndeveloper.paparcar.fakes.data.repository.FakePermissionManager(),
    ) = StatsViewModel(
        vehicleRepository = vehicleRepo,
        userParkingRepository = parkingRepo,
        observeIsPlus = ObserveIsPlusUseCase(FakeEntitlementRepository(entitlement)),
        zoneRepository = com.rndeveloper.paparcar.fakes.data.repository.FakeZoneRepository(),
        // The offline ladder: averages from the binary, no network. What every screen falls back
        // to anyway. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
        resolveFuelPrice = ResolveFuelPriceUseCase(),
        appPreferences = prefs,
        permissionManager = permissions,
        initialVehicleId = initialVehicleId,
    )

    @Test
    fun `should_openOnTheCarItWasGiven_notTheActiveOne`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1", isActive = true))
        vehicleRepo.saveVehicle(vehicle("v2"))

        val vm = viewModel(initialVehicleId = "v2")

        assertEquals("v2", vm.state.value.selectedVehicle?.vehicle?.id)
    }

    @Test
    fun `should_fallBackToTheActiveCar_when_openedWithoutOne`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1"))
        vehicleRepo.saveVehicle(vehicle("v2", isActive = true))

        val vm = viewModel()

        assertEquals("v2", vm.state.value.selectedVehicle?.vehicle?.id)
    }

    @Test
    fun `should_keepTheUsersChoice_when_theRepositoryEmitsAgain`() = runTest {
        // A re-emission must not yank the panel back to the opening car while it is being read.
        vehicleRepo.saveVehicle(vehicle("v1", isActive = true))
        vehicleRepo.saveVehicle(vehicle("v2"))
        val vm = viewModel()

        vm.handleIntent(StatsIntent.SelectVehicle(1))
        parkingRepo.saveNewParkingSession(session("s1", "v1", ageDays = 1))

        assertEquals("v2", vm.state.value.selectedVehicle?.vehicle?.id)
    }

    @Test
    fun `should_hideTheVehicleSelector_when_thereIsOnlyOneCar`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1", isActive = true))

        assertEquals(false, viewModel().state.value.showsVehicleSelector)
    }

    @Test
    fun `should_rescopeTheFigures_when_thePeriodChanges`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1", isActive = true))
        parkingRepo.saveNewParkingSession(session("s-recent", "v1", ageDays = 0))
        parkingRepo.saveNewParkingSession(session("s-old", "v1", ageDays = 200))
        val vm = viewModel()

        vm.handleIntent(StatsIntent.SetFilter(HistoryFilter.All))
        assertEquals(2, vm.state.value.periodStats?.parkings)

        vm.handleIntent(StatsIntent.SetFilter(HistoryFilter.ThisMonth))
        assertEquals(1, vm.state.value.periodStats?.parkings, "a 200-day-old parking is not this month")
    }

    @Test
    fun `should_countTheLifetimeOverEverything_evenForAFreeAccount`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1", isActive = true))
        parkingRepo.saveNewParkingSession(session("s-recent", "v1", ageDays = 1))
        parkingRepo.saveNewParkingSession(session("s-old", "v1", ageDays = 60))
        val vm = viewModel(entitlement = Entitlement.FREE)

        assertEquals(2, vm.state.value.lifetimeParkings, "desde siempre has to mean it")
        assertEquals(1, vm.state.value.periodStats?.parkings, "figures stay inside the free window")
    }

    /**
     * ⛔ The tap does NOT repaint anything. Re-resolving would scope the chart to a window the
     * account cannot see — an axis of empty buckets that reads as "you did not park" — while the
     * figures stayed on the seven days beside it.
     * [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]
     */
    @Test
    fun `should_openThePaywallAndMoveNothing_when_aFreeAccountTapsAPeriodItCannotMeasure`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1", isActive = true))
        parkingRepo.saveNewParkingSession(session("s-recent", "v1", ageDays = 1))
        parkingRepo.saveNewParkingSession(session("s-old", "v1", ageDays = 60))
        val vm = viewModel(entitlement = Entitlement.FREE)
        val before = vm.state.value

        vm.effect.test {
            vm.handleIntent(StatsIntent.SetFilter(HistoryFilter.All))
            val effect = awaitItem()
            assertIs<StatsEffect.NavigateToPaywall>(effect)
            assertEquals(PaywallReason.STATS, effect.reason)
        }

        assertEquals(before.filter, vm.state.value.filter, "the lit chip does not move")
        assertEquals(before.filteredSessions.size, vm.state.value.filteredSessions.size)
        assertEquals(before.periodStats?.parkings, vm.state.value.periodStats?.parkings)
    }

    /** The two windows a seven-day history always covers whole are NOT gated: free asks, the app
     *  answers. [MONEY-001] */
    @Test
    fun `should_answerWithoutAPaywall_when_aFreeAccountTapsAPeriodInsideItsWindow`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1", isActive = true))
        parkingRepo.saveNewParkingSession(session("s-recent", "v1", ageDays = 1))
        val vm = viewModel(entitlement = Entitlement.FREE)

        vm.effect.test {
            vm.handleIntent(StatsIntent.SetFilter(HistoryFilter.ThisWeek))
            expectNoEvents()
        }
        assertEquals(HistoryFilter.ThisWeek, vm.state.value.filter)
    }

    /** Plus is not gated by any of this — the same tap that stops a free account answers for Plus. */
    @Test
    fun `should_rescopeEveryPeriod_when_thePlanIsPlus`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1", isActive = true))
        parkingRepo.saveNewParkingSession(session("s-old", "v1", ageDays = 200))
        val vm = viewModel(entitlement = Entitlement.PLUS)

        vm.effect.test {
            vm.handleIntent(StatsIntent.SetFilter(HistoryFilter.All))
            expectNoEvents()
        }
        assertEquals(HistoryFilter.All, vm.state.value.filter)
        assertEquals(1, vm.state.value.periodStats?.parkings)
    }

    /**
     * The notice over the figures. ⛔ It reads the switch AND the blockers: an account whose
     * background location lapsed measures nothing while its switch still says "on", and that is the
     * population that most needs telling. [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001]
     */
    @Test
    fun `should_sayWhyTheFiguresStopped_when_detectionCannotMeasure`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1", isActive = true))

        assertNull(viewModel().state.value.detectionPause, "everything granted and switched on")

        val off = com.rndeveloper.paparcar.fakes.FakeAppPreferences()
        off.setAutoDetectParking(false)
        assertEquals(
            com.rndeveloper.paparcar.domain.detection.DetectionPause.TURNED_OFF,
            viewModel(prefs = off).state.value.detectionPause,
        )

        // Core granted, PRODUCER missing: the switch still reads "on" and nothing gets measured.
        val coreOnly = com.rndeveloper.paparcar.fakes.MockScenario().apply {
            permissionTier.value = com.rndeveloper.paparcar.fakes.MockScenario.PermissionTier.Core
        }
        assertEquals(
            com.rndeveloper.paparcar.domain.detection.DetectionPause.MISSING_PERMISSIONS,
            viewModel(
                permissions = com.rndeveloper.paparcar.fakes.data.repository
                    .FakePermissionManager(coreOnly),
            ).state.value.detectionPause,
            "a switch that cannot run is not on",
        )
    }

    @Test
    fun `should_haveNoFigures_when_thePeriodIsEmpty`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1", isActive = true))
        val vm = viewModel()

        assertNull(vm.state.value.periodStats)
        assertEquals(false, vm.state.value.isLoading, "resolved with nothing is not still loading")
    }

    @Test
    fun `should_openThePaywall_stampedWithStats`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1", isActive = true))
        val vm = viewModel(entitlement = Entitlement.FREE)

        vm.effect.test {
            vm.handleIntent(StatsIntent.OpenPaywall)
            val effect = awaitItem()
            assertIs<StatsEffect.NavigateToPaywall>(effect)
            assertEquals(PaywallReason.STATS, effect.reason)
        }
    }

    @Test
    fun `should_carryTheEntitlement_intoThePanelState`() = runTest {
        // The panel shows every figure to both; the entitlement only decides the period's name and
        // the Plus row. [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]
        vehicleRepo.saveVehicle(vehicle("v1", isActive = true))
        repeat(6) { i -> parkingRepo.saveNewParkingSession(session("s$i", "v1", ageDays = i)) }

        assertTrue(viewModel(entitlement = Entitlement.PLUS).state.value.isPlus)
        assertEquals(false, viewModel(entitlement = Entitlement.FREE).state.value.isPlus)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val HOUR_MS = 3_600_000L
    }
}
