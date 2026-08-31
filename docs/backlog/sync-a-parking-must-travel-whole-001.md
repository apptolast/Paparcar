# SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001 · un aparcamiento viaja entero, o el documento miente

**Estado:** ✅ Done · mergeado a master por squash · ⏳ **sin validar en campo** · ⚠️ el cambio de
iOS no está compilado en este banco (lo confirma el CI de macOS)

## Problema

Salió tirando del hilo de un pin del field 30-08 que había **perdido su radio de duda**. Doc real
`users/itmGbBxaz8…/parkingHistory/825dcb60-…`: `detectionPath=unattended_zone_gap_anchor`,
`reliability=0.5`, y **`zoneRadiusMeters = null`** — mientras el log de su confirmación (30-08
01:49:32) registró `zoneRadius=250.0`.

Consecuencia para el usuario: **el pin pierde su diana**. El marcador de duda depende SOLO de
`zoneRadiusMeters != null` (`PaparcarMapView.kt:287`), así que una zona de 250 m que el sistema
midió se dibuja como **pin exacto**. Es la dirección del fallo asimétrico que nunca tomamos:
afirmar más certeza de la que hay.

## Causa raíz — tres patas, todas verificadas

1. **`SaveNewParkingSessionWorker.buildRequest`** serializaba **15 campos** al `workDataOf` y
   `toParkingHistoryDto` reconstruía el DTO **sólo desde ahí**. **No viajaban**: `zoneRadiusMeters`,
   `spotType`, `address`, `placeInfo`, `routePolyline`, `routeSnapped`,
   `routeInferredSpans`/`Resolution`, `routeDistanceMeters`, `endedAtMs`, `publishedSpot`,
   `updatedAt`.
2. **`RemoteUserProfileDataSourceImpl:41`** → `document(session.id).set(session)` — **`set()` sin
   merge**. Lo que no viaja **no se omite: se escribe a null/false** encima de lo que hubiera.
3. **`UserParkingReconcile.onTakeRemote`** rescataba 7 campos del row local y `zoneRadiusMeters`
   **no estaba**, así que el null remoto entraba en Room y el valor bueno se perdía también en el
   dispositivo.

Y `updatedAt` salía **`0L`** en cada escritura del worker: 0 pierde toda comparación
Last-Write-Wins, así que ese documento **nunca podía ganar** a un row local obsoleto.

## Doctrina violada

- El código **pedía la paridad por escrito**: `:101` *"keep in lockstep with ParkingHistoryDto"* y
  `:39` *"**every field** of the new session"* — que era falso. **Un comentario no es un chequeo**,
  igual que en [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001] faltaba el TESTIGO.
- **Ya se había roto tres veces**: `MAPPER-001` (`detectionReliability`), `MAPPER-002` (`vehicleId`)
  y ahora `zoneRadiusMeters` — cuyo propio KDoc en el DTO dice que la duda viaja a remoto
  [DET-DOUBT-REACHES-REMOTE-001]. **Las dos veces anteriores el arreglo fue añadir el campo a la
  lista**, que es exactamente lo que garantizaba una cuarta.

## Diseño

**Se mata la lista, no se le añade una fila.** `buildRequest` serializa el `ParkingHistoryDto`
entero a una única clave (`KEY_SESSION_JSON`) y `doWork` lo deserializa. Un campo añadido al DTO
viaja **porque es parte del DTO**, no porque alguien se acuerde. El worker sigue siendo
autocontenido (sin lecturas de Room), que era el diseño original y sigue siendo correcto.

Además:
- `updatedAt` se estampa en el **encolado** (el momento de la edición local) — arregla el LWW.
- `UserParkingReconcile` pasa a rescatar `zoneRadiusMeters` del row local cuando el remoto trae null
  (docs escritos antes de este arreglo).

⚠️ **`set()` se deja SIN merge, deliberadamente.** Con el DTO completo el `set()` es correcto *y
necesario*: es lo que permite LIMPIAR un campo que legítimamente pasa a null. Poner `merge = true`
taparía el síntoma y haría invisible el próximo campo que se caiga. El defecto nunca fue el `set()`
— fue mandarle un DTO mutilado.

## Criterio de éxito

Un test que compare el **DTO ENTERO** que llega al data source contra el que produce el mapper, de
forma que un campo nuevo quede cubierto el día que se añade, sin que nadie recuerde que el test
existe. Validado por falsación.

## Consumidores auditados

- ✅ **`SaveNewParkingSessionWorker`** — cerrado (el defecto).
- ✅ **`UserParkingReconcile`** — cerrado (rescata el radio).
- ✅ **`IosParkingSyncScheduler:54`** — **estaba expuesto a la mitad del defecto y se arregla aquí**:
  pasa el DTO completo (no usa workData, así que no perdía campos) pero llamaba a
  `toParkingHistoryDto()` **sin argumento** → `updatedAt = 0L`, el mismo agujero de LWW. Mismo
  invariante, arreglado en la misma tarea.
- ⚪ **`UpdateParkingSessionAddressAndPlaceWorker`** / **`ClearActiveParkingSessionWorker`** —
  exentos con razón: usan `update()` **parcial** (`{address, placeInfo}` / `{isActive}`), no pueden
  borrar nada más.
- ⚪ **`pushPendingParkingSessions`** (`UserParkingRepositoryImpl:157`) — exento: ya usaba el mapper
  completo. Es la razón por la que otros pines del mismo día conservan su radio: su última escritura
  vino de aquí y no del worker.
- 🔴 **`ReportSpotWorker` — MISMO PATRÓN, NO cerrado.** Tiene **17 constantes `KEY_`** y publica el
  `Spot` comunitario. No lo toco en este ticket porque es otro agregado y otro DTO, pero está
  expuesto al mismo modo de fallo. → follow-up `SYNC-A-SPOT-MUST-TRAVEL-WHOLE-001`.
- ⚪ `usersCollection().set(profile)` y `vehiclesCollection().set(vehicle.copy(updatedAt = nowMs()))`
  — fuera de alcance (otros agregados). El de vehículos ya estampa `updatedAt`; el de perfil no.

## Estado de verificación

- ✅ `:shared:testDebugUnitTest` → **2.006 tests** (2.004 de base + 2 nuevos).
- ✅ **Falsación hecha**: reintroducido el bug (`zoneRadiusMeters` fuera del payload) →
  `SaveNewParkingSessionWorker carries EVERY dto field to the data source` se pone **ROJO**.
- ✅ `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin`.
- ⚠️ **iOS no se compila en este banco** (lo valida un compañero) — el cambio de `IosParkingSyncScheduler`
  es de una línea + import, pero **no está compilado aquí**. El job de CI de macOS
  [CI-IOS-COMPILES-ON-A-MAC] es quien debe confirmarlo.
- ⏳ Sin strings nuevos, sin pantalla ni estado nuevo → no toca locales ni Dev Catalog.
- ⏳ **Los documentos ya dañados NO se reparan solos**: el reconcile ahora protege el radio local,
  pero un doc remoto que ya salió a null seguirá a null hasta que ese pin se vuelva a escribir desde
  un dispositivo que aún tenga el valor en Room. Sin backfill de datos (estado pre-beta).
