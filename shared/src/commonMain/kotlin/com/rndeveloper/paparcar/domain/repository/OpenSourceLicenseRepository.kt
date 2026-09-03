package com.rndeveloper.paparcar.domain.repository

import com.rndeveloper.paparcar.domain.model.OpenSourceAttribution

/** Reads the attribution bundled with the app. [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001] */
interface OpenSourceLicenseRepository {
    /** One-shot: the data ships inside the APK, so there is nothing to observe. */
    suspend fun loadAttribution(): Result<OpenSourceAttribution>
}
