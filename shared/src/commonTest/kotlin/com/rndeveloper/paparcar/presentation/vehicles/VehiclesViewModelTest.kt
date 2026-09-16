@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import app.cash.turbine.test
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
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

class VehiclesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val location = GpsPoint(40.0, -3.7, 10f, 0L, 0f)

    private fun vehicle(id: String, isActive: Boolean = false) = Vehicle(
        id = id,
        userId = "user-1",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        isActive = isActive,
    )

    private fun session(id: String, vehicleId: String?) = UserParking(
        id = id,
        vehicleId = vehicleId,
        location = location,
        isActive = false,
    )

    private lateinit var vehicleRepo: FakeVehicleRepository
    private lateinit var parkingRepo: FakeUserParkingRepository
    private lateinit var entitlementRepo: com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
    private lateinit var vm: VehiclesViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vehicleRepo = FakeVehicleRepository()
        parkingRepo = FakeUserParkingRepository()
        val declareActiveVehicle = com.rndeveloper.paparcar.domain.usecase.vehicle.DeclareActiveVehicleUseCase(
            vehicleRepository = vehicleRepo,
            swapFences = com.rndeveloper.paparcar.domain.usecase.vehicle.SwapActiveVehicleFencesUseCase(
                userParkingRepository = parkingRepo,
                vehicleRepository = vehicleRepo,
                geofenceService = com.rndeveloper.paparcar.fakes.FakeGeofenceManager(),
                config = com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig(),
            ),
        )
        // PLUS: these tests pin the vehicles/timeline mechanics over arbitrary-age fixtures; the
        // free window is covered by HistoryEntitlementGateTest + the gating test below. [MONEY-001]
        entitlementRepo = com.rndeveloper.paparcar.fakes.FakeEntitlementRepository(
            com.rndeveloper.paparcar.domain.model.Entitlement.PLUS,
        )
        vm = VehiclesViewModel(
            vehicleRepo,
            parkingRepo,
            // No zones: the places still exist, they are just called by their street.
            // [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]
            com.rndeveloper.paparcar.fakes.data.repository.FakeZoneRepository(),
            declareActiveVehicle,
            com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase(entitlementRepo),
            // The offline ladder: averages from the binary, no network. What every screen falls
            // back to anyway. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
            com.rndeveloper.paparcar.domain.usecase.fuel.ResolveFuelPriceUseCase(),
            com.rndeveloper.paparcar.fakes.FakeAppPreferences(),
            // Coche compartido: sin grupo en estos casos — la pestaña se comporta como siempre.
            // [VEH-SHARED-001]
            com.rndeveloper.paparcar.fakes.data.repository.FakeSharedCarRepository(),
            com.rndeveloper.paparcar.fakes.FakeAuthRepository(
                com.apptolast.baselogin.domain.model.UserSession(
                    userId = "user-1", email = null, displayName = null, photoUrl = null,
                ),
            ),
            // Todo concedido: estos casos no van de permisos, y un fake que los negara pondría el
            // aviso de detección pausada sobre las cifras de cada uno.
            // [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]
            com.rndeveloper.paparcar.fakes.data.repository.FakePermissionManager(),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Test
    fun `should_emit_empty_list_with_isLoading_false_on_init`() = runTest {
        assertEquals(emptyList(), vm.state.value.vehicles)
        assertEquals(false, vm.state.value.isLoading)
    }

    @Test
    fun `should_combine_vehicles_and_sessions_into_vehicleWithStats`() = runTest {
        val v1 = vehicle("v1")
        vehicleRepo.saveVehicle(v1)
        parkingRepo.saveNewParkingSession(session("s1", vehicleId = "v1"))
        parkingRepo.saveNewParkingSession(session("s2", vehicleId = "v1"))

        val stats = vm.state.value.vehicles
        assertEquals(1, stats.size)
        assertEquals(2, stats.first().sessionCount)
    }

    @Test
    fun `should_set_sessionCount_zero_for_vehicle_with_no_sessions`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1"))

        val stats = vm.state.value.vehicles
        assertEquals(0, stats.first().sessionCount)
    }

    // ── Historial: "aún no lo sé" ≠ "no hay nada" ─────────────────────────────
    // [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001]

    @Test
    fun `should_report_history_as_unresolved_when_the_vehicle_is_not_in_the_cache_yet`() = runTest {
        // Antes de la primera emisión no hay entrada de caché para ningún coche: el historial debe
        // decir que NO SABE, no devolver una lista vacía ya resuelta (que la UI pinta como
        // "aún no hay historial" sobre el coche del usuario).
        assertIs<HistoryTimeline.Unresolved>(vm.state.value.historyState.timeline)
    }

    @Test
    fun `should_report_history_as_resolved_and_empty_for_a_vehicle_with_no_sessions`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1"))

        val timeline = vm.state.value.historyState.timeline
        assertIs<HistoryTimeline.Resolved>(timeline)
        assertEquals(emptyList(), timeline.sessions)
    }

    @Test
    fun `should_resolve_history_with_its_sessions_when_room_emits`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1"))
        parkingRepo.saveNewParkingSession(session("s1", vehicleId = "v1"))

        val timeline = vm.state.value.historyState.timeline
        assertIs<HistoryTimeline.Resolved>(timeline)
        assertEquals(listOf("s1"), timeline.sessions.map { it.id })
    }

    // ── Paparcar Plus: la ventana free se re-resuelve EN VIVO [MONEY-001] ─────

    @Test
    fun `should_narrowTheTimelineAndCountTheHidden_when_plusIsLost`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1"))
        val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val recent = session("s-recent", "v1").let {
            it.copy(location = it.location.copy(timestamp = nowMs))
        }
        parkingRepo.saveNewParkingSession(recent)
        // The fixture timestamp is 0L — epoch 1970, far beyond any free window.
        parkingRepo.saveNewParkingSession(session("s-ancient", "v1"))

        var timeline = vm.state.value.historyState.timeline
        assertIs<HistoryTimeline.Resolved>(timeline)
        assertEquals(2, timeline.visibleSessions.size, "Plus sees everything")
        assertEquals(0, timeline.hiddenOlderCount)

        entitlementRepo.emitEntitlement(
            com.rndeveloper.paparcar.domain.model.Entitlement.FREE,
        )

        timeline = vm.state.value.historyState.timeline
        assertIs<HistoryTimeline.Resolved>(timeline)
        assertEquals(listOf("s-recent"), timeline.visibleSessions.map { it.id })
        assertEquals(1, timeline.hiddenOlderCount, "the footer must be able to count what is beyond")
        // The FULL list stays underneath, intact: losing Plus filters, never deletes.
        assertEquals(2, timeline.sessions.size)
    }

    /**
     * The ficha's half of the chip gate — the same predicate, the same refusal to repaint.
     * [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]
     */
    @Test
    fun `should_openThePaywallAndKeepTheChip_when_freeTapsAPeriodItCannotMeasure`() = runTest {
        entitlementRepo.emitEntitlement(com.rndeveloper.paparcar.domain.model.Entitlement.FREE)
        vehicleRepo.saveVehicle(vehicle("v1"))
        parkingRepo.saveNewParkingSession(session("s-ancient", "v1"))
        val before = vm.state.value.historyState.activePeriod

        vm.effect.test {
            vm.handleIntent(VehiclesIntent.SetStatsPeriod(StatsPeriod.ThisYear))
            assertEquals(
                VehiclesEffect.NavigateToPaywall(
                    com.rndeveloper.paparcar.domain.entitlement.PaywallReason.STATS,
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(before, vm.state.value.historyState.activePeriod, "the lit chip does not move")
    }

    @Test
    fun `should_rescopeTheChart_when_freeTapsAPeriodInsideItsWindow`() = runTest {
        entitlementRepo.emitEntitlement(com.rndeveloper.paparcar.domain.model.Entitlement.FREE)
        vehicleRepo.saveVehicle(vehicle("v1"))
        parkingRepo.saveNewParkingSession(session("s-ancient", "v1"))

        vm.handleIntent(VehiclesIntent.SetStatsPeriod(StatsPeriod.ThisWeek))

        assertEquals(StatsPeriod.ThisWeek, vm.state.value.historyState.activePeriod)
    }

    @Test
    fun `should_emitNavigateToPaywall_when_theHistoryFooterIsTapped`() = runTest {
        vm.effect.test {
            vm.handleIntent(VehiclesIntent.OpenPlusPaywall)
            assertEquals(
                VehiclesEffect.NavigateToPaywall(com.rndeveloper.paparcar.domain.entitlement.PaywallReason.HISTORY),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Vehicle limit [FREE-LIMITS-001] ──────────────────────────────────────

    @Test
    fun `should_openThePaywall_insteadOfTheForm_when_freeAlreadyHasACar`() = runTest {
        entitlementRepo.emitEntitlement(com.rndeveloper.paparcar.domain.model.Entitlement.FREE)
        vehicleRepo.saveVehicle(vehicle("v1"))

        vm.effect.test {
            vm.handleIntent(VehiclesIntent.AddVehicle)
            assertEquals(
                VehiclesEffect.NavigateToPaywall(com.rndeveloper.paparcar.domain.entitlement.PaywallReason.VEHICLES),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
        // Soft gate: the car that exists is untouched.
        assertEquals(1, vm.state.value.vehicles.size)
    }

    @Test
    fun `should_openTheForm_when_freeHasNoCarYet`() = runTest {
        entitlementRepo.emitEntitlement(com.rndeveloper.paparcar.domain.model.Entitlement.FREE)

        vm.effect.test {
            vm.handleIntent(VehiclesIntent.AddVehicle)
            assertIs<VehiclesEffect.NavigateToAddVehicle>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_openTheForm_when_plusHasSeveralCars`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1"))
        vehicleRepo.saveVehicle(vehicle("v2"))

        vm.effect.test {
            vm.handleIntent(VehiclesIntent.AddVehicle)
            assertIs<VehiclesEffect.NavigateToAddVehicle>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── SetActiveVehicle ──────────────────────────────────────────────────────

    @Test
    fun `should_call_setActiveVehicle_on_SetActiveVehicle`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1"))
        vm.handleIntent(VehiclesIntent.SetActiveVehicle("v1"))
        // FakeVehicleRepository.setActiveVehicle is a no-op — just verify no crash
        assertEquals(1, vm.state.value.vehicles.size)
    }

    // ── Bluetooth connected vehicle ───────────────────────────────────────────

    @Test
    fun `should_set_bluetoothConnectedVehicleId_on_BluetoothVehicleConnected`() = runTest {
        vm.handleIntent(VehiclesIntent.BluetoothVehicleConnected("v1"))
        assertEquals("v1", vm.state.value.bluetoothConnectedVehicleId)
    }

    @Test
    fun `activeVehicle_returns_bluetooth_connected_vehicle_when_present`() = runTest {
        val v1 = vehicle("v1", isActive = true)
        val v2 = vehicle("v2", isActive = false)
        vehicleRepo.saveVehicle(v1)
        vehicleRepo.saveVehicle(v2)
        vm.handleIntent(VehiclesIntent.BluetoothVehicleConnected("v2"))

        assertEquals("v2", vm.state.value.activeVehicle?.id)
    }

    // ── Navigation effects ────────────────────────────────────────────────────

    @Test
    fun `should_emit_NavigateToAddVehicle_on_AddVehicle`() = runTest {
        vm.effect.test {
            vm.handleIntent(VehiclesIntent.AddVehicle)
            assertIs<VehiclesEffect.NavigateToAddVehicle>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_emit_NavigateToEditVehicle_with_id`() = runTest {
        vm.effect.test {
            vm.handleIntent(VehiclesIntent.EditVehicle("v42"))
            val effect = awaitItem()
            assertIs<VehiclesEffect.NavigateToEditVehicle>(effect)
            assertEquals("v42", effect.vehicleId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Un aparcamiento VIVO se ve en Home, no en el detalle de una sesión cerrada: es el AHORA y es
     * donde se puede actuar sobre él. [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
     */
    @Test
    fun `should_sendBackToHome_when_theParkingBeingWatchedIsStillLive`() = runTest {
        vm.effect.test {
            vm.handleIntent(VehiclesIntent.ViewLiveParkingOnMap("s1"))
            val effect = awaitItem()
            assertIs<VehiclesEffect.NavigateToLiveParking>(effect)
            assertEquals("s1", effect.sessionId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Foco pedido desde fuera de la pantalla ────────────────────────────────
    // [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]

    @Test
    fun `should_openOnTheAskedCar_when_theListIsAlreadyThere`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1"))
        vehicleRepo.saveVehicle(vehicle("v2"))

        vm.handleIntent(VehiclesIntent.FocusVehicle("v2"))

        assertEquals(1, vm.state.value.selectedVehicleIndex)
        assertNull(vm.state.value.pendingFocusVehicleId)
    }

    /**
     * La carrera real: "Ver vehículo" navega y la pantalla pide el foco ANTES de que Room haya
     * emitido los coches. Si la petición se descartara, el modal de un coche abriría la ficha de
     * otro — que es exactamente lo que el botón existe para no hacer.
     */
    @Test
    fun `should_holdTheRequestAndResolveIt_when_theListArrivesLater`() = runTest {
        vm.handleIntent(VehiclesIntent.FocusVehicle("v2"))
        assertEquals("v2", vm.state.value.pendingFocusVehicleId)

        vehicleRepo.saveVehicle(vehicle("v1"))
        vehicleRepo.saveVehicle(vehicle("v2"))

        assertEquals(1, vm.state.value.selectedVehicleIndex)
        assertNull(vm.state.value.pendingFocusVehicleId)
    }

    @Test
    fun `should_keepTheCurrentCar_when_theAskedOneIsNotRegistered`() = runTest {
        vehicleRepo.saveVehicle(vehicle("v1"))

        vm.handleIntent(VehiclesIntent.FocusVehicle("ghost"))

        assertEquals(0, vm.state.value.selectedVehicleIndex)
    }

}
