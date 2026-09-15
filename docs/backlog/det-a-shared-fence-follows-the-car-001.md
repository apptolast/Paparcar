# DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001 · la geocerca sigue al coche, no a quien lo aparcó

**Estado:** ✅ **Done** (pendiente de mergear) · rama `feature/DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001` ·
worktree `../Paparcar-shared-fence` · base master `164ea025`

> Primera pieza de la **etapa 2** de F8 ([`veh-shared-001.md`](veh-shared-001.md)). Regla del user
> (15-09): *«si Marta coge el coche y se va, la geocerca de Antonio debería desaparecer y ponerse
> donde aparcó Marta; todos comparten geocerca»*.

## Problema

Hoy la geocerca es de la SESIÓN: cada móvil vigila el aparcamiento que hizo ÉL. En un coche que
conducen varios eso deja a los demás vigilando un sitio donde el coche ya no está.

Concreto, con el coche compartido de la etapa 1 ya en master: Antonio aparca el lunes (su sesión,
su valla). El martes Marta coge el coche y lo deja tres calles más allá. El móvil de Antonio sigue
con la valla del lunes y con **su sesión activa**, así que:

1. su tarjeta de Home dice que el coche está donde ya no está,
2. su red de seguridad vigila un pin fantasma,
3. y si Antonio pasa andando por la calle del lunes, su valla dispara un EXIT que **arma detección
   y puede publicar la plaza de un coche que no se ha movido** — falso positivo con el nombre de
   otra persona encima.

El (3) es el que duele, y es el que la doctrina de fallo asimétrico prohíbe.

## Doctrina — lo que esto NO puede romper

- **El evento NOMINA, el movimiento MEDIDO confirma.** Una valla heredada del grupo no puede
  nominar ni armar nada: que la valla del coche esté en mi móvil no prueba que yo conduzca. Quien
  libera la plaza sigue siendo quien **midió** la conducción (su BT o su tracker).
- **Carriles separados.** Esto no mete señales BT en el scoring del Coordinator: la valla compartida
  no entra en ningún scoring, es un objeto de UBICACIÓN.
- **Fallo asimétrico.** Superseder una sesión propia por error borra un aparcamiento real, así que
  solo se cede ante un hecho más nuevo y de OTRA persona (abajo).
- **La decisión en un use case puro**; el servicio y el worker solo hacen I/O.

## Diseño

### 1 · Una valla compartida tiene su propio espacio de nombres

`shared:<carId>`, en `SharedCarFence` (`domain/detection/fence/`). El id importa porque **ninguna
sesión local la respalda**, y hay tres sitios que hoy deciden por «¿hay sesión para esta valla?»:

| Quién | Qué haría sin el namespace | Qué hace ahora |
|---|---|---|
| `EvaluateGeofenceExitUseCase` | clasificarla como HUÉRFANA y borrarla | la reconoce y la ignora: ni huérfana ni salida real |
| `cleanOrphanEnterFences` (carril ENTER) | lo mismo, en el primer ENTER | igual: la salta |
| `GeofenceJanitorWorker` | no re-registrarla nunca (barre sesiones) | reconcilia también las compartidas, desde la caché `shared_cars` |

### 2 · El veredicto: `EvaluateSharedCarWatchUseCase`

Puro, síncrono, en `domain/usecase/sharedcar/`. Entra el estado del espejo, mi uid y mi sesión
activa para ese coche; sale un plan:

- **`Mine`** — el espejo dice que lo aparqué yo, con mi sesión: la valla de mi sesión ya hace el
  trabajo; si había una compartida, se quita. Cero duplicados.
- **`Group(sessionId, lat, lon)`** — lo aparcó otro: registro la valla compartida ahí.
- **`None`** — el grupo dice que el coche no está aparcado: fuera la valla compartida.

Y, por separado, **si mi sesión local quedó obsoleta**: solo cuando el espejo nombra una sesión
DISTINTA, de OTRA persona y **más nueva** que la mía. Entonces mi sesión se cierra como *superseded*
(`clearActiveByVehicle`, que ya existe: termina la sesión sin publicar plaza). Las tres condiciones
son el freno: sin la de «más nueva», una carrera de sincronización me borraría un aparcamiento que
acabo de hacer offline.

