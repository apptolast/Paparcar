package com.rndeveloper.paparcar.domain.model

data class AddressInfo(
    val street: String?,
    val city: String?,
    val region: String?,
    val country: String?,
    val countryCode: String? = null,
    /**
     * Administrative level BELOW [region]: Spain's province ("Madrid") where [region] is the
     * autonomous community ("Comunidad de Madrid"). Routing data, not display data — Spain's fuel
     * feed is queried by province, and feeding it the community left the whole country silently
     * unpriced. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
     *
     * ⛔ **Persisted since v6, and the reversal is the point.** [FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001]
     *
     * It used to be deliberately left out of the geocoder cache, the parking/spot entities and
     * workData, on the argument that no persisted consumer read it. That argument was correct and
     * still cost us: [countryCode] fell out of four write paths for the same reason nobody could
     * state — each of them enumerated the fields somebody thought mattered, and the enumeration
     * quietly became the definition. A spend figure then failed to render in every country on
     * earth, with no error anywhere.
     *
     * So the rule is now simpler than any argument about consumers: **an address travels whole.**
     * There is no field-by-field judgement call left to get wrong, and no exemption list for the
     * next reader to inherit.
     */
    val province: String? = null,
) {
    val displayLine: String?
        get() = street ?: city ?: region ?: country

    val fullAddress: String
        get() = listOfNotNull(street, city, region, country).joinToString(", ")
}
