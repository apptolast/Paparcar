package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.geocoder.GeocoderPort
import io.apptolast.paparcar.domain.model.AddressInfo

class StubGeocoderPort : GeocoderPort {
    override suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo> =
        Result.success(AddressInfo(street = null, city = null, region = null, country = null))
}
