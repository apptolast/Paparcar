# VEH-ACTIVE-FENCE-001 · Pieza 1 — Plan de implementación (vallas solo del activo)

> **Estado**: PLAN. Scaffolding puro ya en la rama (`VehicleFenceOwnershipPolicy` + tests). Wiring
> (Fase 2) DIFERIDO hasta el field-test de las piezas 2/3/4/5 (decisión user 18-07). Alto riesgo:
> toca el registro de geocercas del OS y la atribución del detector. Redactado 2026-07-18.

## Objetivo

Solo el vehículo ACTIVO (o BT-emparejado) tiene geocerca del OS. Aparcar un inactivo no registra
valla; cambiar de activo intercambia vallas; el janitor/restore saltan sesiones de inactivos; y una
sesión de conducción se atribuye a la valla NOMINADORA, no al activo del momento. La sesión del
inactivo conserva pin, TTL y safety-net (que trabaja por sesiones, no por vallas).

## Mapa del lifecycle actual (verificado en código)

**Registro (3):**
- `ConfirmParkingUseCase.kt:234` — `createGeofence(geofenceId = sessionId, …)` en cada confirm
  (auto y manual). `geofenceId == sessionId` (`:190`). **No mira isActive.**
- `UpdateParkingLocationUseCase.kt:52,74` — remove+recreate al mover el pin (mismo id).
- `GeofenceJanitorWorker.kt:80` — re-registra TODAS las sesiones activas (12 h + on-demand). **No
  filtra por vehículo activo.** Es el "cure".

**Remoción (6):** `RevertParkingUseCase:80`, `ProcessConfirmedDepartureUseCase:81`,
`ReleaseActiveParkingSessionUseCase:78`, `ConfirmParkingUseCase:214` (re-park orphan),
`CoordinatorDetectionService:364` (orphan-on-exit), `UpdateParkingLocationUseCase:52`.

**Cambio de activo:** `VehicleRepositoryImpl.setActiveVehicle():206` — **cero interacción con
vallas** (el gap).

**Atribución:** `CoordinatorParkingDetector.kt:681-692` — bloquea `vehicleId` con
`observeActiveVehicle().first()` en el primer fix de conducción. PERO el geofence-exit ya calcula el
vehículo nominador (`EvaluateGeofenceExitUseCase:85` filtra "active-preferred") y lo pasa al trip
(`CoordinatorDetectionService:542` → `TripContext(session.location, session.vehicleId)`). El detector
lo **ignora** y re-deriva del activo. → El fix es preferir el nominador del trip.

**Restore en arranque:** `BootCompletedReceiver:47` + `UserParkingRepositoryImpl.syncFromRemote:114`
encolan el janitor. Si el janitor filtra por activo, el restore hereda el filtro gratis.

## Núcleo puro (Fase 1 — YA en la rama, sin cablear)

`domain/detection/VehicleFenceOwnershipPolicy.kt` (+ `VehicleFenceOwnershipPolicyTest`):
- `shouldOwnFence(vehicleIsActive, isBluetoothPaired)` — la regla de propiedad de valla.
- `planActiveSwap(outgoing, incoming): FenceSwapPlan` — qué quitar / qué registrar al cambiar activo.
- `resolveSessionVehicleId(nominating, active)` — atribución por nominador con fallback al activo.

Los caminos de I/O de abajo CONSULTAN estos veredictos; no reimplementan la regla.

## Fase 2 — Wiring (alto riesgo, por sitio)

Orden de menor a mayor riesgo. Cada sub-paso es un commit con su test.

### 2a · No registrar valla de inactivos en el confirm — `ConfirmParkingUseCase`
- Antes de `createGeofence` (`:234`): mirar el vehículo resuelto (ya se resuelve para `sizeCategory`).
  Si `!shouldOwnFence(vehicle.isActive, vehicle.bluetoothDeviceId != null)` → **saltar** el registro
  (guardar la sesión igual: pin, TTL, `geofenceId` puede quedar seteado pero sin registrar, o
  nulo — decidir: probablemente **dejar `geofenceId` seteado** para que el swap lo re-registre luego,
  y NO llamar a createGeofence). Loguear `GeofenceRegistration(success=false)`? No — no es fallo;
  añadir un log claro "inactive vehicle → no fence by design".
- Riesgo: una sesión de inactivo sin valla no detecta salida por geocerca — POR DISEÑO (la
  cubre el safety-net + el swap cuando se declare activo). Verificar que el safety-net no dependa
  de la valla.
