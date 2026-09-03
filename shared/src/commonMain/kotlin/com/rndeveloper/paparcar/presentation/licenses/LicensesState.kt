package com.rndeveloper.paparcar.presentation.licenses

import com.rndeveloper.paparcar.domain.model.OpenSourceLibrary
import com.rndeveloper.paparcar.domain.model.OpenSourceLicense

/**
 * Serves BOTH screens of the attribution flow: the library list and one licence's text. They read
 * the same generated file, so splitting the state would only mean parsing it twice and inventing a
 * second way for the two to disagree. [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001]
 */
data class LicensesState(
    val isLoading: Boolean = true,
    val libraries: List<OpenSourceLibrary> = emptyList(),
    val licenses: List<OpenSourceLicense> = emptyList(),
    /** The attribution file could not be read. Never expected — the build generates it. */
    val failedToLoad: Boolean = false,
) {
    fun license(id: String): OpenSourceLicense? = licenses.firstOrNull { it.id == id }

    /** The licence names of a library, resolved for display. */
    fun licenseNames(library: OpenSourceLibrary): List<String> =
        library.licenseIds.mapNotNull { license(it)?.name }
}
