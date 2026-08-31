# DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001 · A confirmed departure leaves a follower, not a deaf detector

**Estado:** ✅ Done (01-09-2026) — mergeado a master por squash; el hash vive en `MEMORY.md`.

## Problema

Field 2026-08-31, Oppo (`90lnZzs5…`), medido en su `parkdiag.log` (no inferido):

```
21:22:38  ⑊ honest close: aborted_false_enter stayed silent (frozen_counter)   ← la sesión viva MUERE
21:22:44  Depart attempt=2 speed=13.29km/h acc=2.314m → Confirmed              ← salida REAL (camino de
21:22:44  ClearActiveParkingSessionWorker SUCCESS session=d194668c                tierra, despacio)
21:28:53  ⊘ AR ENTER not armable (NoSession, lag=206ms)                        ← el viaje real, vetado
21:22:45→23:00  39 × ⊘ SENTRY_WAKE — 1 parked session(s), none of the active car; standing down
```

El worker de salida **midió** el coche alejándose (13,3 km/h, acc 2,3 m — ground truth del user:
salida real lenta por camino de tierra) y borró la única sesión aparcada del vehículo activo. Desde
ese instante el detector se quedó **sin nada de lo que rearmar**: sin parking activo no hay valla,
sin valla el sentry se planta (`standing down`, 39 veces, el móvil VIVO toda la noche), y el único
carril restante —el AR ENTER— muere en `ArEnterDecision.NoSession`. **Dos aparcamientos perdidos**
(≈21:40 y ≈22:40, Góndola).

**El contraejemplo que prueba el diseño correcto es el Redmi, en el MISMO viaje**: mismo defecto,
salvado por 100 s de orden. Su `SENTRY_WAKE` armó un coordinator a las 21:23:56, su worker de salida
confirmó a las 21:24:33 (26,3 km/h), y cuando el clear borró la sesión aparcada **ya había una
sesión viva siguiendo el viaje** → llegada detectada, pin `2f4197dc` a las 21:40, `steps+egress`.
Nada garantiza ese orden: es una carrera entre el backoff del worker (~15/30/60 s) y el próximo
significant-motion.

## Doctrina violada

- *Todo trigger dispara SIEMPRE.* Una salida confirmada es el trigger más fuerte que existe de
  "hay un viaje en curso" — y hoy su procesamiento puede TERMINAR la escucha en vez de continuarla.
- *El evento nomina, solo el movimiento medido confirma.* Aquí el movimiento **ya está medido**
  (fix fresco a velocidad de conducción con precisión creíble, independiente del eco del exit —
  `DepartureDecision.Confirmed`); lo que falta no es evidencia, es que alguien la siga.
- El propio seam existe a medias: `notifyDepartureConfirmed()` upgradea una sesión viva si la hay
  ([DET-G-05]). Si no la hay, la notificación cae al vacío y la conducción medida se descarta —
  la mitad silenciosa del mismo contrato.

## Señales / datos disponibles

- `RunDepartureCheckUseCase` (commonMain) tiene, en el momento exacto del `Confirmed`: el fix
  fresco (velocidad + precisión), el pin que se va a cerrar (ancla real del viaje), el geofenceId
  y `proof` (`Witnessed` = medido / `Deduced` = inferido).
- `DetectionRuntimeState.isRunning` dice si ya hay sesión viva (el caso Redmi → no hacer nada).
- El arm mid-trip ya existe como forma: `ArEnterDecision.ArmMidTrip`, y el arranque programático
  del servicio también (`CoordinatorDetectionService.startBoardedAwayArm`, del re-look de
  `DET-A-DECLINED-ARM-IS-NOT-SILENCE-001`).
- Trazas de campo completas en scratchpad (`oppo_parkdiag.log`, `redmi_parkdiag.log`) para el
  replay: stream íntegro de fixes del 21:22 del Oppo.

## Diseño — el SISTEMA, no el parche

**Invariante:** *procesar una salida confirmada por medición nunca deja al detector sin sesión:
si no hay detección viva en ese instante, la salida arma una que siga el viaje.*

Vive en **UN sitio**: el camino del `Confirmed` medido de `RunDepartureCheckUseCase` (el único
actor que a la vez mide la conducción y ordena el clear — arreglar la carrera donde se produce).
La decisión "¿hace falta follower?" es pura (commonMain, testeable); el side-effect de arrancar el
servicio es del caller androidMain, como en el re-look.

- El arm hereda la evidencia que YA existe: conducción medida + ancla = el pin recién cerrado
  (mismo espíritu que `ArmEvidence.InheritedDrive` / `ArmMidTrip`). No es un arm "de confianza":
  la medición ya ocurrió.
- Con `DetectionRuntimeState.isRunning == true` → no-op (el Redmi de hoy; y
  [DET-ARRIVAL-DOUBLE-PIN-001] ya establece ese skip como patrón).
- **Solo la salida MEDIDA arma.** El gate NO es `proof == Witnessed` a secas:
  `handleWatchdogDeparture` también pasa `Witnessed` (palabra del user) pero **sin hora** — el
  viaje puede llevar horas terminado; armar ahí sería seguir un viaje que no existe. El gate es
  "este mismo intento sampleó un fix fresco a velocidad de conducción creíble".
- El fallo de arranque del FGS en background (Android 12+/OEM) se trata como en el re-look:
  best-effort, log, el safety net sigue de backstop. No se pierde nada que hoy no se pierda ya.

