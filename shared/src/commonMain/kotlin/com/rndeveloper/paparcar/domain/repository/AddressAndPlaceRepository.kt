package com.rndeveloper.paparcar.domain.repository

import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import kotlinx.coroutines.flow.Flow

interface AddressAndPlaceRepository {
    fun getAddressAndPlace(lat: Double, lon: Double): Flow<AddressAndPlace>
}
