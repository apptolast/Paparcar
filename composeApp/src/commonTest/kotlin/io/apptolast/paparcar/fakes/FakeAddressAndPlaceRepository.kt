package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.repository.AddressAndPlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeAddressAndPlaceRepository(
    initialInfo: AddressAndPlace = AddressAndPlace(
        address = AddressInfo(null, null, null, null),
        placeInfo = null,
    ),
) : AddressAndPlaceRepository {

    /** Mutate per-test to control what address/place the repository returns. */
    var addressResult: Result<AddressInfo> = Result.success(initialInfo.address)
    var placeInfo: io.apptolast.paparcar.domain.model.PlaceInfo? = initialInfo.placeInfo
    /** Delay before the first emission — lets tests race a slow geocode against re-asks. */
    var delayMs: Long = 0
    /** Every request, in order — lets tests assert cancellation/dedup behaviour. */
    val calls = mutableListOf<Pair<Double, Double>>()

    override fun getAddressAndPlace(lat: Double, lon: Double): Flow<AddressAndPlace> = flow {
        calls += lat to lon
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        val address = addressResult.getOrElse { AddressInfo(null, null, null, null) }
        emit(AddressAndPlace(address = address, placeInfo = null))
        if (placeInfo != null) emit(AddressAndPlace(address = address, placeInfo = placeInfo))
    }
}
