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

    // ── ¿Puede Home DECIRLO ya? [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001] ──────────────────────

    @Test
    fun should_awaitTheAnswer_when_theAskHasNotBeenMadeYet() {
        // Primer frame de Home: el permiso no está y la pregunta se dispara en cuanto el mapa llega a
        // primer plano. La pref se estampa ANTES de lanzar el diálogo, así que este es el único
        // término que cubre esta ventana.
        assertTrue(
            homeAwaitsLocationAnswer(
                hasCorePermissions = false, locationEverAsked = false, locationAnswerInFlight = false,
            ),
        )
    }

    @Test
    fun should_awaitTheAnswer_when_theDialogIsOnScreen() {
        assertTrue(
            homeAwaitsLocationAnswer(
                hasCorePermissions = false, locationEverAsked = true, locationAnswerInFlight = true,
            ),
        )
    }

    @Test
    fun should_speak_when_theAskWasAnsweredWithARefusal() {
        // El caso al que el aviso sirve: ya preguntamos y dijeron que no.
        assertFalse(
            homeAwaitsLocationAnswer(
                hasCorePermissions = false, locationEverAsked = true, locationAnswerInFlight = false,
            ),
        )
    }

    @Test
    fun should_speak_when_thePermissionIsGrantedAndNoAskIsComing() {
        // ⛔ El testigo del primer término: permiso concedido + servicios apagados. A este usuario no
        // se le preguntó NUNCA (`locationEverAsked` false para siempre), así que sin
        // `hasCorePermissions` delante su píldora se callaría esperando una respuesta que no existe.
        assertFalse(
            homeAwaitsLocationAnswer(
                hasCorePermissions = true, locationEverAsked = false, locationAnswerInFlight = false,
            ),
        )
        assertTrue(homeHasNoLocation(hasCorePermissions = true, DetectionUiState.BlockedCore))
    }
}
