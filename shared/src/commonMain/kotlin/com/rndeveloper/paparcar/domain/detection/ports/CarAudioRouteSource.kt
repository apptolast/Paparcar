package com.rndeveloper.paparcar.domain.detection.ports

import com.rndeveloper.paparcar.domain.detection.AudioRoutePort

/**
 * [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] Reads the Bluetooth audio-output ports the system's
 * audio session is routed to RIGHT NOW.
 *
 * A port because the reading is platform I/O (iOS `AVAudioSession.currentRoute`) while every
 * decision that consumes it is a pure rule in commonMain. It is a SENSOR, never a trigger: it
 * answers only while the process is already alive (a live detection session keeps it alive), it
 * never wakes the app, and it never fires an edge. On platforms with no such signal — Android,
 * every test double — the implementation returns an empty list, which the rules already treat as
 * "the car is not observably here", the safe side. [DET-BT-CONNECTED-NOT-PAIRED-001]
 *
 * ⚠️ Asymmetric by contract: a non-empty reading is evidence the paired car is connected; an
 * EMPTY reading proves nothing (iOS 16+ can hand back an empty route in background). No consumer
 * may read absence as "the car left".
 */
interface CarAudioRouteSource {
    /** Bluetooth A2DP output ports currently in the audio route; empty when none or unreadable. */
    fun currentBluetoothOutputs(): List<AudioRoutePort>
}
