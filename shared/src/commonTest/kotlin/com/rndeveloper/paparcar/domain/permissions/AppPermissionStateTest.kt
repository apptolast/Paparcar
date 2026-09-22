package com.rndeveloper.paparcar.domain.permissions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPermissionStateTest {

    // ── Lo que iOS permite y Android no: precisión y hardware ausente ──────────────────────
    // [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]

    @Test
    fun `should_blockProducer_when_locationIsApproximate`() {
        // iOS 14+ concede ubicación en modo aproximado SIN dejar de decir «concedida». Una plaza no
        // se coloca con kilómetros de error: la detección está bloqueada aunque no falte nada más.
        val state = AppPermissionState(
            hasLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            hasNotificationPermission = true,
            hasReducedLocationAccuracy = true,
        )
        assertTrue(state.hasCorePermissions, "el mapa se puede seguir mirando")
        assertFalse(state.hasProducerPermissions)
        assertTrue(RequiredPermission.PRECISE_LOCATION in state.missingProducerPermissions())
    }

    @Test
    fun `should_notNamePreciseLocation_when_thereIsNoLocationAtAll`() {
        // Sin permiso, quien falta es FOREGROUND_LOCATION. Nombrar las dos contaría el mismo hueco
        // dos veces y la salud de detección enseñaría un remedio que no aplica todavía.
        val state = AppPermissionState(
            hasLocationPermission = false,
            hasReducedLocationAccuracy = true,
        )
        assertTrue(RequiredPermission.FOREGROUND_LOCATION in state.missingPermissions())
        assertFalse(RequiredPermission.PRECISE_LOCATION in state.missingPermissions())
    }

    @Test
    fun `should_reportProducerGranted_when_theDeviceHasNoMotionCoprocessor`() {
        // Un permiso que este dispositivo NO PUEDE conceder no puede faltar: contarlo dejaría la
        // detección bloqueada para siempre sin nada que el usuario pudiera hacer.
        val state = AppPermissionState(
            hasLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = false,
            isActivityRecognitionAvailable = false,
            hasNotificationPermission = true,
        )
        assertTrue(state.hasProducerPermissions)
        assertFalse(RequiredPermission.ACTIVITY_RECOGNITION in state.missingPermissions())
    }

    @Test
    fun `should_stillDemandActivity_when_theDeviceDoesHaveIt`() {
        // El testigo del testigo: sin esto, el caso de arriba pasaría igual con la regla borrada.
        val state = AppPermissionState(
            hasLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = false,
            isActivityRecognitionAvailable = true,
            hasNotificationPermission = true,
        )
        assertFalse(state.hasProducerPermissions)
        assertTrue(RequiredPermission.ACTIVITY_RECOGNITION in state.missingPermissions())
    }

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
