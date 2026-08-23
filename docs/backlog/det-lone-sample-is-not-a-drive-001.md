# DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001 · Un solo fix de velocidad todavía abre la sesión entera

**Estado:** 🔵 Abierto, sin código · descubierto por el replay de
[DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001](det-exit-fix-cannot-prove-its-own-exit-001.md)

## Problema

`ParkingDetectionState.hasEverReachedDrivingSpeed` — la bandera que desarma **todos** los guards
anti-caminata (false-ENTER abort, presupuesto de no-movimiento, la puerta de `steps+egress`) — la
sigue poniendo **UN solo fix**:

```kotlin
// CoordinatorParkingDetector.kt
val hasJustReachedSpeed = !s.hasEverReachedDrivingSpeed &&
        location.speed >= config.minimumTripSpeedMps &&
        credibleSpeedFix                       // acc ≤ minGpsAccuracyForDriving
```

No hay corroboración: ni ventana, ni traza, ni desplazamiento. `credibleSpeedFix` sólo mira la
precisión declarada — y un espejismo de interior declara precisiones excelentes.

Está admitido por escrito en el propio fichero (`DET-DRIVE-PROOF-001`):

> «Arm seeding and session lifecycle (hasEverReachedDrivingSpeed) are **deliberately untouched**:
> the event nominates, only corroborated movement CONFIRMS.»

DET-DRIVE-PROOF-001 endureció la *estadística* de sesión (`maxSpeedMps`, `driveProven`) y dejó la
*bandera de ciclo de vida* como estaba.

## Evidencia medida (no inferida)

Replay `TRACE_HOUSE_MIRAGE_001` (field 22-08-2026 20:50, Oppo, teléfono quieto dentro de casa),
armando **`Unverified`** — es decir, ya con el fix de
DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001 puesto:

```
movedAfterFirstFix = true      ← el primer fix (8,2 m/s, acc 5,6 m) abre la sesión
outcome            = ended     ← NO aborta por false-ENTER: ese guard ya estaba desarmado
saves              = 0
```

La plaza fantasma no se plantó — pero **no** por el guard que debía pararla. Lo que salvó ese
replay fue la etiqueta del arm: `self_observed` mantiene despierto el guard de plausibilidad de
re-aparcamiento en `ConfirmParkingUseCase`, del que las etiquetas verificadas se libran
(`ArmEvidence.isVerifiedLabel`). Es decir: **quedó un solo guard entre el espejismo y el pin.**

## Doctrina violada

*El evento NOMINA, solo el movimiento MEDIDO confirma.* Un sample Doppler aislado no es movimiento
medido, y aquí abre la sesión entera.

## Diseño (esbozo, sin decidir)

Elevar `hasJustReachedSpeed` al mismo listón que ya usa `driveProven`: corroboración por traza
(`corroboratesDrive`) o por desplazamiento desde el pin (`EvaluateShortHopDriveProofUseCase`).

⚠ **No es un cambio pequeño.** `hasEverReachedDrivingSpeed` es la bandera de BUG-SHORT-TRIP («dio
la vuelta a la manzana»): subir el listón puede perder viajes cortos reales cuyo stream arranca
tarde. Antes de tocarlo hay que pasar **todas** las trazas del replay harness y mirar cuáles
dependen de que un fix suelto abra la sesión.

## Criterio de éxito

- `TRACE_HOUSE_MIRAGE_001` con arm `Unverified` acaba en `aborted_false_enter`, no en `ended`.
- Ninguna traza correcta del harness (Calle Gavia, Camelias, Enamorados, Galeote, Supermarket,
  Motorway) pierde su confirmación.
