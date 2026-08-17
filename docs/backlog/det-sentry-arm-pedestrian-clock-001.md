# DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001 · el reloj peatonal sólo vale si el arm acota cuándo salió el coche

**Estado:** ✅ **DONE — master `6ae35526`** (ff-only 17-08, sin pushear; rama + worktree borrados) · ⏳ pendiente validar en campo

## Problema

Field 16-08 23:52 local, Oppo `fiypNbElGlfFexLMpU9sNaMjRMD3`, sesión `1786917152243`.
**Falso positivo: se plantó un pin en Paseo Marítimo, adonde habíamos llegado ANDANDO** desde el
aparcamiento anterior (Avenida de la Paz 49, pin `bb822c5c`, ~990 m).

```
21:52:32Z  SESSION_STARTED   ARM:SIGNIFICANT_MOTION (sentry-wake geof=bb822c5c) · evidence self_observed
21:52:37Z  FIX  speed 0.0   acc 100 m   36.57140,-6.22416      ← arranque en frío, ya a 990 m del pin
21:52:42Z  FIX  speed 0.0   acc 100 m   36.57140,-6.22416
21:52:47Z  FIX  speed 11.7  acc  11.5   36.57090,-6.22356      ← ESPEJISMO: el receptor converge
21:52:50Z  FIX  speed  0.6  acc  20.2   …                         y a partir de aquí, andando
…          220 STEP events a 0,7–0,9 m/s durante 3 minutos
21:55:37Z  DECISION CONFIRMED  pathLabel=steps+egress  confidence=0.9
21:55:39Z  SESSION_ENDED  outcome=confirmed_steps+egress
```

Cabecera de sesión: `drive 1/71fix · vmax 42 km/h`. Ese **único** fix de 11,7 m/s (= 42,11 km/h,
exactamente el `maxSpeedKmh` de la cabecera) es todo lo que la app llegó a medir como "conducción".

### Cadena exacta

1. `EvaluateMeasuredDepartureUseCase` acepta el fix de las 21:52:47:
   `isCredibleDrivingSpeed(42,1 km/h, acc 11,5 m)` ✓ (umbral 10 km/h, acc ≤ 50 m) y
   `isBeyondPedestrianReach(990 m, elapsed = 15 s, …)` ✓ — porque **el reloj arranca en el ARM**.
   → `departureProven = true`.
2. Los fixes 21:52:47 / :50 / 21:53:01 cumplen la geometría de
   `EvaluateShortHopDriveProofUseCase.qualifies` (≥ 400 m del pin, fuera de valla y envolventes,
   fuera del alcance peatonal *desde el arm*) → 3 consecutivos → `shortHopProven`.
3. `driveProven = true` → `maxSpeedMps = pendingMax = 11,7` → `maxSpeedKmh = 42,1`.
4. `sessionSawDriving = true` en `EvaluateParkingDecisionUseCase` → **el guard
   `self_observed ∈ weakLabels` de DET-UNVERIFIED-CONFIRM-001 deja de morder**.
5. 220 pasos + egress → `hasStepsProof` → `Confirmed("steps+egress", 0.9)`. Pin fantasma.

### La raíz

`ParkingDetectionConfig.isBeyondPedestrianReach(distanceMeters, elapsedMs, …)` responde
*"¿pudo esta persona alejarse tanto A PIE en este tiempo?"*. Es correcta **si y sólo si `elapsedMs`
empieza a contar cuando el coche estaba en el pin**. Eso lo garantiza un evento que acota el instante
de salida:

| Arm | ¿acota cuándo salió el coche? |
|---|---|
| `GEOFENCE_EXIT` | ✅ el teléfono cruzó el radio en ese instante |
| `AR_VEHICLE_ENTER` | ✅ el embarque acaba de ocurrir |
| `BT` disconnect | ✅ |
| `verified_late` (worker) | ✅ verdicto sobre una salida concreta |
| **`SIGNIFICANT_MOTION` (sentry-wake) / `self_observed`** | ❌ **no acota nada** |

La sentinela dispara por movimiento del sensor, que puede llegar minutos u horas después de que el
usuario se marchara andando — o sin que el coche se haya movido jamás. El reloj arranca a cero cuando
el desplazamiento **ya lleva 20 minutos acumulándose a pie**. Resultado: un peatón a 1 km "demuestra"
un viaje en 15 segundos.

Esto es exactamente el FP del 13-08 (Calle Góndola, salir andando) entrando por una puerta nueva:
DET-UNVERIFIED-CONFIRM-001 cerró la vía `!sessionSawDriving`, y DET-UNVERIFIED-ARM-DRIVE-PROOF-001
(16-08, `e9186a52`) abrió sin querer la vía que pone `sessionSawDriving = true`. Ambos móviles del
field llevaban ese build (instalado 16-08 18:42), así que esto es la validación en campo de aquel fix.

## Doctrina violada

