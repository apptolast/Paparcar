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
    /**
     * Código postal. **Routing data, como [province]** — y la clave MENOS frágil de las tres.
     * [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
     *
     * Los ids de provincia del ministerio son los códigos INE, que son por construcción los **dos
     * primeros dígitos del código postal español**: `postalCode.take(2)` ES el `IDProvincia`. Las
     * otras dos claves emparejan NOMBRES y por eso llevan dos tickets de historia —«Andalucía» no es
     * una provincia, «Puerto de Santa María (El)» no se escribe como lo escribe un geocoder—;
     * ésta no empareja nada.
     *
     * 🔑 Y es la única que existe en Ceuta y Melilla: medido en device, ahí no llega `subAdminArea`,
     * así que el camino por nombre se queda literalmente sin provincia que buscar.
     *
     * ⛔ Viaja entero como todo lo demás. El geocoder de plataforma lo rellenaba desde el principio
     * y lo tirábamos en la frontera del dominio, que es el mismo hueco por el que se cayó
     * [countryCode]. [FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001]
     */
    val postalCode: String? = null,
) {
    /**
     * No nombra nada: la forma que toma una búsqueda que no contestó, no un sitio.
     * [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
     *
     * ⛔ **Aquí y en un solo sitio.** Esta pregunta estaba escrita TRES veces —`addressOrNull`,
     * `SpotDtoMapper` y `ReportSpotWorker`—, cada una enumerando a mano los campos que a alguien le
     * parecieron los importantes, y las tres discrepaban: la de las sesiones aparcadas se dejaba
     * fuera `province`, así que una dirección que solo tuviera provincia se leía como «sin
     * dirección» y se tiraba. Es exactamente el error que
     * [FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001] describe: *la enumeración se convierte
     * calladamente en la definición*.
     *
     * Lee TODOS los campos por construcción, así que un campo nuevo entra solo.
     */
    val isBlank: Boolean
        get() = listOf(street, city, region, country, countryCode, province, postalCode)
            .all { it.isNullOrBlank() }

    val displayLine: String?
        get() = street ?: city ?: region ?: country

    val fullAddress: String
        get() = listOfNotNull(street, city, region, country).joinToString(", ")
}
