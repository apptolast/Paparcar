# SYNC-A-REMOTE-DELETE-HAS-NO-OUTBOX-BEHIND-IT-001 · Un borrado remoto es la única escritura sin red de seguridad

**Estado:** 🔵 Abierto, sin código · rama `chore/SYNC-A-REMOTE-WRITE-MUST-OUTLIVE-THE-PROCESS-001-ticket`
· worktree `../Paparcar-remotewrite`
**Abierto:** 2026-08-31 sobre master `d74e6e8c` · nace de
`VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001` (`b0d40353`), donde el riesgo quedó documentado
como residual y sin arreglar

## Problema

`VehicleRepositoryImpl` manda cuatro bloques de escritura remota a `syncScope.launch`
(`VehicleRepositoryImpl.kt:154, 211, 254, 272`), deliberadamente en background para que el botón no
se cuelgue esperando el ack del servidor mientras no hay red. Ese scope muere con el proceso.

Para tres de los cuatro **eso no importa**, y por eso el patrón se estableció: son UPDATES, y cada
fila mutada queda marcada `pendingSync = 1`. Si la escritura remota no llega, la fila sigue ahí,
marcada, y `pushPendingVehicles()` la drena en el siguiente arranque o al recuperar conectividad.
Hay bandera, hay buzón y hay quien lo vacía. [SYNC-RECONCILE-001]

**El borrado rompe esa simetría, y la rompe en silencio: no queda fila donde colgar la bandera.**
`deleteVehicle` borra la fila local del vehículo y —desde `b0d40353`— también todas sus sesiones,
y *después* pide los borrados remotos en background. Si el proceso muere antes de que el SDK drene
su cola, los documentos remotos sobreviven y **no queda nada en local que recuerde que había que
borrarlos**. Ningún `pendingSync`, ningún outbox, ninguna reconciliación que lo note.

Peor: no se queda en un borrado a medias, se **deshace solo**. El reconcile de entrada
(`reconcilePending`) se queda con cualquier fila remota que no exista en local — está escrito para
eso. Así que el siguiente sync vuelve a bajar a Room justo los documentos que el usuario mandó
borrar: el coche reaparece, y con él el historial que este proyecto acaba de decidir que debía
morir con su coche.

Es el mismo agujero que ya tenía el borrado del vehículo antes de tocarlo; lo que cambió en
`b0d40353` es que ahora hay más cosas que resucitar.

## Doctrina violada

*Fallo asimétrico: mejor un falso negativo que un falso positivo.* Un borrado que se deshace solo es
la peor cara de eso: la app afirma con un diálogo irreversible («esto no se puede deshacer») algo
que el sistema puede revertir por su cuenta sin decírselo a nadie.

Y **sistemas, no parches**: el invariante no es «el borrado de sesiones necesita un worker», es *una
escritura remota que nadie puede reintentar no puede quedar colgando de un coroutine scope*. Ponerle
worker sólo a la parte que se tocó en el ticket anterior dejaría el borrado del vehículo —la mitad
gemela de la misma operación— exactamente igual de expuesto.

## Señales / datos disponibles

- La asimetría es legible en el código, no hay que medirla: `markPending`/`clearPending` +
  `pushPendingVehicles()` cubren los updates; ninguna de las dos cosas existe para un borrado.
- **El fontanero ya está montado**: `ParkingSyncScheduler` (commonMain, `domain/service/`) con actual
  en Android (WorkManager) e iOS, y tres precedentes exactos de este patrón —
  `enqueueSaveNewParkingSession`, `enqueueClearActiveParkingSession`,
  `enqueueUpdateParkingSessionAddressAndPlace`. Los workers correspondientes ya resuelven reintentos
  con backoff y constraint de red.
- ⚠️ No está medido cuánto aguanta la cola offline del SDK de Firestore entre arranques. Es la
  hipótesis en la que se apoya el `syncScope` hoy, y este ticket **no depende de refutarla**: aunque
  aguante, sigue sin haber reintento propio si el SDK falla o si el borrado remoto devuelve error.

## Diseño

**El invariante: toda escritura remota que no se pueda reconstruir desde el estado local viaja en un
worker, no en un scope.** El sitio donde vive es `ParkingSyncScheduler`, junto a las tres que ya
están.

Alcance mínimo y coherente — **las dos mitades del mismo borrado, no una**:

1. `enqueueDeleteParkingSessionsForVehicle(vehicleId)` — las sesiones remotas del coche.
2. El borrado remoto del **documento del vehículo**, hoy en el mismo `syncScope.launch:211`.

Ambas en el mismo worker, en ese orden (sesiones primero: el motivo está escrito en
`VehicleRepositoryImpl.kt:212-214`). Un worker con `NonCancellable` + retry, como
`SaveNewParkingSessionWorker`.

Fuera de alcance a propósito: los tres `syncScope` de updates (`saveVehicle`, `setActiveVehicle`,
`updateBluetoothDevice`). Tienen buzón y se autocuran; moverlos sería trabajo sin defecto detrás.

⛔ **No hacer que el borrado espere el ack** (`await`). Se descartó al implementar `b0d40353` y sigue
descartado: colgaría el botón sin red, que es la razón por la que existe el `syncScope`.

⚠️ **A decidir al implementar**: `deleteAllData` (borrado de cuenta) usa `deleteUserData`, que barra
todo de una vez. Comprobar si va por el mismo camino frágil o si el flujo de cuenta ya lo espera —
y si comparte el agujero, entra aquí; si no, se dice por qué no.

## Criterio de éxito

- Matar el proceso justo después de confirmar el borrado y, al volver a abrir, el coche **no
  reaparece**. Es el único testigo que prueba de verdad la diferencia entre un scope y un worker.
- Test del worker en `androidUnitTest` siguiendo `ParkingSyncWorkerTest`: el worker pide los dos
  borrados remotos, en orden, y reintenta si el remoto falla.
- ⛔ Validar **por falsación**: con el worker desactivado y el remoto fallando, un sync posterior
  debe resucitar el coche. Si no falla, el test no está midiendo esto.
- Sin strings nuevos (nada de esto se le cuenta al usuario: para él el borrado ya ocurrió).

## Consumidores auditados

Pendiente de barrido al implementar. Puntos de partida:

- `VehicleRepositoryImpl.deleteVehicle` (`:211`) — el bloque que se muda.
- `ParkingSyncScheduler` + su actual Android e iOS — ⛔ **iOS también implementa la interfaz**: un
  método nuevo obliga a resolverlo ahí, aunque sea con un TODO explícito.
- `RemoteUserProfileDataSource.deleteParkingSessionsForVehicle` / `deleteVehicle` — no cambian, sólo
  cambia quién los llama.
- `UserParkingReconcile` — no se toca; es la pieza que hace visible el fallo, no su causa.
- `deleteAllData` / `deleteUserData` — ver la decisión abierta arriba.

## Relacionados

- `VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001` (`b0d40353`) — de donde sale, y donde el riesgo
  quedó escrito como residual asumido.
- `SYNC-RECONCILE-001` / `SYNC-RECONCILE-USERPARKING-001` — el mecanismo `pendingSync` que cubre los
  updates y del que un borrado no puede beneficiarse.
