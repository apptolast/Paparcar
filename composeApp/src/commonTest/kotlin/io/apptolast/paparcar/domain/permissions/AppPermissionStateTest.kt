package io.apptolast.paparcar.domain.permissions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPermissionStateTest {

    @Test
    fun `should report core granted with only foreground location`() {
        // CORE is foreground location alone — notifications are no longer part of it. [DET-READY-001i]
        val state = AppPermissionState(hasLocationPermission = true)
        assertTrue(state.hasCorePermissions)
    }

    @Test
    fun `should keep core granted when only notifications denied`() {
        // Notifications moved to PRODUCER: denying them must NOT block the consumer side. [DET-READY-001i]
        val state = AppPermissionState(
            hasLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            hasNotificationPermission = false,
        )
        assertTrue(state.hasCorePermissions)
        assertFalse(state.hasProducerPermissions)
        assertTrue(RequiredPermission.NOTIFICATIONS in state.missingProducerPermissions())
        assertEquals(emptySet(), state.missingCorePermissions())
    }

    @Test
    fun `should report producer granted when background activity recognition and notifications present`() {
        val state = AppPermissionState(
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            hasNotificationPermission = true,
        )
        assertTrue(state.hasProducerPermissions)
    }

    @Test
    fun `should list only producer permissions as missing when core is granted`() {
        val state = AppPermissionState(
            hasLocationPermission = true,
            hasNotificationPermission = true,
            hasBackgroundLocationPermission = false,
            hasActivityRecognitionPermission = false,
        )
        assertEquals(
            setOf(RequiredPermission.BACKGROUND_LOCATION, RequiredPermission.ACTIVITY_RECOGNITION),
            state.missingPermissions(),
        )
        assertEquals(emptySet(), state.missingCorePermissions())
        assertEquals(
            setOf(RequiredPermission.BACKGROUND_LOCATION, RequiredPermission.ACTIVITY_RECOGNITION),
            state.missingProducerPermissions(),
        )
    }

    @Test
    fun `should report all four permissions missing on first launch`() {
        val state = AppPermissionState()
        assertEquals(
            setOf(
                RequiredPermission.FOREGROUND_LOCATION,
                RequiredPermission.NOTIFICATIONS,
                RequiredPermission.BACKGROUND_LOCATION,
                RequiredPermission.ACTIVITY_RECOGNITION,
            ),
            state.missingPermissions(),
        )
    }

    @Test
    fun `should report no missing permissions when all granted`() {
        val state = AppPermissionState(
            hasLocationPermission = true,
            hasNotificationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
        )
        assertTrue(state.missingPermissions().isEmpty())
        assertTrue(state.hasCorePermissions)
        assertTrue(state.hasProducerPermissions)
    }
}
