package io.apptolast.paparcar.presentation.vehicle

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.presentation.vehicle.data.VehicleCatalog
import io.apptolast.paparcar.presentation.base.BaseViewModel
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
                    // Reset model when brand changes
                    model = "",
                    isModelOther = false,
                    hasInteractedWithForm = true,
                )
            }
            is VehicleRegistrationIntent.SelectBrandOther -> updateState {
                copy(brand = "", isBrandOther = true, model = "", isModelOther = false, hasInteractedWithForm = true)
            }
            is VehicleRegistrationIntent.SetCustomBrand ->
                updateState { copy(brand = intent.value, hasInteractedWithForm = true) }

            is VehicleRegistrationIntent.SelectModel -> updateState {
                copy(model = intent.model, isModelOther = false, hasInteractedWithForm = true)
            }
            is VehicleRegistrationIntent.SelectModelOther -> updateState {
                copy(model = "", isModelOther = true, hasInteractedWithForm = true)
            }
            is VehicleRegistrationIntent.SetCustomModel ->
                updateState { copy(model = intent.value, hasInteractedWithForm = true) }

            is VehicleRegistrationIntent.SetSize ->
                updateState { copy(sizeCategory = intent.size, hasInteractedWithForm = true) }
            is VehicleRegistrationIntent.SetVehicleType ->
                updateState { copy(vehicleType = intent.type, hasInteractedWithForm = true) }
            is VehicleRegistrationIntent.SetShowOnSpot ->
                updateState { copy(showBrandModelOnSpot = intent.enabled) }
            is VehicleRegistrationIntent.LoadVehicle -> loadVehicle(intent.vehicleId)
            is VehicleRegistrationIntent.Save -> saveVehicle()
            is VehicleRegistrationIntent.NavigateBack ->
                sendEffect(VehicleRegistrationEffect.NavigateBack)
        }
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
                updateState {
                    copy(
                        editingVehicleId = vehicle.id,
                        name = vehicle.name ?: "",
                        brand = vehicle.brand ?: "",
                        isBrandOther = vehicle.brand != null && !brandInCatalog,
                        model = vehicle.model ?: "",
                        isModelOther = vehicle.model != null && !modelInCatalog,
                        sizeCategory = vehicle.sizeCategory,
                        vehicleType = vehicle.vehicleType,
                        showBrandModelOnSpot = vehicle.showBrandModelOnSpot,
                    )
                }
            }.onFailure { e ->
                PaparcarLogger.e(TAG, "Failed to load vehicle", e)
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
            sendEffect(VehicleRegistrationEffect.ShowError(PaparcarError.Database.Unknown("size_required")))
            return
        }
        // Silent CAR default for safety — UI requires a pick (canSubmit gate),
        // so this only triggers on programmatic save paths. [BUG-SCOOTER-001]
        val type = current.vehicleType ?: VehicleType.CAR
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
                val vehicle = Vehicle(
                    id = vehicleId,
                    userId = userId,
                    name = resolvedName,
                    brand = current.brand.trim().ifBlank { null },
                    model = current.model.trim().ifBlank { null },
                    sizeCategory = size,
                    vehicleType = type,
                    showBrandModelOnSpot = current.showBrandModelOnSpot,
                    isDefault = true,
                )
                vehicleRepository.saveVehicle(vehicle)
                if (!isEditing) vehicleRepository.setDefaultVehicle(vehicle.id)
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
                sendEffect(VehicleRegistrationEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
        }
    }

    private companion object {
        const val TAG = "VehicleRegistrationVM"
    }
}