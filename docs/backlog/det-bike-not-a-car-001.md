# DET-BIKE-NOT-A-CAR-001 · una bici supera todos los umbrales de "coche", y nadie la contradice

**Estado:** ✅ **DONE — master `0e37d538`** (ff-only 17-08 tras el stack 1→2→3, sin pushear; rama + worktree borrados) · ⏳ pendiente validar en campo

## Problema

Field 16-08, Samsung SM-A536B (cuenta cardomfer, uid `sUGo7EYl16XDtosI8Ei7LFeAo2E2`), sesión
`1786878499475`. **Un paseo en bici a los Toruños se registró como viaje en coche y movió el pin.**

```
11:08:19Z  ARM:GEOFENCE_EXIT (geof=0575e3e8 d=352m acc=12m
                              exitLoc=36.5790416,-6.21865 dep=verified_speed)
…          58,9 min · vmax 38 km/h · drive 58/904 fix · steps 224
12:07:11Z  SESSION_ENDED  outcome=confirmed_unattended_zone_unpinned_anchor
```

Pin resultante `6b142014-606b-4aa4-8208-eb0b76d66fe3` — `detectionPath = unattended_zone_unpinned_anchor`,
`armEvidence = verified_late`, fiabilidad 0.5, **`isActive = true`**, en 36.5382,-6.2266 ("Unnamed
Road", zona Toruños / Río San Pedro), a ~4,8 km del coche real. El pin anterior del coche
(`0575e3e8`, Calle Toledo 5) quedó cerrado. El Mercedes nunca se movió.

### Por qué pasa todos los filtros

| Filtro | Umbral | Bici |
|---|---|---|
| `minimumDepartureSpeedKmh` (¿salida verificada?) | 10 km/h | 38 km/h ✅ pasa |
| `minGpsAccuracyForDriving` | ≤ 50 m | 12 m ✅ pasa |
| `maxPedestrianSpeedMps` (`isBeyondPedestrianReach`) | 2,5 m/s = 9 km/h | ✅ pasa holgado |
| `minimumTripSpeedMps` (`sessionSawDriving`) | 5 m/s = 18 km/h | ✅ pasa |
| `humanPowered` (`EvaluateParkingDecisionUseCase:200`) | perfil `BIKE`/`SCOOTER` | ❌ **el perfil es `CAR`** |

El único guard antibici que existe mira el **perfil del vehículo registrado**, y el vehículo de
Carlos es un `Mercedes Clase A`, `vehicleType = CAR` (verificado en Firestore). Nunca se activa. La
persona monta en bici; el perfil sigue diciendo coche.

Sin BT emparejado (`bluetoothDeviceId = null`), el Coordinator no tiene **ninguna** señal que
distinga bici de coche: cinemáticamente son el mismo objeto.

### La señal que sí existe y no estamos escuchando

`ActivityRecognitionLabels.kt` ya sabe nombrar `DetectedActivity.ON_BICYCLE`, pero
`ActivityRecognitionManagerImpl.registerTransitions()` **sólo registra transiciones de `IN_VEHICLE`**
(líneas 108-145). Android tiene un clasificador de bicicleta razonablemente bueno y nunca le hemos
preguntado.

## Doctrina violada

- **Fallo asimétrico: mejor falso negativo que falso positivo.** Se plantó una plaza fantasma a
  4,8 km del coche, con la plaza real dada por libre. Es el peor resultado posible del sistema.
- **El evento NOMINA, sólo el movimiento MEDIDO confirma** se cumplió formalmente… y aun así falló:
  el movimiento estaba medido, sólo que no era el del coche. Falta el eje "¿de QUÉ vehículo es este
  movimiento?", que hoy sólo cubre el carril BT (ligado a la MAC).

## Señales / datos disponibles

- **AR `ON_BICYCLE` ENTER/EXIT** — no registrada hoy. Va al carril de EVIDENCIA
  (`getBroadcast`, `ActivityTransitionReceiver`), que no enciende FGS: registrarla no cuesta batería
  visible ni notificación en cada paseo.
- `event.elapsedRealTimeNanos` — el receiver ya convierte a epoch real, así que la evidencia llega
  con su instante verdadero pese a los ~2 min de latencia de AR.
- `DepartureEventBus` — ya existe el patrón de "sellar el bus con la hora verdadera".

## Diseño

**Invariante:** *"me he movido" no implica "mi coche se ha movido". Cuando la única evidencia de
vehículo es cinemática y AR dice que el movimiento fue a pedales, la sesión no puede confirmar ni
guardar en silencio: pregunta.*

Tres piezas, ninguna nueva en el carril BT (los carriles no se mezclan):

1. **Registrar `ON_BICYCLE` ENTER + EXIT** en la MISMA `ActivityTransitionRequest` del carril de
   evidencia (`vehicleTransitionsPendingIntent`). NO se toca el carril de decisión
   (`getForegroundService`): una bici jamás debe armar nada, sólo desmentir.
2. **Latch de sesión** `humanPoweredRideAt: Long?` en `ParkingDetectionState`, sellado por
   `CoordinatorParkingDetector.onHumanPoweredRide(trueEpochMs)` desde el receiver. Se **anula** si
   llega un `IN_VEHICLE` ENTER posterior (bici hasta la estación y luego coche = viaje de coche).
3. **Un evaluador puro** `EvaluateHumanPoweredRideUseCase` (commonMain) que responde
   "¿este movimiento es de tracción humana?" a partir de (perfil del vehículo, latch AR, timestamp
   del último `IN_VEHICLE` ENTER, ventana de frescura). Alimenta el flag `humanPowered` que
   `EvaluateParkingDecisionUseCase` **ya** consume (línea 200/213) → `ParkingDecision.Prompt`.

### El punto donde mordió: la salida desatendida

El FP del campo NO pasó por `EvaluateParkingDecisionUseCase` sino por la cadena de timeout
desatendido (`unattended_zone_unpinned_anchor`). Extender sólo `humanPowered` cerraría la puerta
equivocada. La veto debe cubrir **las dos** salidas:

- auto-confirm (`evaluateCandidatePhase`) → `Prompt`, ya cubierto por el flag.
- timeout desatendido (`saveUnattendedZone` + el save al ancla pinchada) → **nudge, nunca guardar**.

Es exactamente el corolario DET-DRIVE-PROOF-001 → DET-DEPART-PROOF-001: cerrar sólo la vía donde
mordió no basta.

## Criterio de éxito

- Test del evaluador puro: perfil `CAR` + `ON_BICYCLE` ENTER dentro de la sesión sin `IN_VEHICLE`
  ENTER posterior → tracción humana; con `IN_VEHICLE` ENTER posterior → NO.
- Test de regresión con la forma del campo (arm por GEOFENCE_EXIT `verified_speed`, 59 min a 38 km/h,
  ancla no pinchada, timeout) → **ROJO sin el fix** (hoy guarda zona), verde con él (nudge, cero pin).
- Verde sin tocar: un viaje de coche normal con AR mudo o con `IN_VEHICLE` ENTER sigue confirmando.
- Campo: Carlos repite el paseo a los Toruños y el coche sigue pinchado en Calle Toledo.

## Consumidores auditados

*(a completar durante la implementación — plantilla)*

| Sitio | Clasificación |
|---|---|
| `EvaluateParkingDecisionUseCase:200` `humanPowered` (perfil) | **cerrado** — pasa a "perfil O medido" |
| `CoordinatorParkingDetector` cadena desatendida (~1180-1350) | **cerrado** — veto antes de cualquier save |
| `ActivityRecognitionManagerImpl:108-145` registro | **cerrado** — se amplía el carril de evidencia |
| `ActivityTransitionReceiver:49` `if (activityType != IN_VEHICLE) return@forEach` | **cerrado** |
| `VerifyDepartureEvidenceUseCase` (`verified_speed` a 10 km/h) | **exento con razón, follow-up abierto** → [DET-BIKE-DEPARTURE-RELEASE-001](det-bike-departure-release-001.md). El veto de AR llega con ~2 min de latencia y el verdicto de salida es inmediato, así que el latch de bici todavía no existe cuando se decide. Requiere un downgrade post-arm (simétrico de `notifyDepartureConfirmed`), que es otro sistema |
| Carril Bluetooth | **exento** — determinista por MAC, una bici no tiene la MAC del coche |

## Notas de campo relacionadas (mismo móvil, NO son este bug)

Sesión `1786873042480` (09:37–10:46Z, **80 km/h, 44/602 fixes de conducción**) murió como
`aborted_unattended_no_drive`: la firma exacta de DET-UNVERIFIED-ARM-DRIVE-PROOF-001, cerrado en
master el 16-08 (`e9186a52`). Se desconoce qué build lleva el Samsung de Carlos. Verificar antes de
tratarlo como bug vivo.

## Registro

- 2026-08-17 — abierto tras el diagnóstico del field 16-08. Worktree + rama creados.
- 2026-08-17 — **implementado, sin commitear.**
  - `ActivityRecognitionManagerImpl`: `ON_BICYCLE` ENTER+EXIT en el carril de EVIDENCIA
    (`getBroadcast`). El carril de decisión (`getForegroundService`) NO se toca: una bici jamás arma.
  - `ActivityTransitionReceiver`: `ON_BICYCLE` ENTER → `coordinator.onHumanPoweredRide(trueTime)`;
    sin bus, sin acelerador de la red de seguridad. `IN_VEHICLE` ENTER también sella
    `coordinator.onVehicleRide(trueTime)` para poder superseder.
  - `domain/detection/HumanPoweredRide.kt` — función pura de nivel superior, **no** un caso de uso:
    es un PREDICADO (sin vocabulario de diagnóstico propio) compartido por dos veredictos, así que va
    con el patrón `SentryWakeCooldown` / `SentryLifecycleDecision` [DET-VERDICT-NOT-PREDICATE-001].
    Nació como `EvaluateHumanPoweredRideUseCase` y se replegó el mismo día: `usecase/detection/`
    vuelve a 11 ficheros, o sea este ticket añade **cero** casos de uso. Test en `HumanPoweredRideTest`.
    + `humanPoweredRideMemoryMs` (3 h) + require.
  - `ParkingDecisionInput.humanPoweredRide` ensancha el `humanPowered` existente de "perfil" a
    "perfil O medido" → los caminos de auto-confirm degradan a prompt.
  - **Veto también en el timeout desatendido** — que es por donde entró el FP real: ni pin ni zona,
    `aborted_unattended_human_powered` + nudge.
  - **1199 tests verdes** (1190 en master + 9 nuevos). prod y mock compilan.
  - Regresión **verificada ROJA sin el fix** (`should_not_save_anything_when_the_ride_was_human_
    powered`).
  - `docs/detection/PARKING-DETECTION.md` actualizado (Sección 2).
  - Sin strings nuevos: el `source` del nudge es provenance, no copy (no hay `when` que lo traduzca).
- 2026-08-17 — **rebasado sobre `bugfix/DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001-in-session-ground`**
  (que a su vez está sobre master `8237c5c4`), o sea un stack lineal 1→2→3 para poder llevar los tres
  en un APK. 4 conflictos:
  - `CoordinatorParkingDetector.kt` — la cadena desatendida ya era el veredicto puro. Ganó master, y
    **el veto se trasladó a la primera rama de `EvaluateUnattendedParkingSaveUseCase`**
    (`UnattendedSaveInput.humanPoweredRide` + `UnattendedSaveReason.HUMAN_POWERED`), que conserva
    literalmente las 3 cadenas de traza (`unattended_human_powered` /
    `aborted_unattended_human_powered` / `UNATTENDED_HUMAN_POWERED_NUDGE`).
  - `ParkingDetectionConfig.kt` y `CoordinatorParkingDetectorTest.kt` — aditivos, se conservan ambos
    lados. ⚠️ En los dos, "quedarse con ambos" rompió la sintaxis porque la llave/cabecera de cierre
    caía FUERA del conflicto: un `require` sin cerrar y dos `@Test` entrelazados. El test se
    reconstruyó de forma determinista desde las dos fuentes intactas (`git show` de la base y del tag
    `prerebase/DET-BIKE-NOT-A-CAR-001`) en vez de desenredarlo a mano.
  - `docs/detection/PARKING-DETECTION.md` — log append-only: las 3 entradas conviven.
  - **1212 tests verdes**, prod + mock compilan. Backup en tag `prerebase/DET-BIKE-NOT-A-CAR-001`.
- ⏳ Pendiente: **confirmar qué build lleva el Samsung de Carlos** antes de tratar la sesión
  `1786873042480` (`aborted_unattended_no_drive` con 80 km/h y 44 fixes) como bug vivo — es la firma
  de `e9186a52`, ya cerrado en master.

- 2026-08-17 — **mergeado a master `0e37d538`** con `--ff-only`, cerrando el stack. 1212 tests verdes
  en master. Follow-up vivo sin código: [DET-BIKE-DEPARTURE-RELEASE-001](det-bike-departure-release-001.md).
