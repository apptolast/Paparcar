# DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001 · Un solo fix de velocidad todavía abre la sesión entera

**Estado:** 🔵 Abierto, sin código · descubierto por el replay de
[DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001](det-exit-fix-cannot-prove-its-own-exit-001.md)
· **03-09: las DOS barras candidatas implementadas y MEDIDAS contra el harness — las dos pierden un
viaje real. El diseño de abajo está refutado tal como está escrito** (ver «Lo medido»)

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

---

## Lo medido (03-09-2026) · el aviso del propio doc se cumplió, y con nombre y apellidos

El doc pedía *«pasar TODAS las trazas del replay harness y mirar cuáles dependen de que un fix suelto
abra la sesión»*. Hecho. **Depende una: `TRACE_CASA_GAP_ANCHOR_3008`** — y no es una traza cualquiera,
es un viaje REAL cuyo propio test dice *«the drive was real and the park was real, and losing it
would be its own failure»*.

Se implementaron y midieron las dos barras candidatas. Las dos cumplen el criterio del espejismo y
las dos matan la traza buena:

| Barra | `HOUSE_MIRAGE` (criterio) | `CASA_GAP_ANCHOR` | Suite |
|---|---|---|---|
| **A** · racha de 2 fixes creíbles en banda consecutivos (`driveAuthorizationMinFixes`, hermano de `pinnedAnchorRealDriveFixes`) | ✅ `aborted_false_enter` | ❌ secuencia de preguntas `[]` (esperaba 3) | 29 rojos |
| **B** · geométrica: TODO cruce nacido del stream queda provisional y lo refuta un fix de vuelta dentro del sobre del origen (extiende `DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001`) | ✅ `aborted_false_enter` | ❌ igual, `[]` | 5 rojos |

### Por qué la traza buena se cae, medido sobre sus fixes

`TRACE_CASA_GAP_ANCHOR_3008` tiene **379 fixes y solo 7 creíbles en banda** (≥5 m/s, acc ≤50 m):
índices `[1, 23, 24, 147, 148, 155, 156]`, con racha máxima de **2**.

O sea: su primer fix en banda es el índice **1** y el siguiente no llega hasta el **23**. Con
cualquiera de las dos barras la autorización no se abre (o se revoca) en ese hueco de ~22 fixes — y
la sesión **no llega viva** hasta el 23. Por eso la secuencia de preguntas sale vacía: no es que
pregunte distinto, es que ya no existe.

### Lo que eso reencuadra

**El problema no es solo qué abre la bandera, es qué MATA la sesión mientras la evidencia está
pendiente.** Los guards anti-caminata (false-ENTER abort, presupuesto de no-movimiento) disparan
precisamente cuando la bandera está en `false`, así que **cualquier** barra que retrase la bandera
acorta también la vida de un viaje real con stream pobre. Endurecer la bandera sin tocar la vida de
la sesión cambia un FP de interior por un FN de coche real — y `DET-COARSE-FIX-DRIVE-PROOF-001`
documenta exactamente esa misma población (móviles con accuracy crónicamente mala).

Dirección que señala la evidencia, para quien lo retome: **separar «puede confirmar» de «puede
seguir viva»**. Que el cruce solitario siga abriendo la sesión (para no matarla), pero que lo que
desarma no sea todo a la vez — o que los guards que la matan tengan su propia tolerancia mientras
haya un cruce provisional pendiente.

⚠️ Observación sin explicar, por si alguien repite el experimento: con la variante **B** aparecieron
además 2 NPE en `ArmLabelTest` (`getLabel()`/`getDriveAuthorization()` sobre un `null`) que no se
investigaron — mirarlas antes que nada, porque no se ve la relación.

⛔ **No se dejó código**: la rama se revirtió entera y la suite quedó en 2.161/0. Lo que queda es
esta medición, que es lo que faltaba para poder decidir.
