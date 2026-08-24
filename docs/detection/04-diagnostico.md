# 04 — Auditoría de observabilidad del subsistema de detección

> Subagente D · refactor de solo-lectura · 2026-08-18. Fuentes: lectura completa de
> `domain/diagnostics/*`, `FirestoreDetectionEventLogger`, `FirestoreUiLocationLogger`,
> `DetectionEventDto`, `PaparcarLogger`, `FileAntilog`, y barrido de todos los call sites de
> `DetectionEventLogger` + tags `PARKDIAG`. Las líneas citadas son del working tree a fecha del
> informe. Lo no confirmado en código se marca **NO VERIFICADO**.

## 0. Arquitectura de la observabilidad (resumen)

Tres sinks independientes:

1. **Local, siempre (debug)** — `PaparcarLogger` (Napier) con 28 tags `PARKDIAG/*`;
   `FileAntilog` (`androidMain/logging/FileAntilog.kt`) persiste TODA línea cuyo tag empiece por
   `PARKDIAG` en `files/parkdiag.log` (5 MB + 1 rotación, solo builds debug). Es el sink forense
   principal en device.
2. **Remoto de sesión de detección** — `DetectionEventLogger` (port en
   `domain/diagnostics/DetectionEventLogger.kt`) → `FirestoreDetectionEventLogger`
   (`data/datasource/remote/FirestoreDetectionEventLogger.kt`). Esquema:
   `diagnostics/{uid}/sessions/{sessionId}` (header `DetectionSessionDto`) +
   subcolección `events/{autoId}` (un doc por `DetectionEventDto`). Gate por
   `diagnostics_config/{uid}.enabled` (leído 1 vez por proceso, cacheado solo si la lectura tuvo
   éxito; default `false`). Canal buffered (128) + consumidor único; drop silencioso al saturar.
   Retención: sweep de sesiones > 7 días una vez por proceso al resolver el gate enabled
   (`cleanupExpiredSessions`, líneas 115-141).
3. **Remoto de localización de UI** — `UiLocationLogger` → `FirestoreUiLocationLogger` →
   `diagnostics/{uid}/uiLocation/{autoId}` (`UiLocationSampleDto`). Local a logcat SIEMPRE
   (sin gate); remoto con el mismo gate + throttle de FIX a 1/10 s. Emisor único:
   `HomeViewModel.kt:775-795` (SUBSCRIBED / FIX / STOPPED del stream del mapa)
   [UI-LOC-FOREGROUND-001].

Ambos loggers reales están bindeados en prod (`di/DataModule.kt:49-66`); el no-opt-in paga solo
un `trySend` hasta que el gate resuelve `false` y luego cero (short-circuit en `log`, línea 78).
El header de sesión estampa identidad de device + estado de supervivencia
(`DeviceInfoProvider`: modelo, versiones, `batteryUnrestricted`, `requiresAutostart`,
`requiresOemBatteryFreeze`) [DIAG-READABLE-001][DET-SESSION-RELIABILITY-STAMP-001], y en
`SESSION_ENDED` se parchea un rollup (outcome, endedAt, maxSpeedKmh, drivingFixes/fixCount,
maxStepCount, finalLat/Lon, `summary` de una línea, espejado también a logcat).

## 1. Catálogo de eventos emitidos

20 subtipos declarados en `DetectionEvent.kt`; **18 se emiten en producción, 2 están muertos**
(`Geofence`, `Bluetooth` — mapeados en el DTO y usados solo en tests/fixtures; ningún call site
productivo los construye). Convención de `sessionId`: dentro de sesión coordinator = epoch-ms del
arm; fuera de sesión = `geofenceId`/`parkingId` de la entidad trazada; `"system"` cuando no hay
dueño; `"arm_<ts>"` para el trigger-log del servicio.

Rutas base: `Coord` = `commonMain/domain/coordinator/CoordinatorParkingDetector.kt` ·
`Svc` = `androidMain/detection/service/CoordinatorDetectionService.kt` ·
`SafetyNet` = `androidMain/detection/worker/ParkingSafetyNetWorker.kt` ·
`Backfill` = `androidMain/detection/worker/ParkingBackfillWorker.kt` ·
`BT` = `androidMain/bluetooth/BluetoothParkingDetector.kt`. Destino de todos: Firestore
(gated) + su línea PARKDIAG local paralela.

