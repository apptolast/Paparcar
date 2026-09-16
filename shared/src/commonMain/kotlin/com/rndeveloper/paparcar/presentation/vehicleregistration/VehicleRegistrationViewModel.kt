package com.rndeveloper.paparcar.presentation.vehicleregistration

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.VehicleType
import com.rndeveloper.paparcar.domain.vehicle.VehicleActiveStatePolicy
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.GallonKind
import com.rndeveloper.paparcar.localeGallonKind
import com.rndeveloper.paparcar.localePumpGallon
import com.rndeveloper.paparcar.presentation.util.pumpPriceFromDisplay
import com.rndeveloper.paparcar.presentation.util.pumpPriceToDisplay
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import com.rndeveloper.paparcar.presentation.util.DistanceUnit
import com.rndeveloper.paparcar.presentation.util.consumptionFromDisplay
import com.rndeveloper.paparcar.presentation.util.consumptionToDisplay
import com.rndeveloper.paparcar.presentation.util.platformCurrencyCode
import com.rndeveloper.paparcar.presentation.util.roundToTenth
import com.rndeveloper.paparcar.presentation.vehicleregistration.data.VehicleCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.round
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class VehicleRegistrationViewModel(
    private val vehicleRepository: VehicleRepository,
    private val authRepository: AuthRepository,
    /** Sólo para saber en qué unidad se TECLEA el consumo: mpg si el usuario eligió imperial, y de
     *  vuelta a l/100 km antes de guardar. El modelo nunca ve mpg.
     *  [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001] */
    private val appPreferences: AppPreferences,
    /** En qué unidad cobra el surtidor de este usuario — `null` = litros, que es el caso de 193
     *  países. Entra por el constructor y no se lee del locale aquí dentro porque es la ÚNICA forma
     *  de que un test pueda mirar el caso de EE. UU., que es justo el que no tiene síntoma visible.
     *  [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001] */
    private val pumpGallon: () -> GallonKind? = ::localePumpGallon,
) : BaseViewModel<VehicleRegistrationState, VehicleRegistrationIntent, VehicleRegistrationEffect>() {

    // The money this phone is set in, read once: it labels whatever the user types into the price
    // fields, and its absence is what hides them. [FUEL-PRICE-RESOLVER-001]
    override fun initState(): VehicleRegistrationState =
        VehicleRegistrationState(priceCurrency = platformCurrencyCode())

    init {
        viewModelScope.launch {
            runCatching {
                val count = vehicleRepository.observeVehicles().first().size
                updateState { copy(existingVehicleCount = count) }
            }
        }
    }

    override fun handleIntent(intent: VehicleRegistrationIntent) {
        when (intent) {
            is VehicleRegistrationIntent.SetName ->
                updateState { copy(name = intent.value, hasInteractedWithForm = true) }

            is VehicleRegistrationIntent.SelectBrand -> updateState {
                copy(
                    brand = intent.brand,
                    isBrandOther = false,
                    model = "",
                    isModelOther = false,
                    carbodyType = null,
                    sizeCategory = null,
                    isCarbodyManualOverride = false,
                    vehicleType = vehicleType ?: VehicleType.CAR,
                    hasInteractedWithForm = true,
                )
            }
            is VehicleRegistrationIntent.SelectBrandOther -> updateState {
                copy(
                    brand = "",
                    isBrandOther = true,
                    model = "",
                    isModelOther = false,
                    carbodyType = null,
                    sizeCategory = null,
                    isCarbodyManualOverride = false,
                    vehicleType = vehicleType ?: VehicleType.CAR,
                    hasInteractedWithForm = true,
                )
            }
            is VehicleRegistrationIntent.SetCustomBrand -> updateState {
                // Typing in the brand field always switches to "custom brand" mode. If we were
                // previously on a catalog selection, drop the model so it doesn't outlive the
                // brand it was tied to.
                val wasCatalog = !isBrandOther
                val nextModel = if (wasCatalog) "" else model
                val inferred = inferCarbody(vehicleType, intent.value, nextModel)
                copy(
                    brand = intent.value,
                    isBrandOther = true,
                    model = nextModel,
                    isModelOther = if (wasCatalog) false else isModelOther,
                    carbodyType = inferred,
                    sizeCategory = resolveSize(vehicleType, inferred),
                    isCarbodyManualOverride = false,
                    // Typing a brand implies a CAR (no non-CAR picker on this screen);
                    // keep parity with SelectBrand so expectsCarbody surfaces the carbody card.
                    vehicleType = vehicleType ?: VehicleType.CAR,
                    hasInteractedWithForm = true,
                )
            }

            is VehicleRegistrationIntent.SelectModel -> updateState {
                val inferred = inferCarbody(vehicleType, brand, intent.model)
                copy(
                    model = intent.model,
                    isModelOther = false,
                    carbodyType = inferred,
                    sizeCategory = resolveSize(vehicleType, inferred),
                    isCarbodyManualOverride = false,
                    vehicleType = vehicleType ?: VehicleType.CAR,
                    hasInteractedWithForm = true,
                )
            }
            is VehicleRegistrationIntent.SelectModelOther -> updateState {
                copy(
                    model = "",
                    isModelOther = true,
                    carbodyType = null,
                    sizeCategory = if (vehicleType == null || vehicleType.hasCarbody) null else VehicleSize.MOTORCYCLE,
                    isCarbodyManualOverride = false,
                    hasInteractedWithForm = true,
                )
            }
            is VehicleRegistrationIntent.SetCustomModel -> updateState {
                // Typing in the model field always switches to "custom model" mode so the
                // canSubmit gate treats the value as user-supplied free text.
                val inferred = inferCarbody(vehicleType, brand, intent.value)
                copy(
                    model = intent.value,
                    isModelOther = true,
                    carbodyType = inferred,
                    sizeCategory = resolveSize(vehicleType, inferred),
                    isCarbodyManualOverride = false,
                    vehicleType = vehicleType ?: VehicleType.CAR,
                    hasInteractedWithForm = true,
                )
            }

            is VehicleRegistrationIntent.SetCarbody -> updateState {
                copy(
                    carbodyType = intent.body,
                    sizeCategory = intent.body.sizeCategory,
                    isCarbodyManualOverride = true,
                    hasInteractedWithForm = true,
                )
            }
            is VehicleRegistrationIntent.SetVehicleType -> updateState {
                val newType = intent.type
                // [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001] The form asks the type
                // whether it HAS a body, instead of asking whether it is the one type that does.
                if (newType.hasCarbody) {
                    val inferred = inferCarbody(newType, brand, model)
                    copy(
                        vehicleType = newType,
                        carbodyType = inferred,
                        sizeCategory = resolveSize(newType, inferred),
                        isCarbodyManualOverride = false,
                        hasInteractedWithForm = true,
                    )
                } else {
                    // A type with no carbody is sized as MOTORCYCLE for the spot fit calculation
                    // and persists a null body — today that is motorcycle, scooter and bike, but
                    // the branch is chosen by the answer, not by the list.
                    copy(
                        vehicleType = newType,
                        carbodyType = null,
                        sizeCategory = VehicleSize.MOTORCYCLE,
                        isCarbodyManualOverride = false,
                        // A human-powered type burns nothing: drop the fuel block so a car→bike
                        // switch does not persist a ghost powertrain. [VEH-FUEL-001]
                        fuelType = if (newType.burnsFuel) fuelType else null,
                        consumptionText = if (newType.burnsFuel) consumptionText else "",
                        consumptionElectricText = if (newType.burnsFuel) consumptionElectricText else "",
                        energyLabel = if (newType.burnsFuel) energyLabel else null,
                        hasInteractedWithForm = true,
                    )
                }
            }
            is VehicleRegistrationIntent.SetFuelType -> updateState {
                copy(
                    fuelType = intent.fuel,
                    // The secondary field only exists for a PHEV — a leftover value from a
                    // previous pick must not survive the switch. [VEH-FUEL-001]
                    consumptionElectricText =
                        if (intent.fuel.hasSecondaryElectric) consumptionElectricText else "",
                    hasInteractedWithForm = true,
                )
            }
            is VehicleRegistrationIntent.SetConsumption ->
                updateState { copy(consumptionText = intent.value) }
            is VehicleRegistrationIntent.SetFuelPrice ->
                updateState { copy(fuelPriceText = intent.value) }
            is VehicleRegistrationIntent.SetEnergyPrice ->
                updateState { copy(energyPriceText = intent.value) }
            is VehicleRegistrationIntent.SetConsumptionElectric ->
                updateState { copy(consumptionElectricText = intent.value) }
            is VehicleRegistrationIntent.SetEnergyLabel ->
                updateState { copy(energyLabel = intent.label, hasInteractedWithForm = true) }
            is VehicleRegistrationIntent.SetColor ->
                updateState { copy(color = intent.color, hasInteractedWithForm = true) }
            is VehicleRegistrationIntent.SetLicensePlate ->
                updateState { copy(licensePlate = intent.value) }
            is VehicleRegistrationIntent.SetShowOnSpot ->
                updateState { copy(showBrandModelOnSpot = intent.enabled) }
            is VehicleRegistrationIntent.LoadVehicle -> loadVehicle(intent.vehicleId)
            is VehicleRegistrationIntent.Save -> saveVehicle()
            is VehicleRegistrationIntent.DeleteVehicle -> deleteVehicle()
            is VehicleRegistrationIntent.NavigateBack ->
                sendEffect(VehicleRegistrationEffect.NavigateBack)
        }
    }

    /**
     * Runs the carbody inference only for a type that HAS a body ([VehicleType.hasCarbody]). For
     * the rest we never have a carbody, and a blank brand+model pair short-circuits to null so the
     * UI doesn't flash a stale selection. A null type has not been chosen yet and is left open —
     * this screen's brand/model fields imply a car, and the type picker rewrites the answer.
     *
     * When the user types a brand/model the catalog can't recognise (free-text
     * path), inference falls back to [DEFAULT_CAR_CARBODY] instead of null so the
     * form is never blocked — the user can refine it via the manual carbody
     * picker (the card always exposes a "change" affordance). [VEH-FREETEXT-001]
     *
     * [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001] Was `inferIfCar`, asking `!= CAR`.
     * The name and the question both said "car" where they meant "has a body".
     */
    private fun inferCarbody(type: VehicleType?, brand: String, model: String): CarbodyType? {
        if (type != null && !type.hasCarbody) return null
        if (brand.isBlank() && model.isBlank()) return null
        return VehicleCatalog.inferBodyType(brand, model) ?: DEFAULT_CAR_CARBODY
    }

    /**
     * Resolves the size dimension that gets persisted:
     *  - a type with no carbody is always [VehicleSize.MOTORCYCLE]
     *  - a bodied type with a known carbody uses [CarbodyType.sizeCategory]
     *  - a bodied type without an inferred carbody returns null so the form stays gated
     */
    private fun resolveSize(type: VehicleType?, body: CarbodyType?): VehicleSize? = when {
        type != null && !type.hasCarbody -> VehicleSize.MOTORCYCLE
        body != null -> body.sizeCategory
        else -> null
    }

    private fun loadVehicle(vehicleId: String) {
        viewModelScope.launch {
            runCatching {
                val vehicle = vehicleRepository.observeVehicles()
                    .first { list -> list.any { it.id == vehicleId } }
                    .first { it.id == vehicleId }
                val catalogBrands = VehicleCatalog.brands()
                val brandInCatalog = vehicle.brand != null && vehicle.brand in catalogBrands
                val modelsForBrand = if (brandInCatalog)
                    VehicleCatalog.modelsFor(vehicle.brand) else emptyList()
                val modelInCatalog = vehicle.model != null && vehicle.model in modelsForBrand
                // Detect a divergence between the stored body and what the catalog would
                // infer right now — surfaces the "manual override" badge so the user
                // remembers their own pick instead of seeing a silent "auto" label.
                val inferredForStored = vehicle.brand?.let { brand ->
                    vehicle.model?.let { model -> VehicleCatalog.inferBodyType(brand, model) }
                }
                val isManualOverride = vehicle.carbodyType != null &&
                        inferredForStored != null &&
                        inferredForStored != vehicle.carbodyType
                updateState {
                    copy(
                        editingVehicleId = vehicle.id,
                        name = vehicle.name ?: "",
                        brand = vehicle.brand ?: "",
                        isBrandOther = vehicle.brand != null && !brandInCatalog,
                        model = vehicle.model ?: "",
                        isModelOther = vehicle.model != null && !modelInCatalog,
                        carbodyType = vehicle.carbodyType,
                        sizeCategory = vehicle.sizeCategory,
                        isCarbodyManualOverride = isManualOverride,
                        vehicleType = vehicle.vehicleType,
                        showBrandModelOnSpot = vehicle.showBrandModelOnSpot,
                        licensePlate = vehicle.licensePlate ?: "",
                        color = vehicle.color,
                        fuelType = vehicle.fuelType,
                        // An override shows as text; a null shows blank so the DEFAULT placeholder
                        // stays visible and honest. [VEH-FUEL-001]
                        consumptionText = vehicle.consumptionPer100?.let {
                            consumptionToField(it, usesLiters = vehicle.fuelType?.usesLiters != false)
                        } ?: "",
                        consumptionElectricText = vehicle.consumptionElectricPer100?.let {
                            consumptionToField(it, usesLiters = false)
                        } ?: "",
                        fuelPriceText = vehicle.fuelPricePerLiter?.let { fuelPriceToField(it.amount) } ?: "",
                        energyPriceText = vehicle.energyPricePerKwh?.let { formatConsumption(it.amount.toFloat()) } ?: "",
                        energyLabel = vehicle.energyLabel,
                    )
                }
                // What deleting this car would cost, read AFTER the form is on screen: the warning
                // and the block need it, the rest of the screen doesn't wait for it.
                // [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001]
                val footprint = vehicleRepository.getParkingFootprint(vehicle.id)
                updateState { copy(parkingFootprint = footprint) }
            }.onFailure { e ->
                PaparcarLogger.e(TAG, "Failed to load vehicle", e)
                sendEffect(VehicleRegistrationEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun saveVehicle() {
        val current = state.value
        if (current.isSaving) {
            PaparcarLogger.d(TAG, "saveVehicle ignored — already saving")
            return
        }
        val size = current.sizeCategory ?: run {
            sendEffect(VehicleRegistrationEffect.ShowError(PaparcarError.Vehicle.SaveFailed))
            return
        }
        // Silent CAR default for safety — UI requires a pick (canSubmit gate),
        // so this only triggers on programmatic save paths. [BUG-SCOOTER-001]
        val type = current.vehicleType ?: VehicleType.CAR
        // Carbody is required for a bodied type (canSubmit enforces it); the rest intentionally
        // persist null. [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001]
        val body = if (type.hasCarbody) current.carbodyType else null
        // name is required when both brand and model are blank — persist placeholder if that slips through
        val resolvedName = current.name.trim().ifBlank {
            if (current.brand.isBlank() && current.model.isBlank()) "Car ${current.defaultNamePlaceholderIndex}" else null
        }
        val isEditing = current.editingVehicleId != null
        val vehicleId = current.editingVehicleId
            ?: current.pendingNewVehicleId
            ?: Uuid.random().toString()
        updateState { copy(isSaving = true, pendingNewVehicleId = if (isEditing) null else vehicleId) }
        viewModelScope.launch {
            runCatching {
                val userId = authRepository.getCurrentSession()?.userId ?: ""
                // The form doesn't track these on-vehicle fields, so they must be
                // read from the existing row before save — otherwise the constructor
                // defaults (null / false) silently overwrite them in Room AND Firestore.
                // [BUG-NEW-VEHICLE-DEFAULT] covers isActive; [ARCH-MONITORING-002]
                // covers bluetoothDeviceId — pairing via BluetoothConfigViewModel only
                // touches its own field, so the form save must not wipe it.
                val existing = if (isEditing) vehicleRepository.getVehicleById(userId, vehicleId) else null
                // [AUDIT-M11-001] Single-active invariant decision lives in the domain policy.
                val shouldBeDefault = VehicleActiveStatePolicy.shouldBeActiveOnSave(
                    isEditing = isEditing,
                    existingIsActive = existing?.isActive ?: false,
                    userHasVehicles = vehicleRepository.hasVehicles(userId),
                )
                val vehicle = Vehicle(
                    id = vehicleId,
                    userId = userId,
                    name = resolvedName,
                    brand = current.brand.trim().ifBlank { null },
                    model = current.model.trim().ifBlank { null },
                    sizeCategory = size,
                    carbodyType = body,
                    vehicleType = type,
                    bluetoothDeviceId = existing?.bluetoothDeviceId,
                    showBrandModelOnSpot = current.showBrandModelOnSpot,
                    isActive = shouldBeDefault,
                    licensePlate = current.licensePlate.trim().ifBlank { null },
                    color = current.color,
                    // The fuel block only exists for a burning type; a typed value is an override
                    // even when it equals the default (the user ANSWERED). [VEH-FUEL-001]
                    fuelType = if (type.burnsFuel) current.fuelType else null,
                    consumptionPer100 = if (type.burnsFuel) {
                        consumptionFromField(
                            text = current.consumptionText,
                            usesLiters = current.fuelType?.usesLiters != false,
                        )
                    } else {
                        null
                    },
                    consumptionElectricPer100 =
                        if (type.burnsFuel && current.fuelType?.hasSecondaryElectric == true) {
                            consumptionFromField(current.consumptionElectricText, usesLiters = false)
                        } else {
                            null
                        },
                    // A price is only stored with the currency it was typed in; without one the
                    // field was never offered. [FUEL-PRICE-RESOLVER-001]
                    fuelPricePerLiter = current.priceCurrency?.let { c ->
                        if (type.burnsFuel && current.fuelType?.usesLiters == true) {
                            fuelPriceFromField(current.fuelPriceText)?.let { Money(it, c) }
                        } else {
                            null
                        }
                    },
                    energyPricePerKwh = current.priceCurrency?.let { c ->
                        if (type.burnsFuel && current.fuelType?.usesElectricity == true) {
                            parseConsumption(current.energyPriceText)?.let { Money(it.toDouble(), c) }
                        } else {
                            null
                        }
                    },
                    energyLabel = if (type.burnsFuel) current.energyLabel else null,
                )
                vehicleRepository.saveVehicle(vehicle).getOrThrow()
                if (!isEditing && shouldBeDefault) vehicleRepository.setActiveVehicle(vehicle.id).getOrThrow()
            }.onSuccess {
                updateState { copy(isSaving = false, pendingNewVehicleId = null) }
                sendEffect(
                    VehicleRegistrationEffect.SavedSuccessfully(
                        vehicleId = vehicleId,
                        isNewVehicle = !isEditing,
                    ),
                )
            }.onFailure { e ->
                PaparcarLogger.e(TAG, "Failed to save vehicle", e)
                updateState { copy(isSaving = false) }
                sendEffect(VehicleRegistrationEffect.ShowError(PaparcarError.Vehicle.SaveFailed))
            }
        }
    }

    private fun deleteVehicle() {
        val vehicleId = state.value.editingVehicleId ?: return
        if (state.value.isDeleting) return
        updateState { copy(isDeleting = true) }
        viewModelScope.launch {
            vehicleRepository.deleteVehicle(vehicleId)
                .onSuccess { sendEffect(VehicleRegistrationEffect.NavigateBack) }
                .onFailure { e ->
                    updateState { copy(isDeleting = false) }
                    // A car that is parked isn't a failure to report as one: the repository refused
                    // on purpose and the user is told what to do about it. Only a real breakage is
                    // logged as an error. [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001]
                    if (e is PaparcarError.Vehicle.DeleteBlockedByActiveParking) {
                        // The button should already be blocked; reaching here means the parking
                        // started while the screen was open, so re-read what the screen believes.
                        val footprint = vehicleRepository.getParkingFootprint(vehicleId)
                        updateState { copy(parkingFootprint = footprint) }
                        sendEffect(VehicleRegistrationEffect.ShowError(e))
                    } else {
                        PaparcarLogger.e(TAG, "Failed to delete vehicle", e)
                        sendEffect(VehicleRegistrationEffect.ShowError(PaparcarError.Vehicle.DeleteFailed))
                    }
                }
        }
    }

    /** Locale-tolerant parse of a consumption field: comma or dot, must be a positive number.
     *  Anything else (blank, junk, zero, negative) means "no override" → the default applies. */
    private fun parseConsumption(text: String): Float? =
        text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }

    /** Renders a stored override back into the field without a trailing ".0" for whole numbers. */
    private fun formatConsumption(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

    // ─────────────────────────────────────────────────────────────────────────
    // El consumo, entre la unidad del modelo y la unidad del campo.
    // [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
    //
    // ⛔ Las dos direcciones van SIEMPRE en pareja. Con el ajuste en millas el campo dice mpg, así
    // que leer el consumo guardado sin convertir enseñaría «5,5» bajo una etiqueta que pone mpg, y
    // guardar sin convertir metería 43 en un campo que el resto de la app lee como l/100 km — un
    // coche que gasta 43 litros a los cien. Cada `formatConsumption` de una CIFRA DE CONSUMO tiene
    // su `fromDisplay` al guardar.
    //
    // ⛔ Y el PRECIO también pasa por aquí, aunque sea dinero: lleva denominador de volumen, y donde
    // el surtidor cobra por galón el campo pide galones. Esa frontera es otra —la manda el LOCALE, no
    // el ajuste de millas— y por eso tiene su propia pareja de funciones, no una rama de éstas.
    // [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
    // ─────────────────────────────────────────────────────────────────────────

    /** El sistema en el que el usuario teclea ahora mismo. */
    private fun displayUnit(): DistanceUnit =
        if (appPreferences.useImperialUnits) DistanceUnit.IMPERIAL else DistanceUnit.METRIC

    /** Consumo guardado (l/100 km · kWh/100 km) → el texto que se le enseña en su campo. */
    private fun consumptionToField(per100Km: Float, usesLiters: Boolean): String = formatConsumption(
        roundToTenth(consumptionToDisplay(per100Km, usesLiters, displayUnit(), localeGallonKind())),
    )

    /** Lo que quedó escrito en el campo → la unidad del modelo, o null si no es un número válido. */
    private fun consumptionFromField(text: String, usesLiters: Boolean): Float? =
        parseConsumption(text)?.let {
            consumptionFromDisplay(it, usesLiters, displayUnit(), localeGallonKind())
        }

    /**
     * Precio guardado (siempre €/litro) → el texto del campo, en la unidad del surtidor.
     *
     * Tres decimales, que es la precisión a la que se cotiza el combustible en las dos unidades
     * («1,879 €/l», «$3,499/gal»). Ese redondeo hace la ida y vuelta un punto FIJO: el primer
     * guardado desplaza el precio en una milésima de céntimo por litro y a partir de ahí no se mueve
     * nunca más. Guardar el texto tecleado en vez del número sería meter la unidad de presentación
     * dentro del modelo, que es justo lo que este borde existe para evitar.
     */
    private fun fuelPriceToField(perLitre: Double): String =
        formatPumpAmount(pumpPriceToDisplay(perLitre, pumpGallon()))

    /**
     * Lo que quedó escrito en el campo → €/litro, o null si no es un número válido.
     *
     * Parsea a **Double**, no a Float como el consumo: `Money.amount` es Double, y pasar un precio
     * por Float le mete un error de 5·10⁻⁸ que convertía «1,879» en 1,878999948501587 antes de que
     * nadie lo multiplicara por nada. Con una conversión de unidad delante, esa basura ya no es sólo
     * fea. [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
     */
    private fun fuelPriceFromField(text: String): Double? =
        parsePrice(text)?.let { pumpPriceFromDisplay(it, pumpGallon()) }

    /** Mismo criterio que [parseConsumption] —coma o punto, positivo— pero sin pasar por Float. */
    private fun parsePrice(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

    /** Un precio como se teclea: sin ceros de relleno, pero sin perder la milésima. */
    private fun formatPumpAmount(value: Double): String {
        val rounded = round(value * PRICE_SCALE) / PRICE_SCALE
        return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() else rounded.toString()
    }

    private companion object {
        const val TAG = "VehicleRegistrationVM"

        /** Milésimas: la precisión a la que se cotiza un combustible, aquí y en el surtidor. */
        const val PRICE_SCALE = 1_000.0

        /**
         * Body type assumed for a CAR whose typed brand/model matches neither the
         * catalog nor any keyword pattern. The most common segment (compact
         * hatchback) — pre-selected but always editable via the manual picker.
         */
        val DEFAULT_CAR_CARBODY = CarbodyType.HATCHBACK_MEDIUM
    }
}
