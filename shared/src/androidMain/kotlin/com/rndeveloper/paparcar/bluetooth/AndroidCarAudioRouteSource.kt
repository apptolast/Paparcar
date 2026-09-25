package com.rndeveloper.paparcar.bluetooth

import com.rndeveloper.paparcar.domain.detection.AudioRoutePort
import com.rndeveloper.paparcar.domain.detection.ports.CarAudioRouteSource

/**
 * [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] The audio-route sensor on Android: **empty by
 * construction**, because this platform answers "is the car connected?" with a real signal —
 * the ACL lane (`AndroidBtConnectStampReader`, the connect/disconnect receiver) — and the audio
 * route exists in the port only for iOS, where no ACL event reaches a third-party app.
 *
 * The port's own contract already prescribes this: *"On platforms with no such signal — Android,
 * every test double — the implementation returns an empty list, which the rules already treat as
 * 'the car is not observably here', the safe side."* So this is the ANDROID IMPLEMENTATION, not a
 * stand-in for one.
 *
 * ## Why it exists as production code instead of reusing the fake
 *
 * [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001] It used to be `FakeCarAudioRouteSource`,
 * bound straight into `AndroidPlatformModule` — a PRODUCTION graph importing the mock graph. That
 * compiled only while the fakes lived inside `:shared`, and the whole point of moving them to
 * `:mockkit` is that production cannot see them: 161 exported `Fake*` classes were measured in the
 * Release framework of TestFlight build 2, which is App Store guideline 2.3.1 territory.
 *
 * So the module split did not break this binding — it REVEALED it. A permanent platform absence is
 * not a test double: it is the honest implementation for a platform whose answer is always "no
 * audio route here", and naming it after the platform (like [AndroidBluetoothScanner] and every
 * other binding in that module) is what keeps the next reader from looking for the missing sensor.
 */
class AndroidCarAudioRouteSource : CarAudioRouteSource {

    /** Always empty: Android proves car presence through the ACL lane, never through the route. */
    override fun currentBluetoothOutputs(): List<AudioRoutePort> = emptyList()
}