| Tipo (`type`) | Payload clave | Emisor (fichero:línea) | Rama de decisión |
|---|---|---|---|
| `SESSION_STARTED` | strategy, vehicleType?, evidence | `Coord:552` | Entrada de `invoke()` — abre la sesión con su `armEvidence`. |
| `SESSION_STARTED` (sintético `arm_<ts>`) | strategy=`"ARM:<trigger> (detail)"` | `Svc:1368` (`logArmTrigger`) | CADA arm (GEOFENCE_EXIT / AR_VEHICLE_ENTER / MANUAL / SIGNIFICANT_MOTION), antes de lanzar el coordinator. Crea un header de sesión que NUNCA se cierra. |
| `SESSION_ENDED` | outcome (`confirmed_*` / `aborted_*` / `confirm_failed_*` / `ended`) | `Coord:1325` | `finally` del dueño del estado; dispara el patch del rollup en el header. |
| `SESSION_ENDED` (outcome=`superseded`) | — | `Coord:1334` | `finally` de una sesión sustituida (gap del audit 2026-07-15: antes quedaba sin outcome). |
| `ACTIVITY_TRANSITION` | IN_VEHICLE×EXIT | `Coord:840` | Edge-detect del flip `vehicleExitConfirmed` en el stream de fixes (una vez por flanco). |
| `LOCATION_FIX` | lat/lon/acc/speed + stoppedDurationMs | `Coord:837` | **Cada fix GPS consumido** — el stream de replay [DET-LOG-04]. |
| `STEP` | stepCount, stopped | `Coord:655,658,661` | Cada evento del step detector (pre-drive / stopped / egress-walk). |
| `CANDIDATE` | action=OPENED/DISCARDED, phase | `Coord:2492,2509` (OPENED en HIGH), `Coord:1736` (DISCARDED sin egress) | Transiciones de `ConfirmationPhase`. Nota: `CONFIRMED` figura en el KDoc pero nunca se emite como `Candidate` (lo cubre `Decision CONFIRMED`). |
| `DECISION outcome=CONFIRMED` | pathLabel, confidence | `Coord:1612` | `runConfirm.onSuccess` — el veredicto terminal. |
| `DECISION CONFIRM_DEGRADED_PROMPT` | pathLabel | `Coord:1632` (ImplausibleRepark) · `Coord:1769` (`degradeToPrompt`, evidencia débil) | Auto-confirm rebajado a pregunta. |
| `DECISION CONFIRM_FAILED` | pathLabel | `Coord:1649` | `confirmParking` falló (no auth / error real). |
| `DECISION PROMPT_SHOWN` | pathLabel=`low_medium(...)`/`high_candidate`, confidence | `Coord:2454` y `Coord:2495` | Posteo del prompt — añadido porque el prompt del 2026-07-25 00:35 (Redmi) era invisible en forensics [DET-FROZEN-COUNTER-001]. |
| `DECISION HOLD_STALE_DISCARDED` | pathLabel del confirm retenido | `Coord:882` | Settle-revalidation del hold: la posición superó lo andable → errand/pick-up [DET-CONFIRM-FRESHNESS-001]. |
| `DECISION NO_MOVEMENT_JAM_FOLD` | recentCreep/rawMax en pathLabel | `Coord:989` | Presupuesto extendido de atasco agotado [DET-JAM-WINDOW-001]. |
| `DECISION UNATTENDED_ZONE_SAVED / _SAVE_FAILED` | distance, radius | `Coord:1509` | Timeout desatendido → zona aproximada en vez de perder el parking. |
| `DECISION <reason.decisionOutcome>` | pathLabel=`unattended_timeout`, distance | `Coord:1542` (`nudgeUnattended`) | Salida nudge-only del timeout desatendido (una por `UnattendedSaveReason`). |
| `DECISION BACKFILL_DEFERRED_TO_NUDGE` | sessionId=`system`, path=`safety_net_backfill` | `Backfill:92` | El backfill se abstiene porque el coordinator ya resolvió la llegada como nudge-only [DET-BACKFILL-TAINT-001]. |
| `HONEST_CLOSE` | verdict, reason + toda la aritmética (dist, walkDist, stepsDelta, requiredSteps, sessionStepEvents, maxSpeed, radius) | `Svc:866` | Escalera honest-close tras abort silencioso (`aborted_false_enter`/`aborted_no_movement`), logueado bajo el id de la sesión abortada [DET-FROZEN-COUNTER-001]. |
| `DEPARTURE_VERDICT` | verdict, source, attempt?, speedKmh?, enterAgeMs? | `Svc:620` (source=`pre-arm`) · `RunDepartureCheckUseCase.kt:88` (source=`worker`, cada intento) y `:120` (`Preconfirmed`) · `SafetyNet:594` vía `logVerdict` (:380 dispatch, :406 prompt; source=`safety-net:<src>`) · `BT:189` vía `logRemote` (source=`bt`: driving_abort, gps_timeout, timeout_save, walkaway_driving_abort, park_confirmed, *_refused) | El veredicto de evidencia de salida en cada lane. |
| `DEPARTURE_PROCESSED` | published, sessionCleared | `ProcessConfirmedDepartureUseCase.kt:84` | Salida confirmada procesada (publica + limpia sesión). |
| `REVERTED` | sessionAgeMs | `RevertParkingUseCase.kt:64` | FALSO POSITIVO etiquetado por el usuario — la señal más valiosa. |
| `RELEASED` | published, reason (`ParkingReleaseReason`) | `ReleaseActiveParkingSessionUseCase.kt:78` | Liberación de sesión: salida vs borrado de registro [PARK-DELETE-NO-DECLARE-001]. |
| `ORPHAN_CLEANED` | — | `Svc:469` | EXIT de una valla sin sesión → valla huérfana borrada. |
| `SESSION_SUPERSEDED` | distanceMeters, ageMs | `Svc:563` (lane GEOFENCE_EXIT) · `Svc:713` (lane AR ENTER) | Sesión zombi sustituida por un arm lejos de su ancla [DET-SUPERSEDE-001]. |
| `GEOFENCE_REGISTRATION` | success, radius | `ConfirmParkingUseCase.kt:320` (registro al confirmar) · `SafetyNet:297` (cure re-registro) | Invariante sesión⟺valla, con su resultado. |
| `BACKGROUND_KILL_SUSPECTED` | gapMs | `SafetyNet:543` | Gap de heartbeat > umbral con sesión activa, sin reboot [OEM-KILL-001]. |
| `FORCE_STOP_CONFIRMED` | — | `SafetyNet:582` | Android 16+ `wasForceStopped()` == true con sesión activa. |
| `SENTRY` | event (entered/woke/killed/wake_cooldown), signal, gapMs, residencyMs | `Svc:1030` (entered), `Svc:1098` (woke), `Svc:1117` (killed, lane live), `Svc:977` (wake_cooldown), `SafetyNet:515` (killed, lane periódica) | Ciclo de vida del residente SENTRY [DET-RESIDENT-FGS-001][DET-SENTRY-COOLDOWN-001]. |
| `UiLocationSample` (colección aparte) | kind, foreground, priority, acc, gap, speed, lat/lon | `HomeViewModel.kt:775-795` | Stream del mapa; FIX throttled a 1/10 s remoto. |

