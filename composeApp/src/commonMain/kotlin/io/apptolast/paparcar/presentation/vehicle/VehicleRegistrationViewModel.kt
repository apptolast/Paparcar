package io.apptolast.paparcar.presentation.vehicle

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
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

    override fun handleIntent(intent: VehicleRegistrationIntent) {
        when (intent) {
            is VehicleRegistrationIntent.SetBrand ->
                updateState { copy(brand = intent.value) }
            is VehicleRegistrationIntent.SetModel ->
                updateState { copy(model = intent.value) }
            is VehicleRegistrationIntent.SetSize ->
                updateState { copy(sizeCategory = intent.size) }
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
                updateState {
                    copy(
                        editingVehicleId = vehicle.id,
                        brand = vehicle.brand ?: "",
                        model = vehicle.model ?: "",
                        sizeCategory = vehicle.sizeCategory,
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
        // Defensa en profundidad contra doble-tap: si ya hay un save en vuelo, ignorar.
        // La UI también desactiva el botón con isSaving=true (PapPrimaryButton lo respeta),
        // pero el guard del VM cubre el race entre intent dispatch y recomposición. [VEHICLES-002]
        if (current.isSaving) {
            PaparcarLogger.d(TAG, "saveVehicle ignored — already saving")
            return
        }
        val size = current.sizeCategory ?: run {
            sendEffect(VehicleRegistrationEffect.ShowError(PaparcarError.Database.Unknown("size_required")))
            return
        }
        val isEditing = current.editingVehicleId != null
        // Memoizar el id en el state al primer intento. Si el save falla y el usuario
        // reintenta, reusamos este mismo id → Firestore `.set()` sobreescribe el doc en
        // lugar de crear otro → no duplica. [VEHICLES-002]
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
                    brand = current.brand.trim().ifBlank { null },
                    model = current.model.trim().ifBlank { null },
                    sizeCategory = size,
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
                // Mantenemos pendingNewVehicleId para que el reintento reuse el mismo id.
                updateState { copy(isSaving = false) }
                sendEffect(VehicleRegistrationEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
        }
    }

    private companion object {
        const val TAG = "VehicleRegistrationVM"
    }
}