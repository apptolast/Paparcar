package com.rndeveloper.paparcar.domain.usecase.location

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.fakes.FakeAddressAndPlaceRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetAddressAndPlaceUseCaseTest {

    private val address = AddressInfo(street = "Calle Mayor", city = "Madrid", region = null, country = "ES")
    private val place = PlaceInfo(name = "El Corte Inglés", category = PlaceCategory.MALL)

    @Test
    fun `should_forward_repository_flow_to_caller`() = runTest {
        val info = AddressAndPlace(address, null)
        val repo = FakeAddressAndPlaceRepository(info)
        val useCase = GetAddressAndPlaceUseCase(repo)

        val result = useCase(40.416775, -3.703790).toList()

        // Two emissions for a place-less point, not one: the second says the POI question was
        // ASKED and came back empty, which is not the same as never having asked.
        // [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
        assertEquals(listOf(info, info.copy(poiChecked = true)), result)
    }

    @Test
    fun `should_pass_the_persisting_flag_through_to_the_repository`() = runTest {
        val repo = FakeAddressAndPlaceRepository(AddressAndPlace(address, null))
        val useCase = GetAddressAndPlaceUseCase(repo)

        useCase(40.416775, -3.703790).toList()
        useCase(40.416775, -3.703790, persisting = true).toList()

        // A use case that dropped the flag would look identical from every consumer's side, and the
        // cached "no place here" would go on answering for pins it never measured.
        assertEquals(listOf(false, true), repo.persistingCalls)
    }

    @Test
    fun `should_forward_multi_emission_flow`() = runTest {
        val addressOnly = AddressAndPlace(address, null)
        val withPlace = AddressAndPlace(address, place)
        val repo = object : com.rndeveloper.paparcar.domain.repository.AddressAndPlaceRepository {
            override fun getAddressAndPlace(lat: Double, lon: Double, persisting: Boolean) =
                flowOf(addressOnly, withPlace)
        }
        val useCase = GetAddressAndPlaceUseCase(repo)

        val result = useCase(40.416775, -3.703790).toList()

        assertEquals(listOf(addressOnly, withPlace), result)
    }
}