Sinks auxiliares (no `DetectionEvent`): Crashlytics custom keys `det_trigger`/`det_action`/
`det_job_active`/`det_has_movement`/`det_location_perm` (`Svc:1363,1391-1403`); notificaciones
debug `showDebug` (solo builds DEBUG); `PaparcarLogger.e` con throwable → `CrashReporter`
(non-fatal Crashlytics).

## 2. Huecos de observabilidad (ramas mudas remotamente)

Los dos casos históricos que el propio código reconoce como "esto no se veía en forensics" y que
ya fueron tapados: (a) el prompt invisible del 2026-07-25 (→ `PROMPT_SHOWN`, `Coord:2451-2460`) y
(b) las sesiones superseded sin outcome del audit 2026-07-15 (→ `Coord:1330-1334`). Otros dos
tapados: el prompt de evidencia débil que "nunca se mostró" el 2026-07-10 19:19
(→ `Coord:1763-1770`) y el BT sin telemetría remota pre-2026-07-07 (→ `BT:181-200`).

> ✅ **Actualizado 2026-08-24 — `DET-EVERY-TRIGGER-LEAVES-A-TRACE-001`** (primera entrega de la
> propuesta 3, adelantada). El **carril de triggers deja de ser mudo**: `DetectionEvent.Trigger` con
> `TriggerDisposition`, emitido por una sola puerta, cubre **2, 3, 4, 5 y 6** de la lista de abajo,
> la mitad de trigger de la 1, y añade el `ARMED` que antes solo se infería del `SessionStarted`.
> `type=TRIGGER` agrupado por `outcome` es ya el histograma completo de qué le pasa a cada trigger.
>
> ⚠️ **Y corrige un defecto que esta sección no había visto**: la retención encuentra sesiones por
> `startedAt` y **solo `SessionStarted` escribe el documento padre**, así que lo que se escribe bajo
> un id inexistente es inalcanzable y no se borra nunca. El `ARM_SUPPRESSED_USER_STOP` que ya existía
> fugaba un huérfano por supresión. El carril nuevo va a un **libro diario** (`triggers_<día>`) con
> cabecera real, recogible por la barrida que ya existe — y de paso tapa esa fuga.
>
> Siguen mudas, y son la continuación de la propuesta: **7-15**, la mitad BT de la 1, y las **ramas
> del hold del coordinator** (no listadas aquí porque esta sección inventarió el servicio y los
> workers, no el bucle — se descubrieron desde los tests en `DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001`).

