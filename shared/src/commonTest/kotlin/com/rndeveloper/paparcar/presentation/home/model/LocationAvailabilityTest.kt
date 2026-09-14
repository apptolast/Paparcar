package com.rndeveloper.paparcar.presentation.home.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El veredicto de «Home no sabe dónde estás». [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
 *
 * Los dos caminos a la misma ausencia se comprueban por separado porque la app se entera de cada uno
 * por un sitio distinto — y el primero es el que el predicado anterior no veía.
 */
class LocationAvailabilityTest {

    @Test
    fun should_haveNoLocation_when_theCorePermissionIsMissing_evenWithNoCar() {
        // Sin coche registrado la detección está en `NoVehicle`, no en `BlockedCore`: ese era
        // exactamente el hueco por el que Home se quedaba mudo para el usuario recién llegado.
        assertTrue(homeHasNoLocation(hasCorePermissions = false, DetectionUiState.NoVehicle))
    }

    @Test
    fun should_haveNoLocation_when_theWatchIsBlockedForCore() {
        // Permiso concedido, servicios de localización apagados: sólo la readiness lo sabe.
        assertTrue(homeHasNoLocation(hasCorePermissions = true, DetectionUiState.BlockedCore))
    }

    @Test
    fun should_haveLocation_when_thePermissionIsGrantedAndNothingIsBlocked() {
        assertFalse(homeHasNoLocation(hasCorePermissions = true, DetectionUiState.AwaitingFirstPark))
        assertFalse(homeHasNoLocation(hasCorePermissions = true, DetectionUiState.Parked))
        // ⛔ `Inactive` es la detección apagada o los permisos de PRODUCTOR: eso no es «sin
        // ubicación», y decirlo pintaría un aviso ámbar sobre un mapa que sabe perfectamente dónde
        // está el usuario.
        assertFalse(homeHasNoLocation(hasCorePermissions = true, DetectionUiState.Inactive))
    }
}
