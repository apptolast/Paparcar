package com.rndeveloper.paparcar.presentation.licenses

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rndeveloper.paparcar.presentation.preview.FakeData
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme

private val loaded = LicensesState(
    isLoading = false,
    libraries = FakeData.openSourceLibraries,
    licenses = FakeData.openSourceLicenses,
)

@Preview(name = "Licencias — lista · Claro", showBackground = true)
@Composable
private fun LicensesListLightPreview() {
    PaparcarTheme(darkTheme = false) { LicensesContent(state = loaded) }
}

@Preview(
    name = "Licencias — lista · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun LicensesListDarkPreview() {
    PaparcarTheme(darkTheme = true) { LicensesContent(state = loaded) }
}

@Preview(name = "Licencias — cargando", showBackground = true)
@Composable
private fun LicensesLoadingPreview() {
    PaparcarTheme(darkTheme = false) { LicensesContent(state = LicensesState(isLoading = true)) }
}

/** El APK salió sin el fichero generado: la fila no puede quedarse girando para siempre. */
@Preview(name = "Licencias — no se pudo leer", showBackground = true)
@Composable
private fun LicensesFailedPreview() {
    PaparcarTheme(darkTheme = false) {
        LicensesContent(state = LicensesState(isLoading = false, failedToLoad = true))
    }
}

@Preview(name = "Licencia — texto completo · Claro", showBackground = true)
@Composable
private fun LicenseDetailTextLightPreview() {
    PaparcarTheme(darkTheme = false) {
        LicenseDetailContent(state = loaded, licenseId = FakeData.licenseApache.id)
    }
}

@Preview(
    name = "Licencia — texto completo · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun LicenseDetailTextDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        LicenseDetailContent(state = loaded, licenseId = FakeData.licenseApache.id)
    }
}

/** Términos propietarios: no se distribuyen, se enlazan. */
@Preview(name = "Licencia — solo enlace", showBackground = true)
@Composable
private fun LicenseDetailLinkOnlyPreview() {
    PaparcarTheme(darkTheme = false) {
        LicenseDetailContent(state = loaded, licenseId = FakeData.licenseTerms.id)
    }
}
