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
 * ⛔ Y por eso **este es el único tope que cobra**: el del invitado ([canJoinAnotherCar]) existe,
 * pero es un techo técnico sin paywall — cobrarle por entrar sería cobrar dos veces el mismo sitio,
 * y lo contradice el trato que el propio código documenta en `VehicleEntitlementGate`: *the OWNER's
 * Plus pays for the group*.
 *
 * @param alreadyShared coches propios que este usuario ya tiene compartidos.
 */
fun canShareAnotherCar(alreadyShared: Int): Boolean = alreadyShared < SHARED_CAR_MAX_OWNED

const val SHARED_CAR_MAX_OWNED = 2

/**
 * En cuántos grupos puede estar la misma persona como INVITADA. (user, 16-09)
 *
 * ⛔ **Techo técnico, no paywall**, y la diferencia importa: compartir ya exige Plus y entrar es
 * gratis porque *the OWNER's Plus pays for the group* — cobrarle al que entra sería cobrar dos veces
 * el mismo sitio. Diez no protege ninguna cuenta de resultados: protege de que alguien coleccione
 * coches prestados, que es un uso que no existe.
 *
 * ⛔ Y por eso el número es ALTO: alcanzarlo obliga a salirse de un grupo, y salirse **borra tus
 * viajes allí** [PRIV-LEAVING-A-GROUP-MUST-CLEAR-YOUR-NAME-FROM-ITS-HEAD-001]. Un tope que empuja a
 * destruir datos propios no es un tope, es una trampa; a diez no se llega sin querer.
 *
 * @param alreadyJoined coches PRESTADOS que ya tiene (`isLinked`), nunca los suyos.
 */
fun canJoinAnotherCar(alreadyJoined: Int): Boolean = alreadyJoined < SHARED_CAR_MAX_JOINED

const val SHARED_CAR_MAX_JOINED = 10

