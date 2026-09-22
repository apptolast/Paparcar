package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.detection.AudioRoutePort
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.VehicleType
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateAudioRouteLinkOfferUseCase.LinkOfferDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] The opportunistic onboarding (vía B): offer to link
 * the one Bluetooth port that FRAMED the trip, and only then.
 */
class EvaluateAudioRouteLinkOfferUseCaseTest {

    private val useCase = EvaluateAudioRouteLinkOfferUseCase()

    private val skoda = AudioRoutePort(uid = "00:02:5C:22:22:11-tacl", name = "SKODA BT")
    private val headphones = AudioRoutePort(uid = "AA:BB:CC:DD:EE:FF-tacl", name = "Headphones")

    @Test
    fun should_offer_when_onePortFramedTheTrip_andNobodyClaimsIt() {
        val decision = useCase(
            activeVehicleId = "v-1",
            fleet = listOf(vehicle("v-1", bluetoothDeviceId = null)),
            portsWhileArming = listOf(skoda),
            portsNow = emptyList(),
        )
        val offer = assertIs<LinkOfferDecision.Offer>(decision)
        assertEquals("v-1", offer.vehicleId)
        assertEquals("00:02:5C:22:22:11", offer.mac)
        assertEquals("SKODA BT", offer.port.name)
    }

    @Test
    fun should_notOffer_when_thePortIsStillConnectedAtPark() {
        // Headphones survive the walk to the door; the car's stereo does not. A port still in the
        // route when the trip parked did not frame a car.
        val decision = useCase(
            activeVehicleId = "v-1",
            fleet = listOf(vehicle("v-1", bluetoothDeviceId = null)),
            portsWhileArming = listOf(headphones),
            portsNow = listOf(headphones),
        )
        assertEquals("no_framing_port", (decision as LinkOfferDecision.NoOffer).reason)
    }

    @Test
    fun should_notOffer_when_twoPortsFramedTheTrip() {
        // Stereo + a passenger's headset that also left: cannot tell them apart without asking
        // twice, so ask about neither.
        val decision = useCase(
            activeVehicleId = "v-1",
            fleet = listOf(vehicle("v-1", bluetoothDeviceId = null)),
            portsWhileArming = listOf(skoda, headphones),
            portsNow = emptyList(),
        )
        assertEquals("ambiguous_framing_ports", (decision as LinkOfferDecision.NoOffer).reason)
    }

    @Test
    fun should_notOffer_when_theMacAlreadyBelongsToAVehicle() {
        // Already linked to this car, or deliberately linked to another — the user's choice, not
        // ours to relitigate.
        val decision = useCase(
            activeVehicleId = "v-1",
            fleet = listOf(
                vehicle("v-1", bluetoothDeviceId = null),
                vehicle("v-2", bluetoothDeviceId = "00:02:5c:22:22:11"),
            ),
            portsWhileArming = listOf(skoda),
            portsNow = emptyList(),
        )
        assertEquals("no_framing_port", (decision as LinkOfferDecision.NoOffer).reason)
    }

    @Test
    fun should_notOffer_when_theUidCarriesNoMac() {
        val decision = useCase(
            activeVehicleId = "v-1",
            fleet = listOf(vehicle("v-1", bluetoothDeviceId = null)),
            portsWhileArming = listOf(AudioRoutePort(uid = "CarPlay", name = "SKODA")),
            portsNow = emptyList(),
        )
        assertEquals("no_framing_port", (decision as LinkOfferDecision.NoOffer).reason)
    }

    @Test
    fun should_notOffer_when_theMacWasAlreadyDeclined() {
        // "Not my car" is taken for an answer — the passenger's headset must not be offered again
        // and again, trip after trip. Asymmetric: ask once.
        val decision = useCase(
            activeVehicleId = "v-1",
            fleet = listOf(vehicle("v-1", bluetoothDeviceId = null)),
            portsWhileArming = listOf(skoda),
            portsNow = emptyList(),
            declinedMacs = setOf("00:02:5c:22:22:11"),
        )
        assertEquals("no_framing_port", (decision as LinkOfferDecision.NoOffer).reason)
    }

    @Test
    fun should_notOffer_when_noActiveVehicle() {
        val decision = useCase(
            activeVehicleId = null,
            fleet = emptyList(),
            portsWhileArming = listOf(skoda),
            portsNow = emptyList(),
        )
        assertEquals("no_active_vehicle", (decision as LinkOfferDecision.NoOffer).reason)
    }

    private fun vehicle(id: String, bluetoothDeviceId: String?) = Vehicle(
        id = id,
        userId = "u-1",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        vehicleType = VehicleType.CAR,
        bluetoothDeviceId = bluetoothDeviceId,
        isActive = true,
    )
}
