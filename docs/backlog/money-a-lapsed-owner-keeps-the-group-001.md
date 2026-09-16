# MONEY-A-LAPSED-OWNER-KEEPS-THE-GROUP-001 · El dueño que deja de pagar conserva el grupo, y puede seguir invitando

**Estado:** 🔴 Sin empezar — 1.2.1. **Decisión del user tomada; falta el dato de servidor.**
**Origen:** revisión independiente, 2.ª vuelta — punto 0.4.

## Problema

`canShareVehicle(isPlus)` se mira **una vez**, al crear el grupo. Después nadie vuelve a preguntar:

- `createInvite` no mira entitlement — el dueño caducado sigue **metiendo gente nueva**.
- La función de reparto tampoco, y es por diseño: estar en `members` ES el permiso.
- El tope de coches compartidos vive **solo en la UI** (`VehiclesViewModel`); `shareVehicle` y
  `createInvite` no lo comprueban, y las reglas dejan crear un `sharedCars` a cualquier autenticado.

O sea que el techo de «8 personas por suscripción» lo sostiene una pantalla, no el sistema.

## Decisión del user (16-09): opción A

**Los grupos abiertos siguen vivos; lo que se cierra es invitar a alguien nuevo.** Es lo único
coherente con *«losing Plus never deletes or disables anything, it only gates what is NEW»*: B (solo
lectura tras unos días) y C (pausar avisos) apagan algo que ya funcionaba, para gente que no ha
hecho nada.

## Lo que falta, y es lo caro

Ninguna de las tres opciones se puede implementar en el cliente: quien invita es el dueño, pero
**quien tiene que comprobarlo es el servidor**, y hoy no sabe nada de entitlement. Hace falta un
**webhook de RevenueCat** que escriba `ownerPlusActive` en `sharedCars/{carId}`, y con eso:

- la regla de `create` de `sharedCarInvites` puede exigirlo,
- el tope de coches deja de vivir solo en la UI.

⛔ Sin ese dato, cualquier tope que pongamos lo salta quien hable con Firestore directamente.

## Decisión del user (16-09): opción (b)

**`planExpiresAtMs` escrito por el cliente del dueño + 7 días de gracia.** Se eligió sobre la
extensión oficial de RevenueCat para Firebase con custom claims (a) porque esa exige verificar que
exista y esté disponible para `pap-26` y acordar el nombre del claim — trabajo que nadie ha hecho —
y sobre dejarlo como está (c) porque eso deja el techo sostenido por una pantalla.

⚠️ **Honestidad sobre lo que (b) protege**: el dato lo escribe el cliente del dueño, así que sirve
contra el descuido —la suscripción que caduca y nadie mira— y **no** contra quien manipule su propio
cliente. Para eso hace falta (a). Está bien elegido siempre que el ticket no finja otra cosa.

## Y el tope, que hoy vive solo en la interfaz (M5 de la 3.ª vuelta)

`VehiclesViewModel` comprueba `canShareAnotherCar`, pero `shareVehicle` y `createInvite` no, y las
reglas dejan crear un `sharedCars` a cualquier autenticado. Con `planExpiresAtMs` en el espejo, la
regla de `create` de `sharedCarInvites` puede exigir las dos cosas —Plus vivo y tope— y el techo
deja de depender de una pantalla.
