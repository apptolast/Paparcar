package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.LocationInfo
import kotlinx.coroutines.flow.Flow

interface LocationInfoRepository {
    fun getLocationInfo(lat: Double, lon: Double): Flow<LocationInfo>
}