**El evento NOMINA, sólo el movimiento MEDIDO confirma.** Aquí no se midió movimiento: durante toda
la sesión el desplazamiento propio fue de ~30 m a 0,9 m/s. Lo aceptado como prueba fue *distancia
acumulada antes de que la sesión existiera* más un espejismo Doppler del primer fix creíble tras un
arranque en frío.

## Señales / datos disponibles

- `currentArmEvidence` / `ArmEvidence.isVerifiedDeparture` — ya distingue los arms que acotan de los
  que no. No hace falta señal nueva, hace falta **leerla en el sitio correcto**.
- `state.sessionOrigin` — primer fix de la sesión, ya persistido (línea 790).
- `state.recentFixes` — anillo acotado de fixes recientes con timestamp y accuracy.

## Diseño

**Invariante:** *la distancia al pin dice DÓNDE ha acabado el coche, nunca que esta sesión lo haya
visto llegar. La racha de fixes que sostiene la prueba por desplazamiento tiene que MEDIR conducción,
fix a fix.*

`EvaluateShortHopDriveProofUseCase.qualifies` aplica `config.isCredibleDrivingSpeed` — el mismo
predicado canónico del verificador pre-arm — antes de cualquier geometría. Un peatón no sostiene
≥ `minimumDepartureSpeedKmh` durante `shortHopProofFixes` fixes creíbles consecutivos; un salto real
no produce otra cosa.

### Por qué NO se reescribe el reloj peatonal

Era el diseño de partida (origen = primer fix creíble de la sesión cuando el arm no acota) y **es
incorrecto**: el fixture de la regresión de DET-UNVERIFIED-ARM-DRIVE-PROOF-001 (`driveLateArmedHop`,
Redmi 15-08) cubre sólo **~39 m dentro de la sesión** — la sentinela armó con el viaje casi terminado
y el stream sólo vio la cola. Con un suelo de 400 m ese arreglo habría convertido el FN de ayer en un
FN otra vez.

Lo que separa de verdad los dos casos es la velocidad sostenida:

| | Redmi 15-08 (debe probar) | Oppo 16-08 (no debe) |
|---|---|---|
| Fixes de la racha | 7,1 · 8,2 · 8,4 m/s | 11,7 m/s y luego 0,6 · 0,2 · 0,1 |
| Racha creíble ≥ 3 | ✅ | ❌ (máx. 1) |
| Desplazamiento en sesión | ~39 m | ~30 m |
| Distancia al pin | ~1,1 km | ~990 m |

Ni la distancia al pin ni el desplazamiento propio distinguen nada. La velocidad sí.

El sesgo del reloj (`elapsedSinceArmMs` desde el arm en vez de desde la salida real) **sigue
existiendo** y queda documentado como límite conocido: con la exigencia de velocidad, un paseo ya no
alcanza la prueba, así que dejó de ser explotable por esta vía. No se toca porque tocarlo cuesta una
regresión real y no compra nada hoy.

### Barrido: la misma fuga una rama más abajo

Con todos los caminos de confirmación rechazando correctamente, el timeout desatendido **seguía**
comprando una zona aproximada: el limbo "señal vehicular" de DET-NODRIVE-ZONE-001 aceptaba el PICO
crudo de velocidad — una sola muestra, que es exactamente lo que produce el espejismo. Ahora exige
`rawDriveSignalMinFixes` (2) fixes creíbles de conducción, vía el contador de sesión
`credibleDrivingFixes`. El AR vehicle-exit sigue bastando por sí solo: es evidencia externa, no una
muestra. Cerrar sólo la puerta donde mordió habría movido el FP de un pin exacto a un círculo de 60 m.

## Criterio de éxito

- Test de regresión con la forma del campo (arm `self_observed`, primer fix ya a 990 m del pin, un
  único fix de 11,7 m/s, resto banda peatonal) → **ROJO sin el fix**: hoy confirma `steps+egress`;
  con el fix `driveProven` queda `false`, `sessionSawDriving` queda `false`, `self_observed` vuelve a
  ser weak label y la sesión **pregunta** en vez de pinchar.
- Verdes sin tocar: regresión de DET-UNVERIFIED-ARM-DRIVE-PROOF-001, DET-SHORT-HOP-PROOF-001, el
  anti-espejismo de 2026-07-27 y el anti-resurrección a velocidad peatonal.
- Campo: salir andando del coche y volver a 1 km ya no planta pin.

## Consumidores auditados

`grep -rn "isBeyondPedestrianReach" composeApp/src --include=*.kt`

| Sitio | Clasificación |
|---|---|
| `EvaluateShortHopDriveProofUseCase:qualifies` | **cerrado** — la racha exige velocidad creíble |
| `EvaluateMeasuredDepartureUseCase` | **cubierto por convergencia** — ya exige `isCredibleDrivingSpeed`, y su único consumidor es la prueba de arriba, ahora endurecida |
| `DetectParkingDepartureUseCase:187` | **exento** — lado departure; la ventana la fija el evento de salida que se está juzgando, no un arm |
| `VerifyDepartureEvidenceUseCase:84` | **exento** — verificador PRE-arm: su reloj es el del EXIT, que acota por construcción |
| `EvaluateSafetyNetCheckUseCase:215,283` | **exento** — razona sobre `nearAgeMs` (edad del último fix cercano al pin), cota propia e independiente del arm |
| `CoordinatorParkingDetector` limbo del pico crudo (`vehicularSignal`) | **cerrado** — exige `rawDriveSignalMinFixes` |

