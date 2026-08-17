# DET-VERDICT-NOT-PREDICATE-001 · un caso de uso por veredicto; los predicados se agrupan con su dueño

**Estado:** 🔵 Regla en `CLAUDE.md` ✅ · consolidación del código ⏳ pendiente (a ejecutar DESPUÉS de validar en campo los 3 fixes del field 16-08) · rama `chore/DET-VERDICT-NOT-PREDICATE-001-usecase-doctrine` · worktree `../Paparcar-usecase-doctrine`

## Problema

Observación del usuario (17-08-2026): *"cada vez que tenemos un bug creas un caso de uso nuevo…
¿cuántos tenemos ya? ¿para lo mismo?"*. Medido:

| Medida | Valor (master `96f948e9`) |
|---|---|
| Casos de uso totales | **45** |
| Sólo `usecase/parking/` + `usecase/detection/` | **28** (3.686 líneas) |
| `CoordinatorParkingDetector.kt` | **2.622 líneas** |
| Predicados PUROS como métodos privados del coordinator | **11** |

Los dos síntomas son el mismo problema: **no había criterio** para decidir si una función pura era
un caso de uso o un método privado. Lo decidía el contexto local — editando dentro del coordinator
salía un `private fun`; creando algo nuevo salía un fichero. Así se llega a 45 casos de uso **y** a
una clase de 2.600 líneas a la vez.

### La duplicación que lo demostró

`DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001` añadió `isCredibleDrivingSpeed` a
`EvaluateShortHopDriveProofUseCase.qualifies`. Con eso, y con los MISMOS parámetros:

```
EvaluateMeasuredDeparture ⊂ ShortHopDriveProof.qualifies
   (qualifies = las 3 mismas cláusulas + el suelo de 400 m)
```

Y en el coordinator `departureProven` se calculaba justo antes de `shortHopProven`, sobre el mismo
fix: el primer fix que cumplía `qualifies` ya ponía `departureProven = true`, así que al llegar la
racha a 3 el gate `if (!departureProven) return false` **no podía decidir nada**. Un caso de uso
entero (79 líneas), un campo de estado, un parámetro de constructor y una línea de
`notifyDepartureConfirmed()` quedaron redundantes en menos de 24 h — y sólo se detectó porque el
usuario preguntó.

## Doctrina violada

**Sistemas, no parches.** Un invariante en UN sitio. Dos evaluadores con los mismos parámetros donde
uno es superconjunto del otro no son dos invariantes: son uno mal repartido.

## La regla (ya en `CLAUDE.md`)

> **Un caso de uso por VEREDICTO, nunca por PREDICADO.** Es veredicto si su resultado se puede citar
> en un diagnóstico (`detectionPath`, `outcome`, `armEvidence`, `sessionOutcome`) o cambia lo que ve
> el usuario. Todo lo demás es un input y vive dentro del veredicto que lo consume; si lo comparten
> 2+ veredictos, función pura de nivel superior en `domain/detection/`.
>
> Y: un predicado **no** se queda como método privado del coordinator sólo porque estuvieras editando
> ese fichero.

## Estado de la consolidación

