# IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001 · wake-and-query: el corazón del tier asistido iOS

**Estado:** 🔵 En progreso · rama `feature/IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001-wake-and-query`
· worktree `../Paparcar-ios-f2` · base `feature/IOS-F0-001-fase0` (`27649206`) — todo iOS en el
PR #3. Plan: `docs/IOS-IMPLEMENTATION-PLAN.md` §4 y §8-F2 (puntos 10-12).

## Problema

F1 dejó el camino feliz: iOS arma, sigue el viaje y confirma. Pero el modelo iOS es
**wake-and-query** (§1 del plan) y esa mitad no existe:

- El EXIT de geocerca arma el seguimiento del siguiente park pero **no publica la plaza
  liberada** — el producto entero existe para eso.
- `stepsSinceSeal()` devuelve `null` → honest close y presupuesto de pasos MUDOS.
- Un wake tardío o una muerte mid-trip no reconstruyen nada: los side-records se escriben y nadie
  re-deriva el trayecto desde CMMotionActivity/CMPedometer.
- No hay mesh de safety-net: nada reconcilia sesiones cuando el OS no entregó un EXIT.

## Doctrina (sin excepciones nuevas)

- *Todo trigger dispara SIEMPRE, aunque llegue tarde, con verificación tardía* — el wake tardío es
  el caso NORMAL de iOS, no el excepcional; la reconstrucción alimenta a los MISMOS evaluadores.
- *El evento nomina, el movimiento medido confirma* — una reconstrucción pobre en datos empuja a
  Prompt/zona, jamás a pin silencioso (degradación diseñada, §4 del plan).
- *La decisión en commonMain* — el controller solo compone la query y ejecuta side-effects; los
  veredictos son los evaluadores existentes (`RunDepartureCheck`, `EvaluateSafetyNetCheck`,
  `DetectionTraceIngestion` → coordinator). **Cero evaluadores nuevos salvo que un veredicto
  inalcanzable lo exija** [DET-VERDICT-NOT-PREDICATE-001].

## Etapas (cada una plegable y verificable por CI por separado)

1. **Departure inline** (plan F2.11): el EXIT del bus, además de armar (F1), corre la escalera de
   retries del departure check EN LA VENTANA DEL WAKE (la sesión GPS del arm mantiene viva la
   app) → veredicto → `ProcessConfirmedDepartureUseCase` con su gate de publicación. Sin
   WorkManager: corrutina + delay, supervivencia a process death vía side-records (la
   reconstrucción de la etapa 3 re-deriva el departure perdido).
2. **`stepsSinceSeal` real** (CMPedometer date-range, contrato ya `suspend`, seal F0 ya persiste
   WHERE+WHEN) → se desmutean honest close y witness seal en el epílogo del controller (los ⏳
   declarados de F1).
3. **Reconstrucción wake-and-query** (plan F2.10, §4): en `start()`/wake — `scanStale` de pending
   arms + query CMMotionActivity `[armedAt…now]` + CMPedometer por sub-rangos → `TraceEvent`s →
   `DetectionTraceIngestion` → el coordinator decide con las reglas de siempre.
4. **Mesh de safety-net** (plan F2.12): `EvaluateSafetyNetCheckUseCase` corriendo en los wakes
   disponibles (app-start, ENTER cure, SLC/CLVisit, `BGAppRefreshTask` + sus identifiers en
   Info.plist).

## Diseño

**Del mapa del carril Android (05-09)**: el worker de departure es una capa de traducción PURA —
toda la decisión (escalera, gate de publicación `freedSpotIsStillThere`, doble-publicación
provisional, retract del pin refutado, handoff del follower) vive en
`RunDepartureCheckUseCase`/`ProcessConfirmedDepartureUseCase`, commonMain, ya bindeados. El port
de la etapa 1 es solo la escalera inline + 2 side-effects.

