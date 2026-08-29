package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.detection.ServicePresence
import kotlin.test.Test
import kotlin.test.assertEquals

class ParkedWatchBadgeTest {

    @Test
    fun `no parked session invites parking regardless of everything else`() {
        assertEquals(
            ParkedWatchBadge.PARK_MY_VEHICLE,
            resolveParkedWatchBadge(
                hasParkedSession = false,
                isBluetoothCovered = false,
                presence = ServicePresence.Sentry,
                isReliabilityReduced = false,
            ),
        )
    }

    @Test
    fun `coordinator parked with live sentry and reliable setup is really watching`() {
        assertEquals(
            ParkedWatchBadge.WATCHING,
            resolveParkedWatchBadge(
                hasParkedSession = true,
                isBluetoothCovered = false,
                presence = ServicePresence.Sentry,
                isReliabilityReduced = false,
            ),
        )
    }

    @Test
    fun `coordinator parked but service DEAD is watch interrupted, not watching`() {
        // The OS killed the resident watcher — the old surface would still claim "Vigilando". Honest.
        assertEquals(
            ParkedWatchBadge.WATCH_INTERRUPTED,
            resolveParkedWatchBadge(
                hasParkedSession = true,
                isBluetoothCovered = false,
                presence = ServicePresence.Dead,
                isReliabilityReduced = false,
            ),
        )
    }

    @Test
    fun `coordinator parked, alive but fragile setup warns while still watching`() {
        assertEquals(
            ParkedWatchBadge.WATCHING_FRAGILE,
            resolveParkedWatchBadge(
                hasParkedSession = true,
                isBluetoothCovered = false,
                presence = ServicePresence.Sentry,
                isReliabilityReduced = true,
            ),
        )
    }

    @Test
    fun `dead service takes precedence over fragility copy`() {
        // If it's already dead, "reactivate" is the honest ask — not "it might get killed".
        assertEquals(
            ParkedWatchBadge.WATCH_INTERRUPTED,
            resolveParkedWatchBadge(
                hasParkedSession = true,
                isBluetoothCovered = false,
                presence = ServicePresence.Dead,
                isReliabilityReduced = true,
            ),
        )
    }

    @Test
    fun `bluetooth-covered parked is watching without an FGS even when presence is dead`() {
        // The manifest ACL receiver covers the BT car's reconnect independently of any resident process.
        assertEquals(
            ParkedWatchBadge.WATCHING,
            resolveParkedWatchBadge(
                hasParkedSession = true,
                isBluetoothCovered = true,
                presence = ServicePresence.Dead,
                isReliabilityReduced = false,
            ),
        )
    }

    @Test
    fun `active tracking job counts as watching`() {
        assertEquals(
            ParkedWatchBadge.WATCHING,
            resolveParkedWatchBadge(
                hasParkedSession = true,
                isBluetoothCovered = false,
                presence = ServicePresence.Active,
                isReliabilityReduced = false,
            ),
        )
    }
}