- Test: confirm de vehículo inactivo no llama a `createGeofence`; activo sí.

### 2b · Atribución por nominador — `CoordinatorParkingDetector:681`
- El detector ya recibe el trip con `vehicleId` nominador. Sustituir el bloque `:682`:
  `activeVehicleId = resolveSessionVehicleId(nominatingVehicleId = trip.vehicleId, activeVehicleId = observeActiveVehicle().first()?.id)`.
  Mantener el aborto `aborted_no_vehicle` solo si AMBOS son null.
- Riesgo: **toca el corazón del detector**. Cambio quirúrgico (una asignación), pero altera a qué
  coche se atribuye el pin. Field-test obligatorio.
- Test: con trip.vehicleId presente, se bloquea ese id aunque el activo sea otro.

### 2c · Swap de vallas al cambiar activo — nuevo `SwapActiveVehicleFencesUseCase`
- NO meter I/O de geocercas en `VehicleRepositoryImpl` (el repo no hace I/O de detección). Nuevo
  use case en domain, invocado desde donde se cambia el activo:
  - `VehiclesViewModel.setActiveVehicle` (pantalla Vehículos), y
  - el set-active implícito de Pieza 2 (`HomeViewModel.startDrivingDetection`) y Pieza 4
    (release-inactivo). → Centralizar: que esos 3 llamen a un único punto que hace
    `setActiveVehicle` + `swapFences`.
- El use case: lee sesión activa del saliente y del entrante, `planActiveSwap(...)`, ejecuta
  `removeGeofence` de las salientes + `createGeofence` de las entrantes (resolviendo coords de la
  sesión). BT-emparejados: nunca en `outgoing`.
- Riesgo: medio. I/O de geocercas fuera del detector, idempotente.
- Test: cambiar A→B con ambos aparcados quita la valla de A y registra la de B.

### 2d · Janitor/restore saltan inactivos — `GeofenceJanitorWorker:80`
- Al re-registrar, join a `Vehicle.isActive` (o BT): saltar sesiones cuyo vehículo no deba tener
  valla (`shouldOwnFence`). Necesita acceso al DAO de vehículos en el worker.
- Cubre también el restore de arranque (heredan el filtro).
- Riesgo: bajo-medio. Cuidado: no desregistrar aquí (solo no re-registrar); la remoción vive en el
  swap. El `CureGeofence`/janitor solo debe re-registrar lo que DEBE existir.
- Test: janitor con 1 sesión activa de inactivo no la re-registra; la del activo sí.

### 2e · (revisar) `getActiveSessionByGeofence` y orphan-clean
- Con inactivos sin valla, sus geocercas no existen → no generan EXIT huérfanos. Verificar que
  el orphan-clean (`CoordinatorDetectionService:364`) no borre por error vallas legítimas durante
  el swap (ventana de carrera). El janitor barre la deriva.

## Invariante nuevo

`sesión activa de vehículo (activo ∨ BT) ⟺ valla registrada`. El inactivo-sin-BT: `sesión activa
∧ sin valla`. El swap y el janitor mantienen el ⟺; la Pieza 4 (set-active al declarar/ liberar) es
lo que dispara el swap.

## Estrategia de test
- **Unit puro**: `VehicleFenceOwnershipPolicyTest` (hecho) + tests por sitio (2a-2d) con fakes
  (`FakeGeofenceManager` ya cuenta `removedIds`; añadir conteo de `createdIds`).
- **Replay**: si algún trace de campo del multi-vehículo entra, fijarlo como fixture.
- **Field-test device** (obligatorio, toca detector): 2 coches, escenarios del criterio de éxito.

## Criterio de éxito (del spec)
Con dos coches aparcados: cero FGS espurios de vallas del inactivo, imposible liberar el coche
equivocado (ya cubierto por Pieza 3), y conducir el inactivo tras declararlo (tap manual o
liberar+activar) atribuye el pin al coche correcto.

## Rollback
Cada sub-paso es un commit aislado. 2b (detector) es el más sensible: si el field-test regresa,
revertir SOLO 2b deja el resto (2a/2c/2d telemetría+registro) en pie. El núcleo puro no tiene efecto
hasta que se cablea, así que es inerte ante rollback.

## Dependencias / orden
`FakeGeofenceManager` necesita `createdIds` para testear 2a/2c/2d. Sin cambios de modelo de datos
(el `geofenceId` ya existe). No requiere migración Room.
