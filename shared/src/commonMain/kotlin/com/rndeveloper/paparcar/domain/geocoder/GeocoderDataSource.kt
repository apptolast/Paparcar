package com.rndeveloper.paparcar.domain.geocoder

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.SearchResult

interface GeocoderDataSource {

    /**
     * Where this point is, as the platform names it.
     *
     * ⛔ **`failure` when the lookup could not be asked; `success` only when it answered.** Written
     * down because it was not, and both callers then invented the contract for themselves — each
     * correctly, while the implementations returned a blank `AddressInfo` as a success and made both
     * of them wrong. [FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001]
     *
     * A partial answer is still an answer: a street with no province is normal, and the caller that
     * needs the province says so itself. What must never come back as a success is an
     * [AddressInfo] with nothing in it at all — see [geocodeAnswer], where both platforms decide it.
     */
    suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo>

    suspend fun searchByName(query: String, maxResults: Int = 5): Result<List<SearchResult>>
}
