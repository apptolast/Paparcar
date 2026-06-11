package io.apptolast.paparcar.presentation.vehicleregistration

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.presentation.base.BaseViewModel
import io.apptolast.paparcar.presentation.vehicleregistration.data.VehicleCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class VehicleRegistrationViewModel(
    private val vehicleRepository: VehicleRepository,
    private val authRepository: AuthRepository,
) : BaseViewModel<VehicleRegistrationState, VehicleRegistrationIntent, VehicleRegistrationEffect>() {

    override fun initState(): VehicleRegistrationState = VehicleRegistrationState()

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
                val inferred = inferIfCar(vehicleType, intent.value, nextModel)
                copy(
                    brand = intent.value,
                    isBrandOther = true,
                    model = nextModel,
                    isModelOther = if (wasCatalog) false else isModelOther,
                    carbodyType = inferred,
                    sizeCategory = resolveSize(vehicleType, inferred),
                    isCarbodyManualOverride = false,
                    hasInteractedWithForm = true,
                )
            }

            is VehicleRegistrationIntent.SelectModel -> updateState {
                val inferred = inferIfCar(vehicleType, brand, intent.model)
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
                    sizeCategory = if (vehicleType == VehicleType.CAR || vehicleType == null) null else VehicleSize.MOTORCYCLE,
                    isCarbodyManualOverride = false,
                    hasInteractedWithForm = true,
                )
            }
            is VehicleRegistrationIntent.SetCustomModel -> updateState {
                // Typing in the model field always switches to "custom model" mode so the
                // canSubmit gate treats the value as user-supplied free text.
                val inferred = inferIfCar(vehicleType, brand, intent.value)
                copy(
                    model = intent.value,
                    isModelOther = true,
                    carbodyType = inferred,
                    sizeCategory = resolveSize(vehicleType, inferred),
                    isCarbodyManualOverride = false,
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
                if (newType == VehicleType.CAR) {
                    val inferred = inferIfCar(newType, brand, model)
                    copy(
                        vehicleType = newType,
                        carbodyType = inferred,
                        sizeCategory = resolveSize(newType, inferred),
                        isCarbodyManualOverride = false,
                        hasInteractedWithForm = true,
                    )
                } else {
                    // Motorcycles, scooters and bikes don't have a carbody — they always
                    // share the MOTORCYCLE size for the spot fit calculation.
                    copy(
                        vehicleType = newType,
                        carbodyType = null,
                        sizeCategory = VehicleSize.MOTORCYCLE,
                        isCarbodyManualOverride = false,
                        hasInteractedWithForm = true,
                    )
                }
            }
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
     * Runs the carbody inference only when the user is registering a CAR. For
     * other vehicle types we never have a carbody, and a blank brand+model
     * pair short-circuits to null so the UI doesn't flash a stale selection.
     */
    private fun inferIfCar(type: VehicleType?, brand: String, model: String): CarbodyType? {
        if (type != null && type != VehicleType.CAR) return null
        if (brand.isBlank() && model.isBlank()) return null
        return VehicleCatalog.inferBodyType(brand, model)
    }

    /**
     * Resolves the size dimension that gets persisted:
     *  - non-CAR vehicle types are always [VehicleSize.MOTORCYCLE]
     *  - CAR with a known carbody uses [CarbodyType.sizeCategory]
     *  - CAR without an inferred carbody returns null so the form stays gated
     */
    private fun resolveSize(type: VehicleType?, body: CarbodyType?): VehicleSize? = when {
        type != null && type != VehicleType.CAR -> VehicleSize.MOTORCYCLE
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
                    )
                }
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
        // Carbody is required for CAR (canSubmit enforces it). Non-CAR types
        // intentionally persist null.
        val body = if (type == VehicleType.CAR) current.carbodyType else null
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
                val shouldBeDefault = when {
                    isEditing -> existing?.isActive ?: false
                    else -> !vehicleRepository.hasVehicles(userId)
                }
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
                    PaparcarLogger.e(TAG, "Failed to delete vehicle", e)
                    updateState { copy(isDeleting = false) }
                    sendEffect(VehicleRegistrationEffect.ShowError(PaparcarError.Vehicle.DeleteFailed))
                }
        }
    }

    private companion object {
        const val TAG = "VehicleRegistrationVM"
    }
}