Ramas que HOY siguen mudas en remoto (solo PARKDIAG local, que muere con el device):

1. **Los triggers crudos no existen como eventos**: `DetectionEvent.Geofence` y
   `DetectionEvent.Bluetooth` no se emiten en ningún sitio (grep exhaustivo; solo DTO
   `DetectionEventDto.kt:100-102` y tests). Una entrega de GEOFENCE_EXIT solo se infiere del
   `SessionStarted "ARM:GEOFENCE_EXIT"` + `DepartureVerdict pre-arm`; un EXIT que muere antes del
   arm (permisos, estrategia, lookup) no deja rastro remoto. El ACL de BT (disconnect/connect,
   el debounce de 30 s, la cancelación por reconexión en `BluetoothDetectionService`) es 100%
   local.
2. **Arm rechazado por el strategy gate** — `Svc:1165-1173` (`coordinatorMayArm` false): solo
   `PaparcarLogger.d`. El caso field 2026-08-01 (viajes del Kamiq pinneados al Focus) se
   diagnosticaría hoy sin traza remota del rechazo.
3. **Arm suprimido por re-arm guard** — `Svc:553-555` y `Svc:703-705` ([DET-AR-REARM-001], la
   variante NO-supersede): mudo remoto. El supersede sí traza; la supresión no.
4. **AR ENTER no armable** — `Svc:797-805` (`NoSession`/`StaleEnter`/`NoFix`/`TickOnly`): mudo.
   Un ENTER re-entregado que se descarta bien no deja constancia de que llegó.
5. **`guardPermissions` falla** — `Svc:533` y `Svc:685`: el trigger llegó y murió por permisos;
   mudo remoto (el caso "el usuario revocó background location" solo se ve en logcat).
6. **Lookup de sesión FALLIDO en el EXIT** — `Svc:446-449` (`LookupFailed`): mudo (el `OrphanCleaned`
   cubre solo el caso huérfano confirmado).
7. **Nudge del watchdog never-silent** — `SafetyNet:463-465` (`showMarkParkingNudge`
   source=`stale_pending_watchdog`): un pending huérfano de una muerte de proceso genera nudge
   sin evento remoto; y la variante "clearing silently" (`:467`) tampoco traza.
8. **PromptStillParked throttled** — `SafetyNet:391-408`: `logVerdict safety_net_prompt` solo se
   emite dentro del `if (!throttled)`; el tick que decide "pregunta reciente, no repito" es mudo.