### 3 · Quién ejecuta el plan

`SharedCarFenceReconciler` (data), llamado desde donde ya se reconcilian vallas:
`GeofenceJanitorWorker` (periódico de 15 min + arranque + post-sync). Sin superficie nueva y sin un
segundo sitio que registre vallas.

⏳ **Fuera de alcance, y declarado**: despertar el móvil dormido en el instante en que Marta aparca.
Eso es un mensaje de DATOS por FCM contra los tokens de `members/{uid}`, y va con el push del grupo
(«Marta ha aparcado en…»), que es la otra pieza de la etapa 2. Sin él, la valla de Antonio se mueve
en la siguiente pasada del janitor o al abrir la app — correcto, solo que no instantáneo.

## Criterio de éxito

1. Marta aparca en B ⇒ en el móvil de Antonio la valla de A desaparece y aparece una en B.
2. El EXIT de una valla compartida **no arma detección, no publica plaza y no la borra por huérfana**.
3. La sesión obsoleta de Antonio se cierra sin publicar plaza; su historial la conserva.
4. Una sesión mía recién hecha (offline, espejo aún sin enterarse) **no** se supersede.
5. Suite entera en verde (`./gradlew testAll`).

## Consumidores auditados

`grep` de los 8 sitios que crean vallas y los 9 que las borran. Una valla del grupo solo puede
llegarles por su id, así que la pregunta de cada fila es: *¿este sitio decide algo mirando el id o
la sesión que lo respalda?*

| Sitio | Veredicto |
|---|---|
| `EvaluateGeofenceExitUseCase` (intake EXIT) | ✅ **cerrado** — las vallas del grupo salen del lote antes de clasificar: ni huérfanas ni salidas. Con test de que una huérfana de verdad en el mismo lote sí se barre |
| `cleanOrphanEnterFences` (carril ENTER) | ✅ **cerrado** — mismo veto, con su test |
| `GeofenceJanitorWorker` | ✅ **cubierto** — ahora reconcilia también las compartidas, después de las propias y sin poder tumbar el run |
| `ConfirmParkingUseCase`, `UpdateParkingLocationUseCase`, `SwapActiveVehicleFencesUseCase`, `ProcessConfirmedDepartureUseCase`, `FinalizeDeducedDepartureUseCase`, `ReleaseActiveParkingSessionUseCase` | ✅ **cubierto por construcción** — todos operan sobre `session.geofenceId`, que nunca es un id `shared:` |
| `ParkingSafetyNetWorker` (crea valla) | ✅ **cubierto por construcción** — re-registra la valla de SU sesión |
| `ParkingSafetyNetWorker` (despertado por el ENTER) | ⚠️ **exento con razón** — el ENTER de una valla compartida despierta al worker cada vez que el miembro vuelve al coche. Su barrido ya la ignora y su chequeo es el mismo que el periódico, así que el coste es una pasada de red de seguridad de más. Filtrarlo en el receptor metería una decisión en un relé que está escrito a propósito para no tener ninguna |
| `GeofenceManager.removeAllGeofences` (sign-out) | ✅ **correcto tal cual** — cerrar sesión debe llevarse también las vallas del grupo [SESSION-ISOLATION-001] |
| `IosDetectionController` | ⚠️ **no cableado, declarado** — iOS registra vallas desde sesiones y nadie llama ahí al reconciliador. El veredicto es `commonMain` y está listo; conectarlo entra cuando iOS se descongele (aparcado por el user el 07-09) |
| `UserParkingDao.clearActiveByVehicle` | ✅ **reutilizado** — ya existía como cierre de *supersede*: termina la sesión sin publicar plaza. No hace falta una razón de cierre nueva |

## Bitácora

- **15-09** · Cerrado en rama. Tres piezas (namespace, veredicto puro, reconciliación en el
  janitor), 11 tests nuevos, suite entera en verde con `testAll`. Sin `detectionPath` nuevo: este
  camino no confirma ningún aparcamiento.
- **15-09** · Abierto. Recon: la valla se registra por SESIÓN (`geofenceId == sessionId`) desde
  confirm, swap y janitor; el EXIT resuelve `getActiveSessionByGeofence` y lo que no resuelve lo
  clasifica el evaluador puro como huérfano. Ahí es donde entra el namespace.
