# SYNC-RECONCILE — UserParking / Profile

**Fecha:** 2026-07-03 (diferido) → **2026-07-05 RESUELTO (UserParking)**
**Estado UserParking:** ✅ IMPLEMENTADO — rama `feature/SYNC-RECONCILE-USERPARKING-001` (commit `450a6b58`).
El field-test del 2026-07-05 (Redmi) demostró que el síntoma SÍ molesta (resurrección de sesión
terminada + dos activas en la nube), disparando el trigger de abajo. Sistema completo: entidad+DTO
`updatedAt`/`pendingSync` (MIGRATION_11_12, DB v12), estampado en toda mutación INCLUIDAS las
desactivaciones, `syncFromRemote`→`reconcileParkingSessions` (LWW, supersede el stopgap
SYNC-UP-GUARD-001), drenador `pushPendingParkingSessions`. Pendiente solo: merge + field-test device.
**Estado Profile:** sigue DIFERIDO (impacto bajo — ver §Profile).
**Relacionado:** `docs/backlog/sync-reconcile-001-2026-07-01.md`, [[project_sync_reconcile_vehicle]], [[project_det_falseneg_2026_07_04]]

## Contexto
El reconcile inbound (fin del remote-wins ciego que revertía ediciones offline) se aplicó a las
entidades que el usuario **edita a mano** y donde el revert era **permanente y visible**:
`vehicles` ✅ (`c24fa976`) y `zones` ✅ (`89d4c650`). Este doc explica por qué **UserParking** y
**Profile** se dejaron fuera, y qué haría falta si algún día se retoman.

---

## UserParking — por qué se difiere (paso a paso)

### 1. Los writes YA tienen entrega garantizada (WorkManager)
`UserParkingRepositoryImpl` no escribe a Firestore de forma directa/bloqueante: encola **WorkManager**
vía `ParkingSyncScheduler`:
- `saveNewParkingSession` → Room + `enqueueSaveNewParkingSession`
- `clearActiveParkingSession` (soltar plaza) → Room + `enqueueClearActiveParkingSession`
- `updateParkingSessionPosition` (mover pin) → Room + `enqueueSaveNewParkingSession`
- enrichment (dirección/POI) → Room + `enqueueUpdateParkingSessionAddressAndPlace`

Los workers (p. ej. `ClearActiveParkingSessionWorker`) llevan **`Constraints NETWORK_CONNECTED` +
backoff exponencial 30 s**. WorkManager persiste el job a disco → **corre aunque cierres la app**, en
cuanto haya red, reintentando. Es decir: **la entrega a la nube ya es más robusta que en
vehículos/zonas** (que dependen de reabrir la app). Por eso aquí **no hay button-hang** (no se hace
`await`) y **no hace falta drenador**.

### 2. `syncFromRemote` NO borra a ciegas
A diferencia de vehículos/zonas (que hacían `deleteByUser` + `upsertAll`), el de parking hace **solo
`upsertAll(remote)`** — sin `deleteByUser`. Consecuencia clave:
- Una sesión **creada offline** y aún sin subir → **NO está en el remoto** → `upsertAll` no la toca →
  **se conserva**. Nunca se pierde "dónde aparqué".

### 3. El ÚNICO hueco: `upsertAll` pisa una EDICIÓN offline con el remoto stale
Si una sesión existe **local Y remoto**, `upsertAll` (REPLACE) sustituye la fila local por la remota.
Si el remoto va atrasado (el worker aún no subió el cambio local), se pisa la edición local:
- Soltar plaza offline → Room `isActive=false`. Si el sync lee el remoto **antes** de que el worker
  suba el clear → mete `isActive=true` → **la sesión aparcada "reaparece" activa**.
- Igual con mover el pin: vuelve a la posición vieja un momento.

### 4. Es TRANSITORIO y se AUTO-CURA (no hay pérdida de datos)
- `syncFromRemote` **solo corre en cold-start online** (y offline aborta / con RETRY-GATE ni siquiera
  se ejecuta). Fuera de eso no hay merge → no hay pisado.
- El worker del clear **no lee Room**: hace `update(isActive=false)` en Firestore por `sessionId`. Así
  que, aunque Room se haya pisado a `true`, el worker **igual deja el remoto en `false`**.
- **Siguiente cold-start** → `syncFromRemote` lee el remoto (ya `false`) → Room corregido.
- Nunca se pierde el historial: `upsertAll` no borra, y el dato de la sesión siempre está (Room +
  WorkManager). El único síntoma es un **flag `isActive` transitoriamente incorrecto**.

### 5. Arreglarlo tocaría subsistema sensible
Aplicar el LWW por `updatedAt` obliga a estampar/escribir `updatedAt` en **los workers de detección
(androidMain)** y en el `ParkingSyncScheduler` — subsistema **field-tuned**, lleno de `[BUG-*]`,
validado en device. Meterle mano sin field-test es más riesgo que el bug transitorio que taparía.

## Situación real (walkthrough)
- **Aparcar offline, no reconectar nunca en el móvil A, cambiar a móvil B**: la sesión vive en A hasta
  que A sincronice (límite inherente de offline-first, NO un bug del reconcile; además el coche es tuyo
  y vuelves a él con A). No hay pérdida ni corrupción.
- **Soltar plaza (lo normal es automático al conducir, con la app en background)**: el clear se encola;
  cuando abres la app ya se subió hace rato → sin pisado. El pisado solo pasaría si **lanzas la app en
  frío justo en los ~segundos** entre reconectar y que corra el worker → prácticamente nunca, y se
  auto-cura al siguiente arranque.

**Resumen**: sin pérdida de datos en ningún escenario; el único efecto posible es una plaza soltada que
se ve "activa" unos instantes, en una ventana de timing muy estrecha, y se corrige sola.

## Profile — por qué se difiere
`user_profile.defaultVehicleId` ya lo cubre de facto el reconcile de vehículos: el vehículo activo se
decide por `vehicles.isActive` (reconciliado), no por el puntero del perfil (`getActiveVehicle` solo
cae a `defaultVehicleId` como fallback si la tabla perdiera `isActive`). Impacto bajo, no urgente.

## Trigger para retomar
Solo si un field-test real muestra que "la plaza soltada reaparece activa" ocurre y molesta. Entonces:
1. `UserParkingEntity` + `updatedAt` (+ `pendingSync` opcional) + migración aditiva (v11).
2. `ParkingHistoryDto` + `updatedAt`; estamparlo en CADA worker/scheduler que escribe a Firestore.
3. `syncFromRemote` → LWW: quedarse con local si `local.updatedAt > remote.updatedAt`.
4. Tests + **field-test en device** (subsistema sensible).