9. **CureGeofence sin re-registro** — `SafetyNet:285-287` (throttle del cure): el resellado del
   step-anchor es mudo; solo el re-registro real emite `GEOFENCE_REGISTRATION`.
10. **`SafetyNetAction.None`** — `SafetyNet:411`: el "todo bien" del worker no deja heartbeat
    remoto por tick (solo el prefs local `KEY_LAST_ALIVE_AT`). Es deliberado (coste), pero implica
    que la línea de vida del safety net solo se observa por sus gaps.
11. **Resultado del backfill** — `Backfill:123-138`: el pin colocado (o el rechazo del guard) no
    emite `Decision`; la única provenance remota es el `detectionPath=safety_net_backfill` del
    `UserParking`. El caso deferral sí traza (`Backfill:92`).
12. **La estrategia BT no tiene sesión**: ni `SessionStarted` ni `SessionEnded`; solo
    `DepartureVerdict source=bt`. Además la clave de join es inconsistente: `saved.id` en éxitos
    (`BT:145,171`) y `vehicleId` en fallos (`BT:94,99,150,156,177`) — reconstruir un intento BT
    completo exige unir dos espacios de ids distintos, y los eventos con `sessionId=vehicleId`
    caen como headers-fantasma (sin doc padre `sessions/{id}`... en realidad crean solo la
    subcolección `events` bajo un doc inexistente — "missing parent", invisible a la query de
    retención, `FirestoreDetectionEventLogger:110-114`).
13. **Receivers y sensores**: `ActivityTransitionReceiver`, `GeofenceEnterReceiver`,
    `GeofenceExitWitnessReceiver`, `SignificantMotionMonitor`, `ExactHeartbeatReceiver/Scheduler`,
    `AndroidStepCounterSource`, `TripTrailImpl`, `DrivingRouteStoreImpl` — todos PARKDIAG local
    únicamente. En particular el AR ENTER crudo (lane de evidencia) y el sig-motion que despierta
    al SENTRY no existen como eventos remotos (el sentry `WOKE` sí lleva el trigger como signal).
14. **`maybeStampArrivalResolution`** — `Svc:896-899`: la resolución nudge-only se estampa a prefs
    sin evento; solo se ve remotamente si el backfill luego difiere (11).
15. **Aborts por respuesta del usuario / timeouts de prompt**: el outcome viaja en `SESSION_ENDED`
    (p. ej. `aborted_*`), pero no hay evento con la RESPUESTA del usuario al prompt (sí/ignorado)
    — el "ignorado" se infiere de `PROMPT_SHOWN` + outcome. **NO VERIFICADO** el label exacto de
    cada timeout (no leí las ~800 líneas intermedias del coordinator).

## 3. Coste

Todo lo remoto está detrás del opt-in (`diagnostics_config/{uid}.enabled`): producción no opted-in
paga ~0 (un `trySend` por evento hasta resolver el gate, luego return temprano). El coste es de los
devices de field-test:

- **Sesión típica arm→confirm→close (viaje de 20-30 min)**: dominada por `LOCATION_FIX` — un doc
  por fix consumido, con el stream high-accuracy a 2-5 s (`AndroidLocationDataSourceImpl:131-132`)
  ⇒ **~360-900 eventos LOCATION_FIX** + decenas de STEP + ~5-10 entre
  Candidate/Decision/ActivityTransition + 2 de sesión ⇒ **~400-950 writes Firestore por viaje**
  (más 1 `set` de header, 1 `update` de rollup, 1 doc `arm_*` + su evento, 1-3 DepartureVerdict,
  1 DepartureProcessed, 1-2 GeofenceRegistration). Un día de field-test con 4-6 viajes ≈ 2.5-6k
  writes por device — dentro de cuota de un proyecto Blaze pero **la partida de batería es real**:
  un write de red por fix mantiene la radio despierta durante toda la conducción, sumado al GPS.
- **Documentos**: `DetectionEventDto` plano, mayoría de campos null ⇒ ~0.2-0.5 KB/doc; header con
  rollup < 1 KB. Sin riesgo del límite de 1 MiB (una sesión = N docs pequeños, por diseño).
