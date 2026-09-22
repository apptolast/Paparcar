package com.rndeveloper.paparcar.bluetooth

import com.rndeveloper.paparcar.domain.detection.ports.CarLinkDeclineStore
import platform.Foundation.NSUserDefaults

/**
 * [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] NSUserDefaults-backed memory of the car MACs the user
 * declined to link. A plain comma-joined list of uppercased MACs — the set is tiny (the handful of
 * BT devices that ever framed a trip) and never syncs anywhere. Failing to read returns empty, the
 * safe side: the worst case is offering once more, never mislinking.
 */
class IosCarLinkDeclineStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : CarLinkDeclineStore {

    override suspend fun declined(): Set<String> =
        (defaults.objectForKey(KEY) as? String)
            ?.split(SEP)
            ?.mapNotNull { it.trim().uppercase().takeIf(String::isNotEmpty) }
            ?.toSet()
            ?: emptySet()

    override suspend fun decline(mac: String) {
        val normalised = mac.trim().uppercase()
        if (normalised.isEmpty()) return
        val next = declined() + normalised
        defaults.setObject(next.joinToString(SEP), KEY)
    }

    private companion object {
        const val KEY = "detection.car_link_declined_macs"
        const val SEP = ","
    }
}
