package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.VehicleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] The pure identity rules behind the iOS audio-route
 * sensor: parsing the MAC out of an undocumented port UID, matching it to the paired car, and
 * upgrading an arm — all without any audio session.
 */
class AudioRouteBtIdentityTest {

    @Test
    fun should_extractUppercaseMac_when_uidCarriesA2dpSuffix() {
        assertEquals("00:02:5C:22:22:11", audioRouteMac("00:02:5c:22:22:11-tacl"))
        assertEquals("04:88:E2:2B:E2:A0", audioRouteMac("04:88:e2:2b:e2:a0-tsco"))
    }

    @Test
    fun should_returnNull_when_uidCarriesNoMac() {
        // The day Apple randomises the UID, "no MAC" must be a normal answer, never a crash.
        assertNull(audioRouteMac(null))
        assertNull(audioRouteMac(""))
        assertNull(audioRouteMac("Speaker"))
        assertNull(audioRouteMac("BFB2C0E4-tacl"))
    }

    @Test
    fun should_matchOwnPort_when_pairedMacIsInTheRoute_caseInsensitive() {
        val car = vehicle(bluetoothDeviceId = "00:02:5c:22:22:11")
        val ports = listOf(
            AudioRoutePort(uid = "AA:BB:CC:DD:EE:FF-tacl", name = "Headphones"),
            AudioRoutePort(uid = "00:02:5C:22:22:11-tacl", name = "SKODA BT"),
        )
        val match = ownCarAudioPort(ports, car)
        assertEquals("SKODA BT", match?.name)
    }

    @Test
    fun should_notMatch_when_vehicleHasNoPairedMac() {
        val car = vehicle(bluetoothDeviceId = null)
        val ports = listOf(AudioRoutePort(uid = "00:02:5C:22:22:11-tacl", name = "SKODA BT"))
        assertNull(ownCarAudioPort(ports, car))
        assertNull(ownCarAudioPort(ports, null))
    }

    @Test
    fun should_upgradeUnverified_toOwnCarAudio_when_portMatched() {
        val matched = AudioRoutePort(uid = "00:02:5C:22:22:11-tacl", name = "SKODA BT")
        val upgraded = upgradeArmEvidenceWithOwnCarAudio(ArmEvidence.Unverified, matched)
        assertEquals(ArmEvidence.OwnCarAudio(portName = "SKODA BT"), upgraded)
    }

    @Test
    fun should_leaveUnverifiedUntouched_when_noPortMatched() {
        // Absence proves nothing — iOS 16+ can hand back an empty route in background.
        assertSame(ArmEvidence.Unverified, upgradeArmEvidenceWithOwnCarAudio(ArmEvidence.Unverified, null))
    }

    @Test
    fun should_neverDowngradeAStrongerArm_when_portMatched() {
        // A verified departure carries authority the audio port must not grant; the upgrade only
        // ever lifts Unverified, never replaces something stronger.
        val matched = AudioRoutePort(uid = "00:02:5C:22:22:11-tacl", name = "SKODA BT")
        val verified = ArmEvidence.VerifiedBySpeed(speedKmh = 42f, accuracyM = 8f)
        assertSame(verified, upgradeArmEvidenceWithOwnCarAudio(verified, matched))
        assertSame(ArmEvidence.Manual, upgradeArmEvidenceWithOwnCarAudio(ArmEvidence.Manual, matched))
    }

    @Test
    fun should_beOwnershipNotMovement() {
        // The whole doctrine of this arm in one assertion: it says whose car, never that it moved.
        val arm = ArmEvidence.OwnCarAudio(portName = "SKODA BT")
        assertEquals(DriveAuthorization.None, arm.driveAuthorization)
        assertTrue(!arm.isVerifiedDeparture)
        assertTrue(!arm.confirmsSilentlyWithoutMeasuredDrive)
        assertEquals(ArmLabel.OWN_CAR_AUDIO, arm.label)
        assertEquals("own_car_audio", arm.persistLabel)
    }

    private fun vehicle(bluetoothDeviceId: String?) = Vehicle(
        id = "v-1",
        userId = "u-1",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        vehicleType = VehicleType.CAR,
        bluetoothDeviceId = bluetoothDeviceId,
        isActive = true,
    )
}