- **Etapa 1**: `launchDepartureLadder(fenceId, exitAtMs)` en el lane del EXIT — en PARALELO al
  arm (nunca dentro del intake: 105 s no pueden congelar el bucle; la sesión GPS del arm mantiene
  viva la app durante la ventana). Dedup por fence con REPLACE (la dedupe real cross-wake son los
  registros de adjudicación). Attempts en t=0/+15/+45/+105 s. Dos decisiones explícitas que
  Android deja implícitas: tope de `ProcessFailedRetry` (3, con 30 s — rendirse nunca pierde la
  sesión: la reconstrucción de la etapa 3 re-deriva un departure sin procesar) y `Dismissed` solo
  loguea (el fence-poison→cure es del mesh, etapa 4). `Processed(followTrip)` → arm
  `GEOFENCE_EXIT` + `ArmEvidence.DepartureFollowed` anclado al pin cerrado
  [DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001], con el guard de carrera del handler Android.
- **Etapa 2**: `IosDetectionStepAnchors.stepsSinceSeal` = query real
  `CMPedometer.queryPedometerDataFromDate` sobre `[sealedAtMs, now]` (contrato IOS-F0-05: seal
  persiste WHERE+WHEN; el delta se deriva al LEER — el OS cuenta con la app muerta y no existe
  contador que congelar). Epílogo del controller: `maybeRunHonestClose` espejo del service
  Android **sin el slot de witness** (iOS no tiene contador acumulativo que estampe testigos;
  el evaluador trata witness ausente como "sin refutación", nunca como prueba) → después el
  arrival stamp, mismo orden que Android.
- **Etapas 3-4**: pendientes (reconstrucción vía `DetectionTraceIngestion` — ya probada en
  `ActivityRecognitionQueryTest` — y mesh de safety-net). ⚠️ Diseño abierto anotado para la 3:
  el coordinator de producción es un single con reloj REAL; la reconstrucción exige reloj
  virtual (`ingestion.nowMs`) → hará falta una instancia con reloj inyectado (factory), no el
  singleton.

## Ejecutado (05-09) — etapas 1 y 2, pendiente de plegar

- `IosDetectionStepAnchors` reescrito con la query CMPedometer (suspend bridge, mute en
  error/permiso denegado/salto de reloj — nunca un veredicto falso).
- Controller: `launchDepartureLadder` + `runDepartureLadder` + handoff del follower +
  `maybeRunHonestClose` en el epílogo + 4 deps nuevas (module cableado).
- Verificado en Windows: suite JVM + verify mock + compiles — BUILD SUCCESSFUL (cambios
  iosMain: el compile real es del job `apple`).

## Consumidores auditados (etapas 1-2)

- `RunDepartureCheckUseCase`: worker Android + controller iOS → **convergencia** (la misma
  decisión, dos envoltorios). `freedSpotIsStillThere`/`ProcessConfirmedDeparture` viven dentro.
- `DetectionStepAnchors.stepsSinceSeal`: consumidor Android (service honest close) intacto; el
  consumidor iOS nuevo es el mismo espejo. El productor (`ConfirmParkingUseCase.seal`) no cambia.
- `ArmEvidence.DepartureFollowed`: camino EXISTENTE, ahora también emitido por iOS — provenance
  ya persistida (`persistLabel`), **cero valores nuevos de `detectionPath`** (los labels del
  honest close `ClosedApproximatePin/Zone` ya existen).
- Registros de adjudicación (`adjudication_`): NO tocados — son del safety net (etapa 4).
- Dev Catalog/strings: sin superficie nueva — exento.

## Criterio de éxito

- Suite JVM + simulador verdes en CI (nº reportado); `xcodebuild` verde.
- Tests nuevos para toda pieza de decisión nueva (si la hay) y para la composición pura de la
  reconstrucción.
- `PARKING-DETECTION.md` documenta el carril iOS F2 por etapa.
- Barrido de consumidores por etapa, clasificado abajo.

## Consumidores auditados

(por etapa, pendiente)
