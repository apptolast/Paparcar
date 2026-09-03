package com.rndeveloper.paparcar.presentation.licenses

sealed class LicensesIntent {
    data object NavigateBack : LicensesIntent()

    /** A library row was tapped — open the text of the licence it ships under. */
    data class OpenLicense(val licenseId: String) : LicensesIntent()

    /** The licence is proprietary terms we cannot redistribute: send the user to read them. */
    data class OpenUrl(val url: String) : LicensesIntent()
}
