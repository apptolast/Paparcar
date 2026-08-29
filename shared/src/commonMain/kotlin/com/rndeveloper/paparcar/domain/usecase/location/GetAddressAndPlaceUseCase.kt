package com.rndeveloper.paparcar.domain.usecase.location

import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.repository.AddressAndPlaceRepository
import kotlinx.coroutines.flow.Flow

class GetAddressAndPlaceUseCase(private val repository: AddressAndPlaceRepository) {
    operator fun invoke(lat: Double, lon: Double): Flow<AddressAndPlace> =
        repository.getAddressAndPlace(lat, lon)
}
