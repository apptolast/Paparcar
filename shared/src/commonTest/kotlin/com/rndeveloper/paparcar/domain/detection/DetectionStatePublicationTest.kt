package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.permissions.RequiredPermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001] The pure half: what counts as a change worth a
 * Firestore write, and which population bucket an install falls into.
 */
class DetectionStatePublicationTest {

    private fun snapshot(
        autoDetect: Boolean = true,
        blockers: Set<RequiredPermission> = emptySet(),
        batteryExempt: Boolean = true,
        reliabilityReduced: Boolean = false,
    ) = DetectionStateSnapshot(autoDetect, blockers, batteryExempt, reliabilityReduced)

    // ── The dedupe: what makes this affordable ──────────────────────────────────────────────────

    @Test
    fun should_publish_when_nothingHasEverBeenPublished() {
        assertTrue(snapshot().needsPublishing(lastPublishedFingerprint = null))
    }

    @Test
    fun should_notPublish_when_theStateIsUnchanged() {
        val state = snapshot()
        assertFalse(state.needsPublishing(state.remoteFingerprint()))
    }

    @Test
    fun should_publish_when_aProducerPermissionIsRevoked() {
        val before = snapshot()
        val after = snapshot(blockers = setOf(RequiredPermission.BACKGROUND_LOCATION))
        assertTrue(after.needsPublishing(before.remoteFingerprint()))
    }

    @Test
    fun should_publish_when_theUserFlipsTheMasterSwitch() {
        val before = snapshot(autoDetect = true)
        assertTrue(snapshot(autoDetect = false).needsPublishing(before.remoteFingerprint()))
    }

    @Test
    fun should_publish_when_theBatteryExemptionChanges() {
        val before = snapshot(batteryExempt = true)
        assertTrue(snapshot(batteryExempt = false).needsPublishing(before.remoteFingerprint()))
    }

    /**
     * The one field that must NOT cost a write: it is not stored, so republishing on its account
     * would rewrite three identical fields. Analytics still sees it — that sink is free.
     */
    @Test
    fun should_notPublish_when_onlyReliabilityChanged() {
        val before = snapshot(reliabilityReduced = false)
        assertFalse(snapshot(reliabilityReduced = true).needsPublishing(before.remoteFingerprint()))
    }

    /**
     * A `Set` has no order. If the fingerprint rendered it in iteration order, the same two missing
     * permissions could stringify two ways and every emission would read as a change — the write
     * storm this type exists to prevent, reintroduced by an invisible detail.
     */
    @Test
    fun should_beStable_when_theSameBlockersArriveInAnotherOrder() {
        val one = snapshot(
            blockers = linkedSetOf(RequiredPermission.NOTIFICATIONS, RequiredPermission.BACKGROUND_LOCATION),
        )
        val other = snapshot(
            blockers = linkedSetOf(RequiredPermission.BACKGROUND_LOCATION, RequiredPermission.NOTIFICATIONS),
        )
        assertEquals(one.remoteFingerprint(), other.remoteFingerprint())
        assertFalse(other.needsPublishing(one.remoteFingerprint()))
    }

    /** Different blocker SETS must not collide — a fingerprint that cannot tell them apart would
     *  silently stop reporting the second one. */
    @Test
    fun should_differ_when_theBlockersAreDifferent() {
        assertNotEquals(
            snapshot(blockers = setOf(RequiredPermission.NOTIFICATIONS)).remoteFingerprint(),
            snapshot(blockers = setOf(RequiredPermission.BACKGROUND_LOCATION)).remoteFingerprint(),
        )
    }

    // ── The label: which population ─────────────────────────────────────────────────────────────

    @Test
    fun should_beOn_when_wantedPermittedAndTrusted() {
        assertEquals(DetectionStateLabel.ON, snapshot().label())
    }

    /**
     * The precedence that matters: someone who turned detection OFF is not "broken", and counting
     * their missing permissions would inflate the very number this measures.
     */
    @Test
    fun should_beOffByUser_when_theSwitchIsOffEvenWithBlockers() {
        val state = snapshot(
            autoDetect = false,
            blockers = setOf(RequiredPermission.BACKGROUND_LOCATION),
            reliabilityReduced = true,
        )
        assertEquals(DetectionStateLabel.OFF_BY_USER, state.label())
    }

    /** The population this whole ticket exists to count: wants it, cannot have it. */
    @Test
    fun should_beBlocked_when_wantedButAPermissionIsMissing() {
        val state = snapshot(blockers = setOf(RequiredPermission.ACTIVITY_RECOGNITION))
        assertEquals(DetectionStateLabel.BLOCKED_PERMISSIONS, state.label())
    }

    /** A hard blocker outranks a degradation — the order `SettingsState` already uses. */
    @Test
    fun should_preferBlocked_when_bothBlockedAndReduced() {
        val state = snapshot(
            blockers = setOf(RequiredPermission.NOTIFICATIONS),
            reliabilityReduced = true,
        )
        assertEquals(DetectionStateLabel.BLOCKED_PERMISSIONS, state.label())
    }

    @Test
    fun should_beReduced_when_permittedButTheEnvironmentIsHostile() {
        assertEquals(
            DetectionStateLabel.REDUCED_RELIABILITY,
            snapshot(reliabilityReduced = true).label(),
        )
    }
}
