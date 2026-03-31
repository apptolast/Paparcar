package io.apptolast.paparcar.domain.model

data class AddressInfo(
    val street: String?,
    val city: String?,
    val region: String?,
    val country: String?,
) {
    val displayLine: String?
        get() = street ?: city ?: region ?: country

    val fullAddress: String
        get() = listOfNotNull(street, city, region, country).joinToString(", ")
}
