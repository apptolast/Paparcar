package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.fakes.FakeAddressAndPlaceRepository
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

        assertEquals(listOf(info), result)
    }

    @Test
    fun `should_forward_multi_emission_flow`() = runTest {
        val addressOnly = AddressAndPlace(address, null)
        val withPlace = AddressAndPlace(address, place)
        val repo = object : io.apptolast.paparcar.domain.repository.AddressAndPlaceRepository {
            override fun getAddressAndPlace(lat: Double, lon: Double) = flowOf(addressOnly, withPlace)
        }
        val useCase = GetAddressAndPlaceUseCase(repo)

        val result = useCase(40.416775, -3.703790).toList()

        assertEquals(listOf(addressOnly, withPlace), result)
    }
}