- **Riesgo principal de cuota/almacenamiento**: (a) **`uiLocation` no tiene retención** — el sweep
  de 7 días solo barre `sessions` (`cleanupExpiredSessions:122-125`); con el mapa abierto emite
  6 docs/min y crece sin límite hasta barrido manual. (b) Las trazas keyed por geofenceId/`arm_*`
  con "missing parent" o sin `startedAt` barrible quedan fuera de la query de retención (el propio
  KDoc lo admite, `FirestoreDetectionEventLogger:110-114`). (c) El sweep borra evento a evento sin
  batch (`:129-135`): una sesión de 900 eventos = 901 deletes secuenciales en el arranque del
  proceso opted-in.
- **Protecciones existentes**: canal 128 con drop silencioso (una ráfaga > capacidad pierde eventos
  sin aviso — sesgo silencioso del replay), consumidor único secuencial (autolimita el ritmo de
  writes), throttle 10 s de FIX en uiLocation, gate cacheado.

## 4. Esquema mínimo suficiente para reconstruir una sesión sin el device

Lo de hoy ya permite reconstruir el 90% de una sesión coordinator (SessionStarted+evidence →
fixes+steps → candidate/decisions → SessionEnded+rollup, y el pin con
`detectionPath`+`armEvidence`). Para llegar al 100% remoto:

**Falta (añadir):**
1. **Evento `TRIGGER` único** (nuevo tipo o `Decision outcome=TRIGGER_*`): un doc por trigger
   RECIBIDO (GEOFENCE_EXIT crudo con geofenceId+posición de entrega, AR ENTER crudo con lag, BT
   ACL, sig-motion) **con su disposición** (`armed` / `suppressed_rearm` / `refused_strategy` /
   `refused_permissions` / `not_armable(reason)` / `lookup_failed` / `orphan`). Mata de un golpe
   las ramas mudas 1-6 y 13, y da sentido pleno a la doctrina "todo trigger dispara SIEMPRE":
   hoy un trigger descartado es indistinguible de un trigger no entregado — exactamente la
   ambigüedad que costó el FN del 17-08 (EXIT entregado 11 h tarde).
2. **Sesión BT de verdad**: `SessionStarted(strategy=BLUETOOTH)`/`SessionEnded` alrededor de
   `detectParking`, con un id único por intento usado en TODOS sus verdicts (hoy mezcla
   `saved.id`/`vehicleId`, hueco 12).
3. **Respuesta del usuario al prompt** (`PROMPT_ANSWERED yes/ignored/timeout`): cierra el ciclo
   que `PROMPT_SHOWN` abrió.
4. **Resultado del backfill** (éxito con `saved.id` / rechazo con motivo) y el nudge del watchdog
   never-silent (huecos 7 y 11).
5. **Retención para `uiLocation`** y clave de join `deviceModel` estampada también en los eventos
   fuera de sesión (los keyed por geofenceId no dicen qué móvil los emitió — con Oppo+Redmi en la
   misma cuenta y el mismo geofenceId, un `DepartureVerdict pre-arm` es ambiguo hoy).

**Sobra / a adelgazar:**
1. **`LOCATION_FIX` a resolución completa**: para diagnóstico (no replay) bastaría el throttle
   10 s que ya usa uiLocation, o decimar como el route store; el replay denso podría subirse solo
   bajo un segundo flag (`trace_level=replay`) — recorta ~80-90% de los writes y el coste de radio.
2. **El doc `arm_<ts>`** (`Svc:1361-1376`): duplica lo que `SessionStarted` del coordinator ya
   dice y deja headers huérfanos sin outcome que ensucian el listado por `startedAt`. Con el
   evento `TRIGGER` del punto 1, se elimina.
3. **`STEP` por evento individual**: el rollup (`maxStepCount`) y los steps en cada `Decision`
   cubren el diagnóstico; el detalle por paso solo aporta al replay (mismo flag que 1).

Con 1-5 el trío `sessions/{id}` (header) + `events` + pins (`detectionPath`/`armEvidence`) es
suficiente para reconstruir cualquier sesión — incluidas las que HOY solo se explican con
`parkdiag.log` en mano.
