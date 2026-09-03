package com.rndeveloper.paparcar.presentation.licenses

sealed class LicensesEffect {
    data object NavigateBack : LicensesEffect()
    data class NavigateToLicense(val licenseId: String) : LicensesEffect()
    data class OpenUrl(val url: String) : LicensesEffect()
}