| Fila | Hoy en master | Hecho 17-08 | Pendiente (este ticket) |
|---|---|---|---|
| ¿Armar, y con qué evidencia? | `EvaluateGeofenceExit` · `EvaluateArEnterArm` · `VerifyDepartureEvidence` | — | **nada**: 3 triggers, 3 lifecycles, se quedan |
| ¿Ha conducido de verdad? | `EvaluateMeasuredDeparture` · `EvaluateShortHopDriveProof` · `corroboratesDrive` · `isCorroboratedVehicleHop` · `isSustainedDepartureFromAnchor` (5) | ✅ **5 → 3** en master `6ae35526`: borrados `EvaluateMeasuredDepartureUseCase` + `departureProven` + su parámetro + su seed | **3 → 1**: absorber los 3 predicados privados del coordinator |
| ¿Dónde está el coche y cuánto me fío? | 7 métodos privados: `isAnchorLocked` · `isAnchorPinned` · `isAnchorWalkEntered` · `isEgressBornAtAnchor` · `hasEgressDisplacement` · `hasKinematicEgressSignal` · `egressExceedsWalkReach` (+`refinedParkLocation`) | — | **7 → 1** value object `AnchorTrust`, calculado una vez por fix |
| ¿Confirmo? | `EvaluateParkingDecision` · `CalculateParkingConfidence` | — | se quedan |
| ¿Qué hago al expirar? | `EvaluateUnattendedParkingSave` (nuevo, master `7bdb6a18`) | ✅ nació ya como veredicto único; absorbió después el limbo de T2 y el veto de T3 al rebasarlos | se queda |
| ¿De qué vehículo es este movimiento? | — | ✅ `domain/detection/HumanPoweredRide.kt` en master `0e37d538`: **función pura, NO caso de uso** (nació como `EvaluateHumanPoweredRideUseCase` y se replegó el mismo día) | se queda |

**No se fusiona:** `EvaluateSafetyNetCheck` (439) con `EvaluateHonestClose` (328) — triggers y ciclos
de vida distintos, daría una clase de 767 líneas, que es justo lo que el usuario no quiere. Y el
carril **Bluetooth** no se mezcla nunca: es doctrina.

## Por qué `AnchorTrust` es el que de verdad importa

No es estética. **El FN del Redmi del 16-08 existió por esto**: `anchorSawStepsAtCapture` se leía en
dos sitios distintos (`isAnchorWalkEntered` y el fallback de zona del timeout) con consecuencias
opuestas, y nadie era su dueño. Un `AnchorTrust` calculado una vez por fix —
`(centro, confianza, cota de duda, motivo)` — hace ese bug imposible por construcción, porque la
duda y su cota nacen juntas.

Los 7 predicados leen todos el mismo subconjunto de estado (`bestStopLocation`, `anchorFrozen`,
`stepCount`, `sessionSawSteps`, los cuatro `*AtCapture`, `egressOriginFix`) y ninguno produce
vocabulario de diagnóstico propio: son textbook predicados de un mismo veredicto.

## Criterio de éxito

- `usecase/detection/` + `usecase/parking/` bajan de 28; `CoordinatorParkingDetector.kt` baja de
  2.600 líneas **sin** que ninguna clase pase de ~450.
- Cero cambios de comportamiento: los 1186/1199 tests actuales pasan **sin tocarlos**, salvo los que
  llaman directamente a un predicado que se muda.
- El vocabulario de diagnóstico no cambia (las traces de Firestore se leen literalmente).

## ⛔ Precondición

**No ejecutar la consolidación antes de validar en campo** DET-WALK-ENTERED-ANCHOR-ZONE-001
(`7bdb6a18`), DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001 (`6ae35526`) y DET-BIKE-NOT-A-CAR-001 (`0e37d538`).
Los tres están **en master pero sin validar en campo**, y tocan exactamente la región del coordinator
que este refactor movería: hacerlo antes convertiría un fallo de campo en un diagnóstico a cuatro
bandas. APK instalado el 17-08 14:18 (`prodDebug`, sha `404e9447…`) — la validación es lo siguiente.

## Registro

- 2026-08-17 — abierto a raíz de la pregunta del usuario. Regla escrita en `CLAUDE.md`.
- 2026-08-17 — ejecutado ya, y **ambos repliegues están en master**:
  - `EvaluateMeasuredDepartureUseCase` **borrado** (+ `departureProven`, su parámetro y su seed);
    sus 2 aserciones sin equivalente portadas a `EvaluateShortHopDriveProofUseCaseTest` para no
    perder cobertura. En master `6ae35526`.
  - `EvaluateHumanPoweredRideUseCase` **replegado** a `domain/detection/HumanPoweredRide.kt` como
    función pura (patrón `SentryWakeCooldown`); test movido a `HumanPoweredRideTest`. `usecase/
    detection/` vuelve a 11: el ticket de la bici acaba con **cero** casos de uso nuevos.
    1199 tests verdes. En master `0e37d538`.
