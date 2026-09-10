package com.rndeveloper.paparcar.domain.onboarding

/**
 * A nearby subject whose PROVENANCE has to be said once, the first time the user asks for it.
 * [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
 *
 * Paparcar answers three questions and only ever presented one. The other two are not the
 * community's: they come from third parties (OpenStreetMap, the ministries and price feeds), and
 * data that comes from somewhere else has to say where — and what it does NOT promise — before
 * anyone drives on it. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
 *
 * ⛔ **Free spots are NOT here, and the absence is the statement.** The community spot is the
 * product: nobody has to be told where it came from, because the user IS where it comes from. A
 * third entry for it would turn this into "the list of lanes", which is
 * `com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens`'s job, one layer up.
 *
 * Lives in domain rather than beside the lens because what is remembered — *this user has been told*
 * — is a fact about the USER, persisted through `AppPreferences`, which cannot see presentation.
 */
enum class NearbyComplement {
    /** Public car parks, from OpenStreetMap plus the municipal feeds. [PARKINGS-ON-THE-MAP-001] */
    CAR_PARKS,

    /** Fuel stations and the price each source published. [FUEL-STATIONS-LIST-001] */
    FUEL,
}
