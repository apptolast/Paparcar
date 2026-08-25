# DET-STAGE-VEHICLE-ATTRIBUTION-001 · P3.7 — el efecto que describía el plan no podía existir

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STAGE-VEHICLE-ATTRIBUTION-001-p3-7` ·
worktree `../Paparcar-stage-7`

Paso **P3.7**, la única etapa que necesita I/O. Sigue a `495d25ce` (P3.6).

## Qué mueve

[VEH-ACTIVE-FENCE-001][DET-BT-OWNERSHIP-001] De quién es el coche, resuelto una vez, en el primer fix
a velocidad de conducción. **Una plaza sin dueño no se puede guardar**, y por eso supera a todas las
vías de confirmación — es la adyacencia que fija
`should_resolve_the_vehicle_before_confirming_within_the_same_fix`, el mismo test que cazó el bug del
snapshot en P3.6.

## El aviso del plan disparó, y tenía razón

El plan decía: *la etapa decide en puro y PIDE el lookup; si esto se complica, el efecto está mal
definido, no la regla sobre repositorios.*

**El efecto descrito no puede existir.** La política necesita la RESPUESTA del lookup para decidir:
lee qué vehículo está activo y si el nominador está emparejado por Bluetooth. La secuencia es
**pedir → decidir**, no decidir → pedir.

Así que el efecto pide **HECHOS** en vez de anunciar un veredicto, y la decisión se queda donde ya
estaba: `VehicleFenceOwnershipPolicy.resolveSessionVehicleId`, pura y más antigua que este refactor.

### Lo que aporta la etapa, sin adornos

**Se declara la puerta, se declara su precedencia y se nombra la I/O.** No hace la decisión más rica
— hace que **el LUGAR de la decisión en el orden sea un valor y no un número de línea**.

Conviene decirlo así de plano: es el paso de la fase que menos añade, y el que más aclara sobre el
diseño del andamio.

## Un efecto ya puede terminar la pasada por su cuenta

El abort por falta de vehículo se descubre **DESPUÉS** del lookup que la etapa pidió, así que ninguna
etapa podría haberlo declarado por adelantado. `runStageEffects` devuelve un `StagePass` y el runner
lo combina con el `stopsIteration` del veredicto.

## `nominatingVehicleId` se muda a `SessionTelemetry`

Vivía como parámetro del bucle de detección. Eso está bien mientras solo lo lea el bucle, y deja de
estarlo en cuanto lo lee una **ETAPA** — una etapa ve el estado y nada más, por diseño.

Es identidad de sesión en el mismo sentido exacto que `armEvidence`: fijada en el arm, leída una vez,
nunca re-derivada.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

Ni un assert editado. **6** de precedencia (P0.1) verdes, **18** replays verdes.
**1.629 tests**, 0 fallos. `assembleMockDebug` ✅.

**Siete de diez etapas movidas.** Quedan `NoMovementBudgetStage`, `FalseEnterAbortStage` y
`HoldResolutionStage` — las tres PRIMERAS de la precedencia, que es exactamente lo que tenía que
quedar: la fase se movió en orden inverso a propósito.

Siguiente: **P3.8**, `NoMovementBudgetStage` [DET-ZOMBIE-PROBE-001][DET-JAM-WINDOW-001].