**Alternativa evaluada y descartada** (la dirección inicial del ticket): que `NoSession` con un
parking del vehículo activo cerrado hace <N min compre el re-look de `748648fc`. Descartada como
vía principal porque (a) depende de que llegue un AR ENTER que puede no llegar; (b)
`DeclinedBoardingRelookWorker` y `shouldArmAfterDeclinedBoarding` asumen sesión ACTIVA (fence del
pin vivo) y habría que retorcerlos para leer un pin cerrado; (c) introduce una ventana N arbitraria
donde el diseño elegido no necesita ninguna — arma en el instante del Confirmed, con el fix de ese
instante. Se reconsiderará solo si el arm del follower resulta inviable.

## Implementación (01-09)

- **`RunDepartureCheckUseCase`** (commonMain): el camino `Confirmed` medido captura el fix que
  confirmó (`witnessedSpeedKmh`/`witnessedAccuracyM`) y, si `proof == Witnessed` y
  `DetectionRuntimeState.isRunning == false`, devuelve
  `DepartureCheckOutcome.Processed(followTrip = DepartureFollowHandoff(geofenceId, speedKmh, acc))`.
  `detectionRuntime` es parámetro SIN default (DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001).
  Preconfirmed y el fall-through de boarding devuelven `followTrip = null` (no midieron AHORA).
- **`DepartureDetectionWorker`**: traduce el handoff a
  `CoordinatorDetectionService.startDepartureFollowerArm(...)`, best-effort (patrón del re-look:
  denegación de FGS en background → log + safety net de backstop).
- **`CoordinatorDetectionService`**: `ACTION_FOLLOW_DEPARTURE` + `handleFollowDeparture` — skip si
  `detectionJob` activo (la carrera que salvó al Redmi, cerrada a propósito), relee el pin cerrado
  con `getSessionById` (la fila sobrevive al clear con `isActive=false`) para `TripContext`, y arma
  `startParkingDetection(GEOFENCE_EXIT, detail="departure-follower(…)", trip, DepartureFollowed)`.
- **`ArmEvidence.DepartureFollowed(speedKmh, accuracyM)`** → `ArmLabel.DEPARTURE_FOLLOWED`
  (`departure_followed`): `isVerifiedDeparture = true` (el mismo hecho que `verified_late`, entregado
  a un follower en vez de a una sesión viva), `confirmsSilentlyWithoutMeasuredDrive = false` (la
  barra de salida es de 10 km/h — una bici la pasa; sin conducción medida propia, el final PREGUNTA),
  `driveAuthorization = OnTrust`.

## Criterio de éxito

1. ✅ Tests unitarios del camino Confirmed (4 nuevos en `RunDepartureCheckUseCaseTest`): medido +
   nadie siguiendo → handoff con la medición; `isRunning=true` → null; `preconfirmed` → null;
   fall-through de boarding → null. El watchdog no pasa por este use case (exento por construcción).
2. ✅ **Falsaciones, vistas en rojo**: quitar el gate `isRunning` →
   `should_notHandOffFollower_when_aLiveSessionAlreadyFollowsTheTrip` FAILED; no entregar nunca el
   handoff → `should_handOffFollower_when_departureMeasured_and_nobodyFollowsTheTrip` FAILED.
3. ✅ Suite completa: **2074 tests, 0 fallos** (`--rerun-tasks`). `prod` + `mock` compilan.
4. ⏳ En campo: tras una salida lenta real, el ledger muestra `departure-follower(…)` y la llegada
   se juzga con detección viva; el AR ENTER posterior cae en `SUPPRESSED_REARM same_area` /
   supersede, nunca más `NoSession` en esta forma.

⚠️ Sin cubrir por la suite (dicho, no implícito): el salto de intent
(worker → `startForegroundService` → handler) y la relectura del pin cerrado son I/O androidMain sin
test, como el resto de carriles.

## Consumidores auditados

| Sitio | Papel | Estado |
|---|---|---|
| `RunDepartureCheckUseCase` camino `Confirmed` medido | el defecto — medía y borraba sin dejar follower | ✅ cerrado aquí (handoff + test + falsación) |
| `RunDepartureCheckUseCase` camino `preconfirmed`/boarding fall-through (`Deduced`) | el viaje puede haber acabado; no mide ahora | ✅ exento con test (`followTrip == null` en ambos) |
| `handleWatchdogDeparture` (`Witnessed` sin hora) | palabra del user, horas después | ✅ exento por construcción: no pasa por el use case |
| `handleGeofenceExit` → arm normal | ya arma su propia sesión en el exit | cubierto por convergencia (si esa sesión vive, `isRunning=true` → sin follower) |
| `DeclinedBoardingRelookWorker` / `shouldArmAfterDeclinedBoarding` | asumen sesión ACTIVA | sin cambios (la alternativa descartada los habría retorcido) |
| `nominatingSession` / `ArEnterDecision.NoSession` | con follower vivo este caso ya no se produce tras una salida medida | sin cambios; ⏳ verificar en campo |
| Consumidores de `DepartureCheckOutcome.Processed` | `object` → `data class` | ✅ barridos: worker + `RunDepartureCheckUseCaseTest` (únicos, por grep) |
| `when` exhaustivos sobre `ArmEvidence`/`ArmLabel` | caso nuevo | ✅ el compilador los forzó todos; sets cerrados de `ArmLabelTest` actualizados (verified sí, silent no) |
| Valla huérfana (`enter_d194668c` 12 min tras el clear) | defecto DISTINTO observado el 31-08 | fuera de alcance → follow-up propio |

## Datos de origen

- Memoria: `project_det_field_2026_08_31_oppo_goes_deaf.md`
- Logs: `oppo_parkdiag.log` / `redmi_parkdiag.log` (scratchpad de la sesión, 29-08→31-08 íntegros)
- Ground truth del user (31-08): la salida a 13,3 km/h era REAL (camino de tierra, despacio) — la
  liberación de la plaza fue correcta; lo roto es quedarse sordo después.
