package com.rndeveloper.paparcar.domain.geocoder

import com.rndeveloper.paparcar.domain.model.AddressInfo

/**
 * The lookup could not be asked — not "there is nothing here".
 * [FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001]
 */
class GeocodeUnanswered(message: String) : Exception(message)

/**
 * The ONE place that decides whether a reverse geocode answered. Both platform data sources end
 * here, and that is the whole point: the same lie was written twice — Android
 * (`resume(null)` on `onError`) and iOS (`resume(null)` on `NSError`) — and both then manufactured
 * `AddressInfo(null, null, null, null)` and returned it as a SUCCESS.
 * [FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001]
 *
 * ⛔ **An address with nothing in it is not an address.** It is the shape a mute lookup takes, and
 * downstream it is indistinguishable from a real place — which is how a phone in Spain, whose
 * geocoder had not woken up yet, produced `countryCode = ""`, routed past the Ministry's feed and
 * told the user that Spain does not publish fuel prices.
 *
 * What the consumers were already written to expect, and could never receive:
 * - `AddressAndPlaceRepositoryImpl` treats a null Phase-1 answer as *"offline / GMS mute"* and
 *   borrows the nearest cached street for it ([GEO-CACHE-ANSWERS-NEARBY-001]). That whole mechanism
 *   was unreachable.
 * - `GetNearbyFuelStationsUseCase` / `GetNearbyPublicParkingsUseCase` read `countryCode` to route.
 *
 * ⚠️ The blank case is folded in ON PURPOSE rather than trusted to the platforms: a geocoder that
 * answers with an empty `Address` is the same failure wearing different clothes, and a rule that
 * only covers the way it happened once is the rule that lets it come back the other way.
 */
fun geocodeAnswer(address: AddressInfo?): Result<AddressInfo> = when {
    address == null -> Result.failure(GeocodeUnanswered("The geocoder returned no address"))
    address.isBlank -> Result.failure(GeocodeUnanswered("The geocoder returned an empty address"))
    else -> Result.success(address)
}

/**
 * Nothing that names a place. Deliberately reads EVERY field rather than a chosen few: the
 * enumeration of "the fields that matter" is exactly what let `countryCode` fall out of four write
 * paths in [FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001].
 */
private val AddressInfo.isBlank: Boolean
    get() = listOf(street, city, region, country, countryCode, province)
        .all { it.isNullOrBlank() }
