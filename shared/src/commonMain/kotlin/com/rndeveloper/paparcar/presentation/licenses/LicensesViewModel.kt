package com.rndeveloper.paparcar.presentation.licenses

import com.rndeveloper.paparcar.domain.repository.OpenSourceLicenseRepository
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.launch

class LicensesViewModel(
    private val openSourceLicenseRepository: OpenSourceLicenseRepository,
) : BaseViewModel<LicensesState, LicensesIntent, LicensesEffect>() {

    override fun initState() = LicensesState()

    init {
        viewModelScope.launch {
            openSourceLicenseRepository.loadAttribution()
                .onSuccess { attribution ->
                    updateState {
                        copy(
                            isLoading = false,
                            libraries = attribution.libraries,
                            licenses = attribution.licenses,
                        )
                    }
                }
                .onFailure {
                    // The repository already logged the cause; the screen only needs to stop lying
                    // about loading. No snackbar: an empty attribution IS the error message.
                    updateState { copy(isLoading = false, failedToLoad = true) }
                }
        }
    }

    override fun handleIntent(intent: LicensesIntent) {
        when (intent) {
            is LicensesIntent.NavigateBack -> sendEffect(LicensesEffect.NavigateBack)
            is LicensesIntent.OpenLicense -> sendEffect(LicensesEffect.NavigateToLicense(intent.licenseId))
            is LicensesIntent.OpenUrl -> sendEffect(LicensesEffect.OpenUrl(intent.url))
        }
    }
}
