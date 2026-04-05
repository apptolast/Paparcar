package io.apptolast.paparcar.presentation.vehicle

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class VehicleRegistrationViewModel(
    private val vehicleRepository: VehicleRepository,
    private val authRepository: AuthRepository,
    private val appPreferences: AppPreferences,
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
            is VehicleRegistrationIntent.Save -> saveVehicle()
            is VehicleRegistrationIntent.NavigateBack ->
                sendEffect(VehicleRegistrationEffect.NavigateBack)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun saveVehicle() {
        val current = state.value
        val size = current.sizeCategory ?: run {
            sendEffect(VehicleRegistrationEffect.ShowError(PaparcarError.Database.Unknown("size_required")))
            return
        }
        updateState { copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                val userId = authRepository.getCurrentSession()?.userId ?: ""
                val vehicle = Vehicle(
                    id = Uuid.random().toString(),
                    userId = userId,
                    brand = current.brand.trim().ifBlank { null },
                    model = current.model.trim().ifBlank { null },
                    sizeCategory = size,
                    showBrandModelOnSpot = current.showBrandModelOnSpot,
                    isDefault = true,
                )
                vehicleRepository.saveVehicle(vehicle)
                vehicleRepository.setDefaultVehicle(vehicle.id)
            }.onSuccess {
                appPreferences.setVehicleRegistered()
                updateState { copy(isSaving = false) }
                sendEffect(VehicleRegistrationEffect.SavedSuccessfully)
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