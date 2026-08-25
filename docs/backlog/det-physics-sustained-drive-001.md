# DET-PHYSICS-SUSTAINED-DRIVE-001 · "¿condujo esta sesión?" se pregunta en un solo sitio

**Estado:** ✅ Done · mergeado a master 2026-08-25 · refactor sin cambio de conducta.

## Problema

Al cerrar `DET-ASSERTION-OUTRANKS-INFERENCE-001` quedaron **tres** escrituras inline de la misma
pregunta:

| Sitio | Expresión |
|---|---|
| `EvaluateParkingDecisionUseCase` (pre-existente) | `input.sustainedDrivingMs >= config.sustainedDriveProofMs` |
| `CoordinatorParkingDetector` → `sessionSawDriving` del confirm | `_detectionState.value.provenDrivingBandMs >= config.sustainedDriveProofMs` |
| `CoordinatorParkingDetector` → `assertedPinBlocksRelocation` | `state.provenDrivingBandMs >= config.sustainedDriveProofMs` |

Tres comparaciones que hoy coinciden por suerte, cada una libre de derivar hacia otra cantidad. Y la
cantidad es lo que aguanta el peso: tiene que ser la banda ya **promovida** por la prueba de
conducción, nunca el acumulador crudo y nunca un PICO. Leer el pico es literalmente el fallo que
produjo el FP del 24-08 — un único fix de 5,33 m/s de 25 cruzó `minimumTripSpeedMps`.

Además, los dos guards de `ConfirmParkingUseCase` (aserción y repark) leían **la misma fila** de la
BD con dos `getActiveSessionByVehicle` distintos.

## Doctrina violada

*Sistemas, no parches* — un invariante vive en UN sitio. Y el patrón que la fase 1 del refactor ha
aplicado a todo lo demás (`SpeedBandClock`, `GapDoubt`, `EffectiveDriving`, `SessionOutcome`): un
predicado compartido por 2+ veredictos es una función pura con nombre, no una expresión repetida
[DET-VERDICT-NOT-PREDICATE-001].

## Diseño

- `sustainedDriveWitnessed(provenBandMs, proofMs)` en `physics/SpeedBandClock.kt` — el fichero que
  ya es dueño de la aritmética de la banda, en vez de un fichero nuevo. Los tres sitios lo llaman.
- `ConfirmParkingUseCase`: las dos precondiciones pasan a tener nombre (`assertionGuardApplies` /
  `reparkGuardApplies`) y la sesión activa se lee **una vez**, sólo si alguno de los dos aplica —
  el confirm manual/BT/verificado sigue sin tocar el repositorio. Ninguna regla cambia.

## Por qué ahora

La fase 2 del refactor (`DET-STATE-SESSION-TELEMETRY-001`) está sacando el estado de sesión a
sub-estados. Cuando `DriveProof` salga, lo que cambia es **qué alimenta** esta función, en un sitio,
en lugar de tres comparaciones que hay que encontrar.

## Revisado y NO tocado, a propósito

- `BACKFILL_ARRIVAL_UNWITNESSED` (de `DET-DEPARTURE-IS-NOT-ARRIVAL-001`) **no** pasa a
  `SessionOutcome`: ese tipo modela la etiqueta TERMINAL de una sesión, y esto es una traza de
  decisión, el mismo vocabulario que su vecino `BACKFILL_DEFERRED_TO_NUDGE`.
- `SavedParkingShape` dice explícitamente que nada lo adopta todavía (fase P1.10 de veredictos).
  Adoptarlo aquí sería adelantar mal ese trabajo.

## Criterio de éxito

Conducta idéntica: 1.555 tests verdes (1.552 + 3 nuevos sobre el predicado), mock y prod compilando.
El día que `provenDrivingBandMs` se mueva de sitio, sólo hay un `>=` que reubicar.
