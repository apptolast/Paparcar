package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.LocationInfo
import io.apptolast.paparcar.domain.repository.LocationInfoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeLocationInfoRepository(
    initialInfo: LocationInfo = LocationInfo(
        address = AddressInfo(null, null, null, null),
        placeInfo = null,
    ),
) : LocationInfoRepository {

    /** Mutate per-test to control what address/place the repository returns. */
    var addressResult: Result<AddressInfo> = Result.success(initialInfo.address)
    var placeInfo: io.apptolast.paparcar.domain.model.PlaceInfo? = initialInfo.placeInfo

    override fun getLocationInfo(lat: Double, lon: Double): Flow<LocationInfo> = flow {
        val address = addressResult.getOrElse { AddressInfo(null, null, null, null) }
        emit(LocationInfo(address = address, placeInfo = null))
        if (placeInfo != null) emit(LocationInfo(address = address, placeInfo = placeInfo))
    }
}
