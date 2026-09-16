@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.rndeveloper.paparcar.presentation.vehicleregistration

import app.cash.turbine.test
import com.rndeveloper.paparcar.GallonKind
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.EnergyLabel
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.VehicleColor
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VehicleRegistrationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var vehicleRepo: FakeVehicleRepository
    private lateinit var auth: FakeAuthRepository
    private lateinit var prefs: FakeAppPreferences
    private lateinit var vm: VehicleRegistrationViewModel

    private fun vehicle(id: String) = Vehicle(
        id = id,
        userId = "user-1",
        brand = "Toyota",
        model = "Corolla",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        isActive = true,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vehicleRepo = FakeVehicleRepository()
        auth = FakeAuthRepository(initialSession = FakeAuthRepository.authenticatedSession("user-1"))
        // Métrico por defecto: la mayoría de los casos de abajo no hablan de unidades, y en métrico
        // el consumo entra y sale del formulario sin conversión ninguna.
        // [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
        prefs = FakeAppPreferences()
        vm = VehicleRegistrationViewModel(vehicleRepo, auth, prefs)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Field intents ─────────────────────────────────────────────────────────

    @Test
    fun `should_updateBrand_on_SelectBrand`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SelectBrand("Ford"))
        assertEquals("Ford", vm.state.value.brand)
    }

    @Test
    fun `should_updateModel_on_SelectModel`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SelectModel("Focus"))
        assertEquals("Focus", vm.state.value.model)
    }

    @Test
    fun `should_updateSize_on_SetSize`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.VAN_LIGHT))
        assertEquals(VehicleSize.VAN_HIGH, vm.state.value.sizeCategory)
    }

    @Test
    fun `should_updateShowOnSpot_on_SetShowOnSpot`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetShowOnSpot(true))
        assertTrue(vm.state.value.showBrandModelOnSpot)
    }

    // ── Free-text fallback (VEH-FREETEXT-001) ─────────────────────────────────

    @Test
    fun `should_defaultCarbody_and_unblockSubmit_when_freeTextModelHasNoInference`() = runTest {
        // User types a brand + model the catalog can't recognise and that matches no
        // keyword pattern — the form must still let them advance with a sensible default
        // (medium hatchback) that they can refine via the manual picker.
        vm.handleIntent(VehicleRegistrationIntent.SetCustomBrand("MarcaInventada"))
        vm.handleIntent(VehicleRegistrationIntent.SetCustomModel("ModeloSinPista"))

        val s = vm.state.value
        assertEquals(CarbodyType.HATCHBACK_MEDIUM, s.carbodyType)
        assertEquals(VehicleSize.MEDIUM_SUV, s.sizeCategory)
        assertTrue(s.expectsCarbody, "Typing a brand/model implies a CAR")
        assertFalse(s.isCarbodyManualOverride, "Default is an auto fallback, not a manual pick")
        assertFalse(s.canSubmit, "A car without a fuel pick must stay gated [VEH-FUEL-001]")

        vm.handleIntent(VehicleRegistrationIntent.SetFuelType(com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE))
        assertTrue(vm.state.value.canSubmit, "Free-text car with carbody + fuel must be submittable")
    }

    @Test
    fun `should_keepExactInference_when_freeTextMatchesPattern`() = runTest {
        // A recognisable model cue (Hilux → pickup) must win over the generic default.
        vm.handleIntent(VehicleRegistrationIntent.SetCustomBrand("Toyota"))
        vm.handleIntent(VehicleRegistrationIntent.SetCustomModel("Hilux Invincible"))

        assertEquals(CarbodyType.PICKUP, vm.state.value.carbodyType)
        assertEquals(VehicleSize.VAN_HIGH, vm.state.value.sizeCategory)
    }

    // ── Save — success ────────────────────────────────────────────────────────

    @Test
    fun `should_emit_SavedSuccessfully_on_valid_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SelectBrand("Seat"))
        vm.handleIntent(VehicleRegistrationIntent.SelectModel("Ibiza"))
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.HATCHBACK_SMALL))

        vm.effect.test {
            vm.handleIntent(VehicleRegistrationIntent.Save)
            assertIs<VehicleRegistrationEffect.SavedSuccessfully>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_persist_vehicle_to_repo_on_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SelectBrand("Seat"))
        vm.handleIntent(VehicleRegistrationIntent.SelectModel("Ibiza"))
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.HATCHBACK_SMALL))
        vm.handleIntent(VehicleRegistrationIntent.Save)

        val saved = vehicleRepo.observeActiveVehicle().first()
        assertEquals("Ibiza", saved?.model)
    }

    @Test
    fun `should_setColor_on_SetColor_and_reset_on_null`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetColor(VehicleColor.RED))
        assertEquals(VehicleColor.RED, vm.state.value.color)

        vm.handleIntent(VehicleRegistrationIntent.SetColor(null))
        assertNull(vm.state.value.color)
    }

    @Test
    fun `should_persist_color_on_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SelectBrand("Seat"))
        vm.handleIntent(VehicleRegistrationIntent.SelectModel("Ibiza"))
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.HATCHBACK_SMALL))
        vm.handleIntent(VehicleRegistrationIntent.SetColor(VehicleColor.BLUE))
        vm.handleIntent(VehicleRegistrationIntent.Save)

        assertEquals(VehicleColor.BLUE, vehicleRepo.observeActiveVehicle().first()?.color)
    }

    @Test
    fun `should_trim_blank_brand_to_null_on_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SelectBrand("   "))
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.HATCHBACK_MEDIUM))
        vm.handleIntent(VehicleRegistrationIntent.Save)

        assertNull(vehicleRepo.observeActiveVehicle().first()?.brand)
    }

    // ── Save — validation failure ─────────────────────────────────────────────

    @Test
    fun `should_emit_ShowError_when_size_is_null_on_save`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SelectBrand("Ford"))

        vm.effect.test {
            vm.handleIntent(VehicleRegistrationIntent.Save)
            assertIs<VehicleRegistrationEffect.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Load (edit mode) ──────────────────────────────────────────────────────

    @Test
    fun `should_populate_state_when_loading_existing_vehicle`() = runTest {
        val v = vehicle("v-edit")
        vehicleRepo.saveVehicle(v)

        vm.handleIntent(VehicleRegistrationIntent.LoadVehicle("v-edit"))

        assertEquals("Toyota", vm.state.value.brand)
        assertEquals("Corolla", vm.state.value.model)
        assertEquals(VehicleSize.MEDIUM_SUV, vm.state.value.sizeCategory)
        assertEquals("v-edit", vm.state.value.editingVehicleId)
    }

    // ── NavigateBack ──────────────────────────────────────────────────────────

    @Test
    fun `should_emit_NavigateBack_on_NavigateBack`() = runTest {
        vm.effect.test {
            vm.handleIntent(VehicleRegistrationIntent.NavigateBack)
            assertIs<VehicleRegistrationEffect.NavigateBack>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── VEHICLES-002: no duplicate save on rapid taps or after failure ────────

    @Test
    fun `should_call_saveVehicle_only_once_when_Save_intent_arrives_twice_in_a_row`() = runTest {
        // Hold the first save in-flight via a CompletableDeferred so the second intent
        // arrives while state.isSaving is still true and hits the guard.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        vehicleRepo.saveVehicleAwait = gate
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.HATCHBACK_SMALL))

        vm.handleIntent(VehicleRegistrationIntent.Save)  // launches coroutine, suspends on gate
        vm.handleIntent(VehicleRegistrationIntent.Save)  // state.isSaving=true → blocked by guard
        gate.complete(Unit)                              // let the first save finish

        assertEquals(1, vehicleRepo.saveVehicleCallCount)
    }

    @Test
    fun `should_reuse_same_vehicle_id_when_retrying_after_save_failure`() = runTest {
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.HATCHBACK_SMALL))

        // First attempt fails.
        vehicleRepo.saveVehicleThrows = RuntimeException("network error")
        vm.handleIntent(VehicleRegistrationIntent.Save)

        // Second attempt succeeds. The id MUST match the first — otherwise we leak orphans
        // in Firestore (each `.set()` creates a new doc with whatever UUID is passed).
        vm.handleIntent(VehicleRegistrationIntent.Save)

        assertEquals(2, vehicleRepo.saveVehicleCallCount)
        assertEquals(
            vehicleRepo.savedVehicleIds[0],
            vehicleRepo.savedVehicleIds[1],
            "Retry must reuse the memoized pendingNewVehicleId — generating a new UUID corrupts Firestore.",
        )
    }

    // ── Fuel block [VEH-FUEL-001] ────────────────────────────────────────────

    private fun fillCarForm() {
        vm.handleIntent(VehicleRegistrationIntent.SelectBrand("Seat"))
        vm.handleIntent(VehicleRegistrationIntent.SelectModel("Ibiza"))
        vm.handleIntent(VehicleRegistrationIntent.SetCarbody(CarbodyType.HATCHBACK_SMALL))
    }

    @Test
    fun `should_persistFuelAndParsedOverride_on_save`() = runTest {
        fillCarForm()
        vm.handleIntent(VehicleRegistrationIntent.SetFuelType(FuelType.DIESEL))
        // Comma-typed override (Spanish keyboard) must parse; it is an ANSWER, so it persists
        // even if it happened to equal the default.
        vm.handleIntent(VehicleRegistrationIntent.SetConsumption("5,1"))
        vm.handleIntent(VehicleRegistrationIntent.SetEnergyLabel(EnergyLabel.C))

        vm.handleIntent(VehicleRegistrationIntent.Save)

        val saved = vehicleRepo.observeVehicles().first().single()
        assertEquals(FuelType.DIESEL, saved.fuelType)
        assertEquals(5.1f, saved.consumptionPer100)
        assertEquals(EnergyLabel.C, saved.energyLabel)
        assertEquals(null, saved.consumptionElectricPer100, "only a PHEV carries the second figure")
    }

    @Test
    fun `should_storeLitresPer100Km_when_theUserTypedMpg`() = runTest {
        // ⛔ El campo dice mpg y el modelo sigue en l/100 km. Sin la conversión, «42.8» se guardaría
        // tal cual: un coche que gasta 42,8 litros a los cien, y toda la aritmética de ahorro y
        // consumo detrás leyendo ese número. [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
        prefs.setUseImperialUnits(true)
        vm = VehicleRegistrationViewModel(vehicleRepo, auth, prefs)
        fillCarForm()
        vm.handleIntent(VehicleRegistrationIntent.SetFuelType(FuelType.DIESEL))
        vm.handleIntent(VehicleRegistrationIntent.SetConsumption("42.8"))

        vm.handleIntent(VehicleRegistrationIntent.Save)

        val saved = vehicleRepo.observeVehicles().first().single().consumptionPer100
        assertNotNull(saved)
        assertTrue(
            saved in LITRES_LOW..LITRES_HIGH,
            "42.8 mpg son ~5,5 l/100 km (galón US) o ~6,6 (imperial) — llegó $saved",
        )
    }

    @Test
    fun `should_persistNullOverride_when_theConsumptionFieldStaysBlank`() = runTest {
        fillCarForm()
        vm.handleIntent(VehicleRegistrationIntent.SetFuelType(FuelType.GASOLINE))

        vm.handleIntent(VehicleRegistrationIntent.Save)

        val saved = vehicleRepo.observeVehicles().first().single()
        assertEquals(null, saved.consumptionPer100, "blank means no override — the default applies downstream")
    }

    @Test
    fun `should_persistBothFigures_when_thePlugInHybridFillsBoth`() = runTest {
        fillCarForm()
        vm.handleIntent(VehicleRegistrationIntent.SetFuelType(FuelType.PLUG_IN_HYBRID))
        vm.handleIntent(VehicleRegistrationIntent.SetConsumption("2.8"))
        vm.handleIntent(VehicleRegistrationIntent.SetConsumptionElectric("14"))

        vm.handleIntent(VehicleRegistrationIntent.Save)

        val saved = vehicleRepo.observeVehicles().first().single()
        assertEquals(2.8f, saved.consumptionPer100)
        assertEquals(14f, saved.consumptionElectricPer100)
    }

    @Test
    fun `should_dropTheFuelBlock_when_theTypeSwitchesToHumanPowered`() = runTest {
        fillCarForm()
        vm.handleIntent(VehicleRegistrationIntent.SetFuelType(FuelType.GASOLINE))
        vm.handleIntent(VehicleRegistrationIntent.SetEnergyLabel(EnergyLabel.ECO))

        vm.handleIntent(VehicleRegistrationIntent.SetVehicleType(com.rndeveloper.paparcar.domain.model.VehicleType.BIKE))

        val s = vm.state.value
        assertEquals(null, s.fuelType, "a bike burns nothing — no ghost powertrain survives the switch")
        assertEquals(null, s.energyLabel)
        assertFalse(s.expectsFuel)
        assertTrue(s.canSubmit, "a bike needs no fuel to submit")
    }

    @Test
    fun `should_prefillTheFuelBlock_when_editingAnExistingVehicle`() = runTest {
        val existing = Vehicle(
            id = "v-fuel",
            userId = "user-1",
            brand = "Renault",
            model = "Clio",
            sizeCategory = VehicleSize.MICRO_SMALL,
            carbodyType = CarbodyType.HATCHBACK_SMALL,
            fuelType = FuelType.HYBRID,
            consumptionPer100 = 4.5f,
            energyLabel = EnergyLabel.ECO,
        )
        vehicleRepo.saveVehicle(existing)

        vm.handleIntent(VehicleRegistrationIntent.LoadVehicle("v-fuel"))

        val s = vm.state.value
        assertEquals(FuelType.HYBRID, s.fuelType)
        assertEquals("4.5", s.consumptionText)
        assertEquals(EnergyLabel.ECO, s.energyLabel)
    }

    // ── El precio, entre la unidad del surtidor y la del modelo ──────────────
    // [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]

    @Test
    fun `should_storePricePerLitre_when_theUserTypedPerGallon`() = runTest {
        // ⛔ EL TESTIGO DEL TICKET. Este bug no tiene síntoma: un americano teclea 3,499 pensando en
        // su galón, se guarda como precio del LITRO y el gasto de todos sus viajes sale ×3,785 —
        // con un número que parece perfectamente correcto en todas las pantallas. Sin un test que
        // lo mire, vuelve.
        vm = VehicleRegistrationViewModel(vehicleRepo, auth, prefs) { GallonKind.US }
        val currency = vm.state.value.priceCurrency
        assertNotNull(currency, "sin moneda el campo de precio no se ofrece y este testigo no mira nada")
        fillCarForm()
        vm.handleIntent(VehicleRegistrationIntent.SetFuelType(FuelType.GASOLINE))
        vm.handleIntent(VehicleRegistrationIntent.SetFuelPrice("3.499"))

        vm.handleIntent(VehicleRegistrationIntent.Save)

        val saved = vehicleRepo.observeVehicles().first().single().fuelPricePerLiter
        assertNotNull(saved)
        assertTrue(
            saved.amount in PRICE_PER_LITRE_LOW..PRICE_PER_LITRE_HIGH,
            "3,499 por galón US son ~0,924 por litro — llegó ${saved.amount}",
        )
    }

    @Test
    fun `should_keepThePriceUntouched_when_thePumpChargesByTheLitre`() = runTest {
        // El otro lado del mismo borde, y el caso de 193 países: lo tecleado se guarda tal cual.
        vm = VehicleRegistrationViewModel(vehicleRepo, auth, prefs) { null }
        assertNotNull(vm.state.value.priceCurrency, "sin moneda el campo de precio no se ofrece")
        fillCarForm()
        vm.handleIntent(VehicleRegistrationIntent.SetFuelType(FuelType.GASOLINE))
        vm.handleIntent(VehicleRegistrationIntent.SetFuelPrice("1,879"))

        vm.handleIntent(VehicleRegistrationIntent.Save)

        val saved = vehicleRepo.observeVehicles().first().single().fuelPricePerLiter
        assertNotNull(saved)
        assertEquals(1.879, saved.amount, "por litro no se convierte nada")
    }

    @Test
    fun `should_showThePriceInGallons_when_loadingACarOnAGallonPump`() = runTest {
        // ⛔ Las dos direcciones van en pareja: si sólo convirtiera al guardar, abrir la ficha a
        // cambiarle el nombre al coche le multiplicaría el precio por 3,785 cada vez.
        vehicleRepo.saveVehicle(
            vehicle("v-price").copy(
                fuelType = FuelType.GASOLINE,
                fuelPricePerLiter = Money(0.9243, "USD"),
            ),
        )
        vm = VehicleRegistrationViewModel(vehicleRepo, auth, prefs) { GallonKind.US }

        vm.handleIntent(VehicleRegistrationIntent.LoadVehicle("v-price"))

        assertEquals("3.499", vm.state.value.fuelPriceText)
    }

    private companion object {
        /**
         * 42,8 mpg en litros a los cien: ~5,5 con el galón US y ~6,6 con el imperial. El rango cubre
         * los dos porque el galón lo elige el locale de la MÁQUINA que corre el test, y lo que este
         * caso vigila no es cuál se eligió — es que se convirtió.
         */
        const val LITRES_LOW = 5.4f
        const val LITRES_HIGH = 6.7f

        /** 3,499 por galón US = 0,9244… por litro. El margen es de milésima, no de unidad. */
        const val PRICE_PER_LITRE_LOW = 0.923
        const val PRICE_PER_LITRE_HIGH = 0.926
    }
}
