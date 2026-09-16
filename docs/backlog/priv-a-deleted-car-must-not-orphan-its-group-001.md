# PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001 · Borrar el coche deja el grupo vivo, sin dueño y sin puerta para cerrarlo

**Estado:** 🔴 Sin empezar — abierto 2026-09-16 desde master `aa0be232`
**Origen:** pregunta del user (16-09): *«¿qué pasa si el owner elimina su cuenta o el coche?»*. La
mitad de la cuenta ya estaba resuelta; la del coche no.

## Lo que sí funciona

Borrar la **cuenta** del dueño disuelve el grupo entero para todos
(`SharedCarRepository.deleteAllData` → `stopSharing` → `deleteCar`), y los demás se auto-desvinculan
porque `syncFromRemote` deja de poder leer el espejo
(`SharedCarRepositoryImpl:273-278`). [PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001]

## Lo que no

`VehicleRepositoryImpl.deleteVehicle()` **no toca `sharedCars` en ningún punto**. Borra la fila
local, su historial, promociona el coche activo y encola el borrado remoto del vehículo. El espejo
sobrevive. Y `stopSharing` solo se llama desde un sitio: `VehiclesViewModel:400`, el botón de la
ficha del coche — la ficha que acaba de desaparecer.

Queda un **grupo huérfano**:

- los miembros lo siguen viendo en su garaje como coche vinculado, porque `getCar(carId)` responde;
- dentro siguen sus fichas de miembro con **tokens de push**, las sesiones espejadas y `state`;
- el dueño ya no tiene ninguna fila con ese `sharedCarId`, así que su app ha olvidado que el grupo
  existe y no hay pantalla desde la que disolverlo;
- la Cloud Function sigue repartiendo avisos de un coche que su dueño borró.

Es el mismo patrón que este mes ya mordió dos veces: **una salida que limpia una puerta y deja la
otra abierta** — primero el borrado de cuenta, luego `leave()`, ahora `deleteVehicle`.

## Decisión pendiente (es de producto)

Cuando se borra una fila de vehículo que pertenece a un grupo, el papel decide qué significa:

1. **Coche PROPIO compartido** (`sharedCarId != null && ownerUid == null`) → lo coherente con el
   borrado de cuenta es **disolver el grupo**, que es `stopSharing`. Y avisar antes: «este coche lo
   comparten N personas; si lo borras, el grupo se cierra».
2. **Coche PRESTADO** (`ownerUid != null`) → borrarlo debería ser **salir del grupo** (`leave`),
   que ahora ya se lleva sesiones, nombre en la cabecera y tokens. Hoy, si la UI lo permite, la
   ficha de miembro se queda dentro.

⚠️ Antes de implementar, comprobar qué permite la UI hoy: `VehiclesViewModel` puede estar bloqueando
el borrado de una fila vinculada, en cuyo caso el caso 2 es teórico y basta con dejarlo escrito.

## Criterio de éxito

- Borrar un coche propio compartido no deja nada en `sharedCars/{carId}`.
- Borrar un coche prestado deja el grupo intacto y al que se va, fuera — con sus tokens.
- Un test por caso, con el testigo de que ANTES el espejo sobrevivía.

## Lado iOS

`VehicleRepositoryImpl` y `SharedCarRepositoryImpl` son `commonMain`: iOS lo hereda.
