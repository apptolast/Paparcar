package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.model.AddressInfo

class StubGeocoderDataSource : GeocoderDataSource {

    override suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo> =
        Result.success(AddressInfo(street = null, city = null, region = null, country = null))
}
