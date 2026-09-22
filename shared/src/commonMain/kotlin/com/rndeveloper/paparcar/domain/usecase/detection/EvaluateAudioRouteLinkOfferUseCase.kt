package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.detection.AudioRoutePort
import com.rndeveloper.paparcar.domain.detection.audioRouteMac
import com.rndeveloper.paparcar.domain.model.Vehicle

/**
 * [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] The opportunistic onboarding (vía B): after a trip
 * the coordinator followed, decide whether one Bluetooth port that FRAMED the trip earns a
 * "is this your car?" offer — and for which vehicle to save the MAC if the user says yes.
 *
 * Pure so the whole decision is testable without an audio session. `IosDetectionController`
 * gathers the inputs (what ports were seen while arming, what the route reads now, the fleet) and
 * posts the nudge only when this returns [Offer].
 *
 * The guards are the doctrine, not extras:
 *  - The candidate must FRAME the trip: present when the drive armed AND gone now that it parked.
 *    A pair of headphones survives the walk to the door; the car's stereo does not. This is what
 *    separates "the car I drove" from "a device that happened to be connected".
 *  - Exactly ONE such port. Two framing A2DP ports (stereo + a passenger's headset that also
 *    left) cannot be told apart without asking twice, so we ask about neither.
 *  - The MAC must not already belong to a vehicle: an already-linked car needs no offer, and a
 *    MAC linked to a DIFFERENT car is the user's explicit choice, not ours to relitigate.
 *  - The UID must carry a MAC at all — no MAC, nothing to save (the offer would be a dead end).
 */
class EvaluateAudioRouteLinkOfferUseCase {

    sealed interface LinkOfferDecision {
        /** Offer to link [port] (its MAC) to [vehicleId]; the port name is the human label. */
        data class Offer(val vehicleId: String, val port: AudioRoutePort, val mac: String) : LinkOfferDecision
        /** Nothing to offer — the reason is for diagnostics, never shown to the user. */
        data class NoOffer(val reason: String) : LinkOfferDecision
    }

    operator fun invoke(
        activeVehicleId: String?,
        fleet: List<Vehicle>,
        portsWhileArming: List<AudioRoutePort>,
        portsNow: List<AudioRoutePort>,
        declinedMacs: Set<String> = emptySet(),
    ): LinkOfferDecision {
        val vehicleId = activeVehicleId
            ?: return LinkOfferDecision.NoOffer("no_active_vehicle")

        // Any MAC the fleet already carries is off the table — linked, or deliberately on another car.
        val claimedMacs = fleet.mapNotNull { it.bluetoothDeviceId?.trim()?.uppercase()?.takeIf(String::isNotEmpty) }
            .toSet()
        // MACs the user already said "not my car" to — never ask twice.
        val declined = declinedMacs.map { it.uppercase() }.toSet()
        val nowMacs = portsNow.mapNotNull { audioRouteMac(it.uid) }.toSet()

        // Framing = seen while arming, absent now, with a real MAC that nobody has claimed or refused.
        val framing = portsWhileArming.filter { port ->
            val mac = audioRouteMac(port.uid) ?: return@filter false
            mac !in nowMacs && mac !in claimedMacs && mac !in declined
        }.distinctBy { audioRouteMac(it.uid) }

        return when {
            framing.isEmpty() -> LinkOfferDecision.NoOffer("no_framing_port")
            framing.size > 1 -> LinkOfferDecision.NoOffer("ambiguous_framing_ports")
            else -> {
                val port = framing.single()
                LinkOfferDecision.Offer(vehicleId = vehicleId, port = port, mac = audioRouteMac(port.uid)!!)
            }
        }
    }
}
