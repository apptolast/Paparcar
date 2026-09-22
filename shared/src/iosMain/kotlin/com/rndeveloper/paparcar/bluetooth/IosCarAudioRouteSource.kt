@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.bluetooth

import com.rndeveloper.paparcar.domain.detection.AudioRoutePort
import com.rndeveloper.paparcar.domain.detection.ports.CarAudioRouteSource
import io.github.aakira.napier.Napier
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortBluetoothA2DP
import platform.AVFAudio.AVAudioSessionPortDescription
// currentRoute is an ObjC-category extension in K/N (package-level), not a class member — import it.
import platform.AVFAudio.currentRoute

/**
 * [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] iOS implementation of [CarAudioRouteSource].
 *
 * Reads `AVAudioSession.sharedInstance().currentRoute.outputs` and keeps only the Bluetooth A2DP
 * ports — the profile a car stereo exposes, and the one whose UID historically carries the device
 * MAC. Deliberately **only A2DP**: HFP-only ports would need `playAndRecord` + microphone
 * permission (a car radio does not justify that), and CarPlay's `.carAudio` port carries no MAC.
 *
 * ⚠️ **Passive read, never an activation.** It does NOT call `setCategory`/`setActive`: reading the
 * current route needs neither, and activating a session could interrupt the user's audio or drag
 * in a permission. This is also why it can never be a trigger — it only reports what the route
 * already is, while the process is alive.
 *
 * On any failure (no session, empty route in background on iOS 16+) it returns an empty list: the
 * common rules treat "no port" as "the car is not observably here", the safe side. Absence never
 * means "the car left".
 */
class IosCarAudioRouteSource : CarAudioRouteSource {

    override fun currentBluetoothOutputs(): List<AudioRoutePort> = runCatching {
        val outputs = AVAudioSession.sharedInstance().currentRoute.outputs
        outputs.mapNotNull { it as? AVAudioSessionPortDescription }
            .filter { it.portType == AVAudioSessionPortBluetoothA2DP }
            .map { AudioRoutePort(uid = it.UID, name = it.portName) }
    }.getOrElse { e ->
        Napier.w("audio route read failed (treating as no port)", e, tag = "IosCarAudioRouteSource")
        emptyList()
    }
}
