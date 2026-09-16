package com.rndeveloper.paparcar.domain.entitlement

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Los dos topes del coche compartido, que responden a preguntas distintas. (user, 16-09)
 *
 * El de MIEMBROS dice cuánta gente cabe en un coche; el de COCHES dice cuántos grupos abre una
 * misma persona. Solo el segundo protege la economía —compartir exige Plus, entrar es gratis— y por
 * eso es el único que hace falta: sin él, una suscripción habilitaba grupos ilimitados.
 */
class SharedCarEntitlementGateTest {

    @Test
    fun should_letThePairAndTheFamilyShare_when_underTheCarCap() {
        assertTrue(canShareAnotherCar(alreadyShared = 0))
        assertTrue(canShareAnotherCar(alreadyShared = 1))
    }

    @Test
    fun should_refuseTheThirdGroup_when_twoCarsAreAlreadyShared() {
        assertFalse(canShareAnotherCar(alreadyShared = SHARED_CAR_MAX_OWNED))
        // El techo que de verdad importa: 2 coches x 4 invitados = 8 personas por suscripción.
        assertTrue(SHARED_CAR_MAX_OWNED * SHARED_CAR_MAX_MEMBERS == 8)
    }

    @Test
    fun should_notAskForPlus_when_countingHowManyFitInACar() {
        // ⛔ Plus NO compra un grupo más grande: el tope de miembros es producto, no entitlement,
        // y no recibe `isPlus` por ningún lado. Un coche con seis conductores es una flota.
        assertTrue(canAddSharedCarMember(currentMembers = SHARED_CAR_MAX_MEMBERS - 1))
        assertFalse(canAddSharedCarMember(currentMembers = SHARED_CAR_MAX_MEMBERS))
    }

    @Test
    fun should_leaveRoomForEveryHonestGuest_when_cappingHowManyGroupsYouJoin() {
        assertTrue(canJoinAnotherCar(alreadyJoined = 0))
        assertTrue(canJoinAnotherCar(alreadyJoined = SHARED_CAR_MAX_JOINED - 1))
        assertFalse(canJoinAnotherCar(alreadyJoined = SHARED_CAR_MAX_JOINED))
        // ⛔ Alto A PROPÓSITO: alcanzarlo obliga a salirse de un grupo, y salirse borra tus viajes
        // allí. Un tope que empuja a destruir datos propios sería una trampa, no un tope.
        assertTrue(SHARED_CAR_MAX_JOINED >= 10)
    }

    @Test
    fun should_gateOnlyTheOwner_when_decidingWhoPaysForAGroup() {
        // Abrir el grupo es del dueño y exige Plus…
        assertTrue(canShareVehicle(isPlus = true))
        assertFalse(canShareVehicle(isPlus = false))
        // …y el tope del invitado NO recibe `isPlus` por ningún lado, a propósito: el Plus del dueño
        // paga el grupo, así que cobrarle al invitado sería cobrar dos veces el mismo sitio. Si
        // alguien le añade un parámetro de entitlement a `canJoinAnotherCar`, este test es el sitio
        // donde tiene que discutirlo.
    }
}
