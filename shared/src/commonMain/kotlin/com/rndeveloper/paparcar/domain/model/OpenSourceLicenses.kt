package com.rndeveloper.paparcar.domain.model

/**
 * The open source attribution the app ships with. [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001]
 *
 * These are domain types on purpose, not the parser's own: the list is generated at build time from
 * the Gradle dependency graph, and whichever tool reads that file must not leak into the screens.
 */
data class OpenSourceAttribution(
    val libraries: List<OpenSourceLibrary>,
    val licenses: List<OpenSourceLicense>,
) {
    /** Distinct licences actually referenced by a library, in the order the list shows them. */
    fun licenseFor(id: String): OpenSourceLicense? = licenses.firstOrNull { it.id == id }
}

data class OpenSourceLibrary(
    /** Maven coordinates without the version — `androidx.room:room-runtime`. Stable list key. */
    val id: String,
    val name: String,
    val version: String?,
    val website: String?,
    /**
     * Ids into [OpenSourceAttribution.licenses]. Empty is a bug, not a state: the build fails before
     * shipping a library with no declared licence (`aboutLibraries { library { requireLicense } }`).
     */
    val licenseIds: List<String>,
)

data class OpenSourceLicense(
    /** SPDX id where there is one (`Apache-2.0`, `MIT`), else the generator's hash. URL-safe. */
    val id: String,
    val name: String,
    val url: String?,
    /**
     * The full licence text, when it can be distributed. Null for the proprietary terms of service
     * that some Google artifacts declare: those are not redistributable, so the app links them
     * instead of copying them — which is what [url] is for.
     */
    val text: String?,
)
