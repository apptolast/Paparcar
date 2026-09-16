package com.rndeveloper.paparcar.domain.entitlement

/**
 * Who may open a shared car and how big the group gets, decided in ONE place. [VEH-SHARED-001]
 *
 * *Free asks, Plus watches and remembers* ([plus-alerts-and-tiers-001]): a shared car is the app
 * telling you what somebody ELSE did while you were not looking, so **creating the group is the
 * owner's Plus**. The people who join do not need Plus and never will — the owner's subscription
 * covers the car (decision 06/07-09).
 */
fun canShareVehicle(isPlus: Boolean): Boolean = isPlus

/**
 * Whether one more person fits. The cap is a product decision, not an entitlement one: Plus does
 * NOT buy a bigger group. A car with six drivers is a fleet, and that is not what this feature is.
 *
 * @param currentMembers people already in, the owner NOT counted.
 */
fun canAddSharedCarMember(currentMembers: Int): Boolean = currentMembers < SHARED_CAR_MAX_MEMBERS

const val SHARED_CAR_MAX_MEMBERS = 4

/**
 * Cuántos coches PROPIOS puede tener abiertos a un grupo la misma persona. (user, 16-09)
 *
 * Es el único tope que protege la economía del producto, y por eso va en el lado del DUEÑO y no en
 * el del invitado: compartir ya exige Plus y entrar es gratis, así que sin este tope una sola
 * suscripción habilitaba grupos ilimitados — diez coches por cuatro invitados son cuarenta personas
 * usando el producto por un pago. Con dos, el techo queda en **ocho invitados por suscripción**,
 * que cubre a la pareja con dos coches y a la familia sin dejar sitio al «un Plus para toda la
 * calle».
 *
 * ⛔ Y por eso **no hay tope en el lado del invitado**: cobrarle por entrar sería cobrar dos veces
 * el mismo sitio, y lo contradice el trato que el propio código documenta en `VehicleEntitlementGate`
 * — *the OWNER's Plus pays for the group*.
 *
 * @param alreadyShared coches propios que este usuario ya tiene compartidos.
 */
fun canShareAnotherCar(alreadyShared: Int): Boolean = alreadyShared < SHARED_CAR_MAX_OWNED

const val SHARED_CAR_MAX_OWNED = 2
