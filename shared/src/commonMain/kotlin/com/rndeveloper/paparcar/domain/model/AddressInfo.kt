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
     * ⛔ Deliberately NOT persisted (geocoder cache, parking/spot entities, workData): no persisted
     * consumer reads it, and there a null is honest — "not captured", never a claim. A reader that
     * needs it must take it from a live geocode, like the fuel lookup does.
     */
    val province: String? = null,
) {
    val displayLine: String?
        get() = street ?: city ?: region ?: country

    val fullAddress: String
        get() = listOfNotNull(street, city, region, country).joinToString(", ")
}
