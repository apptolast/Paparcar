package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.Vehicle

/**
 * [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] One Bluetooth audio output as the platform's audio
 * session reports it. On iOS this is an `AVAudioSessionPortDescription` reduced to the two fields
 * that carry identity: the port UID and the human name of the device.
 *
 * ⚠️ The UID is UNDOCUMENTED behaviour: for Classic BT audio ports it has historically been the
 * device MAC with a profile suffix (`"00:02:5C:22:22:11-tacl"` for A2DP). Apple already
 * randomised CoreBluetooth's identifiers and may close this one — every consumer must treat
 * "the UID is not a MAC" as a normal answer, never an error. [audioRouteMac] is the single
 * place that reads it.
 */
data class AudioRoutePort(val uid: String?, val name: String?)

/**
 * Extracts the Bluetooth MAC from an audio-route port UID, or null when the UID does not carry
 * one. The ONLY place the `"MAC-suffix"` shape is parsed — if Apple changes the format, this is
 * the one function that learns about it.
 *
 * Returns the MAC normalised to UPPERCASE, the same normalisation `sharedCarBtToken` applies, so
 * a MAC captured here compares byte-for-byte with one captured by Android's ACL receiver.
 */
fun audioRouteMac(uid: String?): String? {
    val value = uid?.trim() ?: return null
    val match = AUDIO_ROUTE_UID_MAC.find(value) ?: return null
    return match.value.uppercase()
}

/**
 * The port among [ports] whose UID carries the MAC this [vehicle] is paired to, or null.
 * Null [Vehicle.bluetoothDeviceId] never matches: no identity, no ownership claim.
 */
fun ownCarAudioPort(ports: List<AudioRoutePort>, vehicle: Vehicle?): AudioRoutePort? {
    val pairedMac = vehicle?.bluetoothDeviceId?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        ?: return null
    return ports.firstOrNull { audioRouteMac(it.uid) == pairedMac }
}

/**
 * Upgrades an arm's evidence with the ownership the audio route proved at the moment of arming:
 * the phone's audio was routed to THIS vehicle's Bluetooth when the departure fired.
 *
 * Deliberately narrow — the upgrade only replaces [ArmEvidence.Unverified]:
 *  - A verified departure ([ArmEvidence.VerifiedBySpeed]…) is STRONGER (it carries
 *    `OnTrust`/silent-confirm authority the audio port must not grant); replacing it would be a
 *    downgrade wearing an upgrade's name.
 *  - [ArmEvidence.Manual] is the user's word and [ArmEvidence.ArrivalHandoff] carries its own
 *    story; neither needs an ownership witness.
 *
 * [matchedPort] null (no port, no MAC in the UID, different car) leaves the base untouched —
 * absence proves nothing (iOS 16+ may hand an empty route in background). Asymmetric by design.
 */
fun upgradeArmEvidenceWithOwnCarAudio(base: ArmEvidence, matchedPort: AudioRoutePort?): ArmEvidence =
    if (matchedPort != null && base is ArmEvidence.Unverified) {
        ArmEvidence.OwnCarAudio(portName = matchedPort.name)
    } else {
        base
    }

/** Leading `AA:BB:CC:DD:EE:FF` of a port UID; suffix (`-tacl`, `-tsco`, …) deliberately ignored. */
private val AUDIO_ROUTE_UID_MAC = Regex("^[0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5}")