`grep -rn "elapsedSinceArmMs"` → sólo los 3 call-sites del coordinator y los dos use cases. Sin más
consumidores.

## Registro

- 2026-08-17 — abierto tras el diagnóstico del field 16-08. Worktree + rama creados.
- 2026-08-17 — ⚠️ **el diseño de arriba (mover el origen del reloj al primer fix de la sesión) se
  descartó: habría roto la regresión de DET-UNVERIFIED-ARM-DRIVE-PROOF-001.** El fixture
  `driveLateArmedHop` (Redmi 15-08) cubre sólo **~39 m dentro de la sesión** — armó con el viaje casi
  terminado y el stream sólo vio la cola. Anclar al primer fix propio exige 400 m y lo habría matado.
  El discriminador real entre los dos casos no es la distancia sino la **velocidad sostenida**:
  Redmi = 7,1 / 8,2 / 8,4 m/s en tres fixes consecutivos; Oppo = un pico de 11,7 m/s y el resto a
  0,1–0,9 m/s.
- 2026-08-17 — **implementado, sin commitear.**
  - `EvaluateShortHopDriveProofUseCase.qualifies` exige `isCredibleDrivingSpeed` por fix: la racha
    que sostiene la prueba tiene que MEDIR conducción, no sólo estar lejos del pin.
  - Barrido: la misma fuga aparecía una rama más abajo — el limbo del pico crudo de
    DET-NODRIVE-ZONE-001 compraba una zona aproximada con ese único fix. Nuevo contador de sesión
    `credibleDrivingFixes` + `rawDriveSignalMinFixes = 2`; el AR vehicle-exit sigue bastando solo.
  - **1193 tests verdes** (1190 en master + 3 nuevos). `compileMockDebugKotlinAndroid` OK.
  - Regresiones **verificadas ROJAS sin el fix** (`should_not_pin_when_a_sentry_wake_arms_after_the_
    user_already_walked_away` y `should not prove the drive from a run of fixes that merely SIT far
    from the pin`); las de DET-SHORT-HOP-PROOF-001 y DET-UNVERIFIED-ARM-DRIVE-PROOF-001 siguen verdes
    sin tocarlas.
  - `docs/detection/PARKING-DETECTION.md` actualizado (Sección 2).
  - El reloj peatonal **no** se reescribe: con la exigencia de velocidad, un paseo ya no alcanza la
    prueba. Queda documentado como límite conocido, no como deuda silenciosa.
- 2026-08-17 — **[DET-VERDICT-NOT-PREDICATE-001] fusión**: al exigir velocidad creíble en
  `qualifies`, `EvaluateMeasuredDepartureUseCase` quedó **subconjunto estricto** de él con los mismos
  parámetros, y como `departureProven` se calcula sobre el mismo fix justo antes, su gate ya no podía
  decidir nada. Borrados: el caso de uso (79 líneas), su test, el campo de estado `departureProven`,
  el parámetro del constructor, el parámetro de `invoke` y la línea de `notifyDepartureConfirmed()`.
  Las 2 aserciones del test borrado sin equivalente (dentro de la valla · elapsed negativo) se
  **portaron** a `EvaluateShortHopDriveProofUseCaseTest`: fusionar no puede perder cobertura.
  Coordinator **−18 líneas netas**. **1186 tests verdes.**
- 2026-08-17 — **rebasado sobre master `8237c5c4`** (commit `06908858`). 2 conflictos, ambos
  esperados y anotados de antemano:
  - `CoordinatorParkingDetector.kt` — master ya traía la cadena desatendida extraída a
    `EvaluateUnattendedParkingSaveUseCase`. Resuelto a favor de master, y el **endurecimiento del
    limbo del pico crudo se trasladó al evaluador puro**: `UnattendedSaveInput.credibleDrivingFixes`
    + el `rawDriveSignalMinFixes` dentro de la rama NO_DRIVE. El borrado de
    `EvaluateMeasuredDepartureUseCase` se mantuvo.
  - `docs/detection/PARKING-DETECTION.md` — log append-only: se conservan **las dos** entradas.
  - Post-rebase rompía la compilación de tests (`No value passed for parameter
    'credibleDrivingFixes'` en el builder que ticket 1 dejó en master) — arreglado, y añadidos 2 tests
    que cubren el limbo en su nueva casa (pico único → Ask · AR vehicle-exit → zona).
  - **1203 tests verdes**, prod + mock compilan. Backup en tag `prerebase/DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001`.

- 2026-08-17 — **mergeado a master `6ae35526`** con `--ff-only` (el árbol principal seguía con trabajo
  del user staged). 1212 tests verdes en master con los tres tickets dentro.
