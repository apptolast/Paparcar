# UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001 · El stepper del peek camina VEHÍCULOS, no sesiones

**Estado:** ✅ Done (28-08-2026) — en master vía squash; instalado y verificado en Redmi y Oppo (sha256 device↔local coincidente, arranque sin crash). El hash vive en MEMORY.md.

## Problema

El stepper `‹ › ×` del peek [UI-PEEK-STEPS-BETWEEN-PINS-001] tiene dos carriles: plazas
(`browsableSpotIds`) y coches (`sessionStep`). El carril de coches camina `activeSessions` — solo
los APARCADOS. Consecuencias:

- Con vehículos pero **ninguno aparcado**, no hay nada entre lo que saltar: la modal de
  `AddingParkingPeek` (abierta desde el chip "Aparcar") es un callejón sin salida sin flechas.
- Con un coche aparcado y otro sin aparcar, el `›` del `ParkingPeek` no ofrece el segundo coche:
  para marcarle el aparcamiento hay que cerrar el peek y buscar su chip en la lista.

Petición del user (28-08): aunque en Home no haya vehículos aparcados, si hay vehículos se puede
saltar de modal; el vehículo sin sesión de aparcamiento muestra la modal de addParking.

## Doctrina violada

Ninguna regla escrita — es un hueco de alcance del stepper original. Sí aplica el espíritu de
[UI-PEEK-STEPS-BETWEEN-PINS-001]: "ir de un coche al otro no debe exigir cerrar el peek y cazar
el chip". Hoy eso solo se cumple si ambos están aparcados.

## Señales / datos disponibles

`HomePeekSlice` ya lleva `vehicles` + `activeSessions` (el join vehículo→sesión se reconstruye
gratis) y `drivingMeta` (para ordenar con `vehiclesRowOrder`, el mismo orden que la tira
"TUS VEHÍCULOS"). El chip ya define qué es "abrir un vehículo sin sesión":
`EnterAddParkingMode(initialGps = userGps, targetVehicleId = …)` (`HomeSheetContent.cardClick`).

## Diseño

**El invariante en UN sitio: el carril de coches del stepper es la lista ORDENADA de VEHÍCULOS
(`vehiclesRowOrder`), y cada vehículo resuelve a SU modal — con sesión → `ParkingPeek`
(seleccionar), sin sesión → `AddingParkingPeek` (crear).**

- `HomePeekSlice.steppableVehicleIds` (materializada en `toPeekSlice()`, como `browsableSpotIds`)
  sustituye al carril de sesiones; `vehicleStep(currentVehicleId)` da los vecinos. `sessionStep`
  desaparece.
- Acción nueva `HomeSheetAction.StepToVehicle(vehicleId)` sustituye a `SelectParking(sessionId)`
  (su único consumidor era este stepper). La traducción vive en el punto único
  (`HomeSheetSection`): sesión activa del vehículo → mismo lambda que tocar su marcador; sin
  sesión → `EnterAddParkingMode` idéntico al chip + cámara al GPS del usuario (el pin no debe
  nacer sobre el aparcamiento del coche desde el que se salta; el GPS es además su coordenada de
  fallback).
- `ParkingPeek` pasa a `step = vehicleStep(parking.vehicleId)`. `AddingParkingPeek` (solo CREATE)
  recibe el mismo stepper; los saltos de modo (mode ↔ selection) ya los hace el ViewModel con
  `clearedModeFields()` en ambos sentidos.
- `PeekState.AddingParking` gana `vehicleId` (identidad, no dato — [BUG-PEEK-JITTER-001]): sin él,
  dos coches sin aparcar producen estados iguales y `AnimatedContent` no anima el paso. La
  dirección del slide sale de `steppableVehicleIds` para los 4 combos aparcado↔sin aparcar.

**Exenciones deliberadas del stepper en `AddingParkingPeek`:**
- `isEditing` — corregir el pin de UNA sesión es un flujo enfocado; saltar a otro coche a mitad no
  significa nada.
- `fromDetectionNudge` — saltar a otro coche y volver perdería la provenance de detección del pin
  [DET-NUDGE-PIN-PROVENANCE-001]; el nudge es una pregunta sobre UN coche.
- `isSaving` — flechas nulas mientras el write está en vuelo, como el CTA.

## Criterio de éxito

- 2 vehículos, 0 aparcados: chip "Aparcar" → `AddingParkingPeek` con `›`; el paso abre la modal
  addParking del otro coche con slide horizontal.
- 1 aparcado + 1 sin aparcar: `ParkingPeek` ofrece `›` → `AddingParkingPeek` del segundo; `‹`
  vuelve al aparcado.
- 1 solo vehículo: sin flechas (como antes).
- Tests unitarios de `vehicleStep`/`steppableVehicleIds` en `HomeSlicesTest`; galería mock con las
  variantes nuevas.

## Consumidores auditados

- `HomeSheetAction.SelectParking` — único consumidor: stepper de `ParkingPeek` → sustituida por
  `StepToVehicle`. ✅
- `sessionStep` + sus tests (`HomeSlicesTest`) → sustituidos por `vehicleStep`. ✅
- Strings `home_peek_step_prev_car`/`next_car`: EN/DE/NL/PL decían "parked car" — el carril ya no
  es solo de aparcados → reformulados a "car" en esos 4 locales (ES/IT/PT/FR/RO ya decían solo
  "coche"). ✅
- FAB del coche (cicla `activeSessions`): NO usa `sessionStep`, sigue ciclando solo aparcados —
  exento a propósito (el FAB es cámara, no modales). ✅
- Sesión huérfana (vehículo borrado, carrera de delete): antes era paso del carril de sesiones;
  ahora su `vehicleId` no está en `steppableVehicleIds` → `PeekStep.None`, peek sin flechas. Caso
  raro y autoconsistente (tampoco tiene chip) — exento. ✅
- Altura del peek / colapso (`HomeSheetPositioning`): el stepper vive en el clúster trailing del
  header, altura reservada intacta [UI-PEEK-STEPS-BETWEEN-PINS-001] — sin cambios. ✅
