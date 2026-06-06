package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.AddressAndPlace
import kotlinx.coroutines.flow.Flow

interface AddressAndPlaceRepository {
    fun getAddressAndPlace(lat: Double, lon: Double): Flow<AddressAndPlace>
}
