package com.rndeveloper.paparcar.data.repository

import com.mikepenz.aboutlibraries.Libs
import com.rndeveloper.paparcar.domain.model.OpenSourceAttribution
import com.rndeveloper.paparcar.domain.model.OpenSourceLibrary
import com.rndeveloper.paparcar.domain.model.OpenSourceLicense
import com.rndeveloper.paparcar.domain.repository.OpenSourceLicenseRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import paparcar.composeapp.generated.resources.Res

/**
 * Reads the attribution file the build generates from the Gradle dependency graph.
 * [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001]
 *
 * The file is NOT in the source tree: `:shared`'s build regenerates it and hands the directory to
 * Compose Resources (`compose.resources { customDirectory(...) }`). That is the whole point — a
 * hand-kept list goes stale the first time someone bumps a version, silently, and a licence screen
 * that lies is worse than no screen.
 */
class OpenSourceLicenseRepositoryImpl : OpenSourceLicenseRepository {

    /** Bundled data cannot change while the app runs, and both screens ask for it. */
    private val cacheLock = Mutex()
    private var cached: OpenSourceAttribution? = null

    override suspend fun loadAttribution(): Result<OpenSourceAttribution> = cacheLock.withLock {
        cached?.let { return@withLock Result.success(it) }
        parseAttribution().onSuccess { cached = it }
    }

    private suspend fun parseAttribution(): Result<OpenSourceAttribution> = runCatching {
        val json = Res.readBytes(ATTRIBUTION_PATH).decodeToString()
        val libs = Libs.Builder().withJson(json).build()

        // Only licences some library actually points at: the generator emits its whole map.
        val licenses = libs.licenses.map { license ->
            OpenSourceLicense(
                id = license.hash,
                name = license.name,
                url = license.url?.takeIf { it.isNotBlank() },
                text = license.licenseContent?.takeIf { it.isNotBlank() },
            )
        }.sortedBy { it.name.lowercase() }

        OpenSourceAttribution(
            libraries = libs.libraries
                .map { library ->
                    OpenSourceLibrary(
                        id = library.uniqueId,
                        // Some POMs name themselves by their coordinates, and a few leave it blank.
                        name = library.name.takeIf { it.isNotBlank() } ?: library.uniqueId,
                        version = library.artifactVersion?.takeIf { it.isNotBlank() },
                        website = library.website?.takeIf { it.isNotBlank() },
                        licenseIds = library.licenses.map { it.hash },
                    )
                }
                .sortedBy { it.name.lowercase() },
            licenses = licenses,
        )
    }.onFailure { e ->
        PaparcarLogger.e(TAG, "Failed to read the bundled open source attribution", e)
    }

    private companion object {
        const val TAG = "OpenSourceLicenseRepository"

        /** `files/` is the Compose Resources convention for raw assets. */
        const val ATTRIBUTION_PATH = "files/aboutlibraries.json"
    }
}
