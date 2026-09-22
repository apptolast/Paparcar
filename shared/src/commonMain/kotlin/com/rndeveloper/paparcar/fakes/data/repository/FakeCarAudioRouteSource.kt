package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.detection.AudioRoutePort
import com.rndeveloper.paparcar.domain.detection.ports.CarAudioRouteSource

/**
 * [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] The audio-route sensor for every graph that is not
 * iOS production: Android (which links cars through its own ACL lane, never this) and the mock
 * flavours. Returns whatever [ports] is set to — empty by default, so the "link this car" row's
 * tap reports "no car connected", the honest answer where there is no live audio route.
 */
class FakeCarAudioRouteSource(
    var ports: List<AudioRoutePort> = emptyList(),
) : CarAudioRouteSource {
    override fun currentBluetoothOutputs(): List<AudioRoutePort> = ports
}
