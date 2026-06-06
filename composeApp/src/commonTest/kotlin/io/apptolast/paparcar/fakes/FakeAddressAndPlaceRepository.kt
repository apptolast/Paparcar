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

    override fun getAddressAndPlace(lat: Double, lon: Double): Flow<AddressAndPlace> = flow {
        val address = addressResult.getOrElse { AddressInfo(null, null, null, null) }
        emit(AddressAndPlace(address = address, placeInfo = null))
        if (placeInfo != null) emit(AddressAndPlace(address = address, placeInfo = placeInfo))
    }
}
