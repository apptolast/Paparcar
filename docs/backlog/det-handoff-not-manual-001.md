# DET-HANDOFF-NOT-MANUAL-001 · El handoff del safety net no es "el usuario pulsó Estoy conduciendo"

**Estado:** 🟢 ✅ DONE — master `26aec131` (squash 21-08, sin pushear). Pendiente §B.5 (instrumentar tasa de retractadas) y, en ticket propio, avisar a quien va de camino.


## Problema

Field-test 2026-08-19, 22:32 → 23:01, viaje **en bicicleta** con los dos móviles.

Ambos teléfonos abrieron una sesión de Coordinator estampada `ARM:MANUAL` / `evidence=manual`,
**sin que el usuario tocara nada**. El arm lo disparó `ParkingSafetyNetWorker`:

```
WorkManager (Oppo, no_backup/androidx.work.workdb)
  22:32:29  DepartureDetectionWorker                       (attempts=3; run original ~22:32:13)
  22:32:59  ReportSpotWorker + ClearActiveParkingSessionWorker
  23:17:31  ParkingSafetyNetWorker   interval=900000       (periódico de 15 min)
```

`ParkingSafetyNetWorker.kt:369` → `manualParkingDetection.start()` →
`ACTION_START_TRACKING` → `CoordinatorDetectionService.handleStartTracking()` →
`startParkingDetection(DetectionTrigger.MANUAL)` con `armEvidence = ArmEvidence.Manual` (el valor
por defecto del parámetro). Los **únicos dos emisores** de ese intent son el botón "Estoy
conduciendo" (`HomeViewModel` → `SaveManualParkingUseCase` / arranque manual) y este handoff
[DET-ARRIVAL-HANDOFF-001]. Salen indistinguibles.

Sesiones de diagnóstico afectadas:

| Móvil | uid | sesión | armEvidence | outcome |
|---|---|---|---|---|
| Oppo CPH2371 | `fiypNbElGlfFexLMpU9sNaMjRMD3` | `1787171533976` | `manual` (falso) | `aborted_unattended_human_powered` |
| Redmi 2201117TY | `WZB7oftWLDY1toGJrDwoRHnnYHx2` | `1787171592952` | `manual` (falso) | `aborted_unattended_human_powered` |

Además, la salida despachada por ese mismo run publicó dos plazas comunitarias fantasma y liberó
los dos aparcamientos, **sin que ningún coche se moviera** (el usuario iba en bici):

| Doc | Qué | Estado |
|---|---|---|
| `spots/d0a8f9d2-…` | plaza pública, Calle Galeote 31, conf 0.9, 22:32:59 | borrada a mano 2026-08-19 23:3x |
| `spots/49c47695-…` | plaza pública, Calle Góndola 5, conf 1.0, 22:33:13 | borrada a mano 2026-08-19 23:3x |
| `users/fiyp…/parkingHistory/d0a8f9d2-…` | **Ford Focus** aparcado desde 11:25 | `isActive=false` desde 22:32:59 → restaurado a mano 00:15 (`2a1b068c`, a 8 m del pin original) |
| `users/WZB7…/parkingHistory/49c47695-…` | C5 aparcado desde 22:06 | `isActive=false` desde 22:33:13 → restaurado a mano 23:43 (`a1d0ee92`, a 13 m del pin original) |

(Copia de los spots borrados en el scratchpad de la sesión: `phantom-spots-2026-08-19-backup.json`.)

### Daño de UNA salida falsa

No es un dato feo en la telemetría: destruye dos cosas y deja una tercera rota.

1. **Plaza fantasma publicada** a la comunidad como `AUTO_DETECTED` con confianza 0,9-1,0. Alguien
   conduce hasta un hueco que nunca se liberó.
2. **El usuario pierde su coche.** La sesión activa era la única respuesta a "¿dónde lo dejé?" y no
   vuelve sola.
3. **La geocerca desaparece** — `ReleaseActiveParkingSessionUseCase.kt:98` /
   `ProcessConfirmedDepartureUseCase.kt:81` llaman `removeGeofence(geofenceId)`. El coche queda
   aparcado sin vigilancia, así que la salida REAL siguiente ya no tiene ese trigger.

Y el disparador fue una deducción, no una medida: la red vio el teléfono lejos del pin e infirió que
el coche se había ido. El usuario iba en bici. **El teléfono lejos del coche no prueba que el coche
se haya movido.**

## Doctrina violada

1. **Provenance obligatoria** (`feedback_detection_trigger_provenance`): el diagnóstico debe poder
   decir QUÉ disparó cada sesión. Una etiqueta `manual` sobre un arm automático no es un dato
   pobre, es un dato **falso** — el propio diagnóstico de esta noche empezó concluyendo "lo declaró
   el usuario".
2. **`DET-STRATEGY-GATE-001`** (agujero LATENTE, no disparado esta noche): `startParkingDetection`
   exime a `MANUAL` de `coordinatorMayArm(strategy, trigger)`. Un arm automático etiquetado MANUAL
   entra por ese agujero **sea cual sea la estrategia del vehículo activo**. ⚠️ Corrección: esta
   noche el vehículo de la sesión era el **Ford Focus** (`addbe660`, SIN bluetoothDeviceId), no el
   Kamiq (`abf6c516`, BT `50:26:EF:16:1D:C0`) — así que `resolveStrategy` = COORDINATOR y el arm
   era legítimo por estrategia. El agujero sigue abierto: bastaría que el vehículo activo fuese el
   Kamiq para reproducir el fallo de 2026-08-01 que creó ese gate.
3. **`DET-SOLID-001`**: `manual` NO está en `weakLabels` de `EvaluateParkingDecisionUseCase`, así
   que un arm automático y sin verificar hereda la confianza de una declaración humana explícita y
   puede auto-confirmar en silencio sin que la sesión haya presenciado conducción.
4. **Copy que miente** (`feedback_no_internals_in_user_copy`):
   `CoordinatorDetectionService.kt:1428` → `DetectionTrigger.MANUAL -> "pulsaste 'Estoy
   conduciendo'"`. En este camino el usuario no pulsó nada.
5. **El evento NOMINA, solo el movimiento MEDIDO confirma** — aplicado al lado de la SALIDA: la red
   despachó salida, publicó plaza y liberó el coche sobre una partida no presenciada; la sesión que
   ella misma abrió demostró 29 min después que no hubo viaje de coche, y nadie deshizo nada.

## Señales / datos disponibles

- `DetectionTrigger` ya es un enum propio: cabe un valor nuevo sin tocar los call sites existentes.
- `ArmEvidence` ya tiene el vocabulario de etiquetas (`LABEL_*`) y el conjunto `weakLabels`.
- `ParkingSafetyNetWorker` conoce la sesión y el vehículo que está entregando (`session.vehicleId`,
  `session.geofenceId`), y sabe si encadenó backfill (`action.preconfirmed && action.backfillBounded`).
- El resultado de la sesión hija ya se persiste (`sessionOutcome`, `aborted_*`) y el service ya
  tiene epílogo de teardown donde engancharlo (`lastEndedArmTrigger`, el fold del cooldown sentry).

## Diseño

**A · El handoff deja de disfrazarse de usuario.**
- `DetectionTrigger.ARRIVAL_HANDOFF` nuevo + `ArmEvidence` propia (no verificada: la red NO
  presenció conducción, solo dedujo una salida).
- Nueva acción del service (`ACTION_ARRIVAL_HANDOFF`) o parámetro en el intent, de modo que
  `ACTION_START_TRACKING` vuelva a significar exactamente una cosa: el usuario pulsó el botón.
- El gate de estrategia **deja de eximir al handoff**: si el vehículo es de BT, la red no arma
  Coordinator.

**B · La salida deducida se desacopla: la PLAZA va rápido, el COCHE no se toca sin prueba.**
(principal — decisión del user, 2026-08-19)

Hoy el orden es: deducir salida → publicar plaza + liberar sesión + borrar geocerca → *después*
arrancar el seguimiento que averigua si hubo viaje. Destruye primero y comprueba luego, y ata en el
mismo commit dos decisiones con perfiles de riesgo **opuestos**:

- **La plaza vale por su frescura.** En un centro urbano su vida útil son minutos: retrasar la
  publicación 2-5 min mata el valor justo donde la app importa. La velocidad SÍ vale aquí.
- **Liberar la sesión propia no le sirve a nadie.** Nadie consume ese dato salvo el dueño del
  coche, y a él solo le perjudica. La velocidad NO vale nada aquí, y el daño (coche perdido +
  geocerca borrada) es personal y no se recupera solo.

Por tanto:

**B.1 · El coche: nada sin prueba.** La sesión de aparcamiento y su geocerca NO se liberan por una
salida deducida. Se mantienen hasta que la sesión hija **mide conducción real**
(`sustainedDrivingMs` drive-proof-gated, `DET-MOTOR-PROOF-001`). Si la sesión muere sin viaje, no
hay nada que deshacer: el coche nunca se perdió.

**B.2 · La plaza: se publica ya, clasificada por evidencia.** La velocidad se compra con
clasificación, no con espera:

| Clase de salida | Publicación |
|---|---|
| **Presenciada** — BT desconectado + alejamiento · EXIT de geocerca con conducción medida | inmediata, confianza de hoy, TTL normal (2 h) |
| **Deducida** — inferencia del worker, teléfono lejos del coche, sin conducción medida aún | inmediata pero **provisional**: confianza baja, marcada "sin confirmar" en UI, ordenada por debajo de las confirmadas y **TTL corto (10-15 min)** |

El caso normal no se ralentiza para protegerse del caso raro. Una provisional que recibe la prueba
de conducción **sube a confirmada** y recupera el TTL normal.

**El TTL corto es la pieza que hace viable publicar-y-revertir**, porque la reversión PUEDE fallar
(muerte de proceso, sin red, OEM-kill): una provisional que nadie confirma se muere sola. El modo de
fallo se autolimita en vez de dejar un fantasma 2 h. La retractación explícita es el camino rápido;
el TTL es el suelo.

**B.3 · Retractar es un estado, no una carrera de borrados.** `spotStatus: PROVISIONAL → CONFIRMED |
RETRACTED`, y aviso honesto a quien ya iba de camino (`enRouteCount` ya existe en el modelo).

**B.4 · Prerrequisito duro:** publicar-y-revertir solo funciona si el "no hubo viaje" llega RÁPIDO.
Esta noche tardó 29 min, y no porque fuera difícil de saber, sino porque el veredicto colgaba del
reloj de 15 min del prompt. Hace falta un veredicto explícito de *ninguna conducción creíble en N
minutos desde el arm* → **`DET-HUMAN-POWERED-EARLY-CLOSE-001` pasa de follow-up a prerrequisito**.

**B.5 · Instrumentar la política en vez de adivinarla:** tasa de provisionales que acaban
retractadas y tiempo hasta el veredicto, **por clase de salida**. Una clase que casi nunca miente se
promociona a confirmada directa; una que miente se queda provisional.

**B' · Reversión, para lo ya cometido.**
Retirar la plaza publicada (`spotId` de la sesión liberada), restaurar la sesión liberada — **Room
primero**, que es la fuente de verdad (`UserParkingRepositoryImpl.observeActiveSessions()` lee
`dao.observeActive()`), y el espejo de Firestore detrás — y **re-registrar la geocerca**.

El invariante vive en UN sitio: el epílogo que ya sabe con qué trigger nació la sesión y con qué
outcome murió. No un guard nuevo por cada camino de aborto.

## Criterio de éxito

- Una sesión abierta por el safety net aparece en telemetría como handoff, nunca como `manual`.
- En un móvil con vehículo BT emparejado, el handoff no abre sesión de Coordinator.
- Un handoff cuya sesión aborta sin viaje deja el coche aparcado donde estaba y su geocerca viva
  — **sin haber tenido que deshacer nada**.
- Una salida deducida sigue publicando plaza al instante, pero provisional y con TTL corto; si la
  sesión hija no prueba conducción, la plaza se retracta (o caduca sola) en minutos, no en 2 h.
- Una salida presenciada publica exactamente igual de rápido que hoy: la clasificación no puede
  penalizar el caso normal.
- Tests unitarios del veredicto de reversión + del gate de estrategia con el trigger nuevo.
- Campo: repetir el paseo/bici saliendo de un pin activo → el coche sigue aparcado al volver.

## Estado

- **A · el handoff deja de disfrazarse de usuario → HECHO** (sin commitear).
- **B · la salida deja de comprometer sin prueba → HECHO** (sin commitear).
- **B.3 · retractar es un estado, no un silencio → HECHO** (sin commitear).
- 1258 tests verdes, `compileProdDebugKotlinAndroid` y `assembleMockDebug` OK.
- Pendiente dentro de B: **B.5** (instrumentar tasa de retractadas por clase de salida — el evento
  `SPOT_RETRACTED` ya es el numerador). El TTL corto sigue siendo el suelo bajo todo esto.
- **Fuera de nuestro alcance hoy, ticket propio:** avisar a quien YA iba de camino. `enRouteCount` es
  un contador pelado, sin filas por usuario y **hoy sin ningún escritor** en la app: no existe
  registro de QUIÉN va a una plaza ni canal para alcanzarle. Hace falta modelar la intención "voy
  hacia ahí" antes de poder avisar de nada.

### B · lo implementado

| Pieza | Qué |
|---|---|
| `DepartureProof` (`Witnessed` / `Deduced`) | tipo nuevo en `domain/detection`: cuán probada está una salida. `RunDepartureCheckUseCase` la deriva — sólo un fix fresco a velocidad de conducción creíble es `Witnessed`; el reconcile del safety net (`preconfirmed`) y el fall-through por embarque AR son `Deduced` |
| `ProcessConfirmedDepartureUseCase(proof)` | en `Deduced`: publica la plaza **igual de rápido** pero provisional, y **NO** libera sesión ni geocerca; marca la sesión. En `Witnessed`: exactamente lo de siempre |
| `SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS` = 12 min | el suelo: una retractación puede fallar (muerte de proceso, sin red, OEM-kill), así que la vida del spot es corta por construcción |
| `UserParking.provisionalDepartureAtMs` + Room v19 | marca local (nunca sincronizada) de "salida deducida pendiente de prueba" |
| **publicar UNA vez** por deducción pendiente | sin esto, la red de seguridad re-deduce cada 15 min mientras el usuario esté lejos del coche y el spot parpadearía toda la tarde. Re-despachar sí se permite: es lo que sigue entregando el viaje a la detección viva |
| `FinalizeDeducedDepartureUseCase` | al MEDIRSE la conducción: re-publica el mismo id sin `provisional` (mismo documento, TTL completo) y **ahí sí** libera sesión + geocerca |
| hook en el coordinator | one-shot en el instante en que `driveProven` late |
| salida del watchdog ("me he ido") | explícitamente `Witnessed`: la palabra del usuario es la evidencia más fuerte que hay |

**Qué pasa si el viaje nunca prueba conducción** (el caso de la bici del 19-08): no hay nada que
deshacer, porque no se quitó nada. La plaza provisional caduca sola en 12 min, el coche sigue
aparcado donde estaba y su geocerca sigue armada para la salida de verdad.

### B.3 · lo implementado

| Pieza | Qué |
|---|---|
| `SpotStatus` (`CONFIRMED` / `PROVISIONAL` / `RETRACTED`) | el estado viaja con la plaza de punta a punta: dominio, documento de Firestore (`status`), caché Room (v20, `MIGRATION_19_20`), y los dos schedulers (Android + iOS). Default `CONFIRMED`: todo documento escrito antes de que el campo existiera venía de una salida presenciada, que es justo lo que significa |
| `SpotTtlPolicy.RETRACTION_GRACE_MS` = 2 min | una retractación **no borra**: marca y adelanta la caducidad. El documento tiene que seguir llegando el tiempo justo para explicarse; la limpieza de caducados que ya existía lo barre después |
| `SpotRepository.retractSpot` → `FirebaseDataSource.retractSpot(id, expiresAt)` | `update` de DOS campos, nunca `set`: una retractación no tiene por qué reescribir la dirección, el geohash ni los contadores comunitarios que otros llevan rato incrementando. Owner-only por las reglas ya desplegadas — no hacen falta reglas nuevas |
| `RetractDeducedDepartureUseCase` | el veredicto: **esta sesión terminó sin medir conducción**, luego la salida deducida de este viaje queda refutada y su plaza se retira. Retracta TODA deducción pendiente (un viaje que no llegó a velocidad tampoco fijó vehículo, así que no hay clave por la que preguntar) — y retirar de más es el lado seguro |
| gancho en el coordinator | en el bloque de fin de sesión, **detrás** del confirm retenido a propósito: un aparcamiento confirmado en el último instante sustituye la sesión pendiente, y entonces no queda nada que retractar |
| la marca `provisionalDepartureAtMs` **NO se limpia** | es también el guard de "esta deducción ya gastó su única publicación": limpiarla dejaría a la red re-deducir la misma salida 15 min después y republicar el mismo error. Y así una conducción medida MÁS TARDE sigue promocionando la plaza y liberando el coche por el camino de siempre — retractar retira un REPORTE, no cierra el caso |
| `DetectionEvent.SpotRetracted` (`SPOT_RETRACTED`) | la app admitiendo que publicó algo que no pudo sostener; espejo de `Reverted` y numerador de la tasa que medirá §B.5 |
| UI · retirada | fuera de la lista, del mapa y del contador, pero **sigue resolviéndose como plaza seleccionada** para que el peek pueda explicarse (eyebrow "Retirada", una frase honesta, y una sola acción de vuelta a las plazas que sí existen) en vez de cerrarse la hoja bajo el usuario |
| UI · sin confirmar | conserva el bucle comunitario entero — es una oferta real — pero lo dice: token `SIN CONFIRMAR` en la fila (en tinta, nunca teñido) y orden por debajo de toda plaza confirmada, con `sortedBy` estable para no tocar el orden existente |
| strings | 5 keys nuevas en los 9 locales; copy causa + consecuencia + remedio, sin mecánica interna |
| galería mock | `spot_mock_006` provisional en `FakeSpotRepository` + dos variantes nuevas de peek (sin confirmar / retirada) en `StateGalleryScreen` |

Tests nuevos (10): `RetractDeducedDepartureUseCaseTest` (5 — retira, conserva coche+marca, no-op sin
deducción, zona privada nunca publicó, fallo de red), `HomeSlicesTest` (3 — fuera de lista/mapa/
contador, seleccionable para explicarse, orden por debajo de las confirmadas),
`SpotDtoMapperTest` (2 — el `status` sobrevive los cuatro saltos DTO⇄dominio⇄entity, y los
documentos antiguos leen `CONFIRMED`).

### B.3 · consumidores auditados

| Sitio | Asumía | Clasificación |
|---|---|---|
| `HomeState.filteredNearbySpots()` (lista + `freeCount`) | toda plaza cercana está en oferta | **cerrado** — retiradas fuera, provisionales al final |
| `HomeState.toMapSlice().nearbySpots` (marcadores) | idem | **cerrado** — un pin de una plaza que retiramos es exactamente el fallo que esto viene a evitar |
| `toBrowseListSlice().hasAnySpots` (barra de filtros + empty state) | "¿hay algo?" = lista no vacía | **cerrado** — cuenta sólo lo disponible |
| `HomePeekHandle` / `HomeState.selectedSpot` (lista SIN filtrar) | resuelve la selección sobre `nearbySpots` | **cerrado por diseño** — es justo lo que permite que la retirada se explique |
| `HomeScreen.onSpotMarkerClick` (`spotsById` sin filtrar) | mapa y mapa-de-ids comparten origen | **exento** — no hay marcador que pulsar para una retirada; el índice es un superconjunto inofensivo |
| `homeSheetSpotItemIndex` (auto-scroll a la seleccionada) | la seleccionada está en la lista | **exento** — devuelve −1 y no hay scroll; el peek ya cuenta la historia |
| `SpotRepositoryImpl.observeNearbySpots` (barrido de caducados) | borra de Firestore lo caducado | **cubierto por convergencia** — la retirada adelanta `expiresAt`, así que ese mismo barrido la borra pasada la gracia, sin código nuevo |
| `firestore.rules` (`update`) | el dueño edita libre; terceros sólo contadores | **exento** — retracta el dueño; no hay cambio de reglas |
| `ReportManualSpotUseCase` | un reporte manual es palabra humana | **exento** — publica con `provisional=false` → `CONFIRMED`, como debe |
| `decayedConfidence` (rampa de frescura) | confianza × tiempo restante | **exento** — eje distinto: la fiabilidad dice cuánto se fía la comunidad del reporte, el status si algo midió que el coche saliera |
| `enRouteCount` | contador comunitario ya existente | **ABIERTO, ticket propio** — sin filas por usuario y sin escritor hoy: no se puede avisar a quien no sabemos que va |

### A · lo implementado

| Pieza | Qué |
|---|---|
| `DetectionTrigger.ARRIVAL_HANDOFF` | valor propio; MANUAL queda documentado como "intención humana explícita y nada más" |
| `ArmEvidence.ArrivalHandoff` (`arrival_handoff`) | evidencia propia, débil por definición: la deducción es sobre el TELÉFONO |
| `ArrivalHandoffDetection` + `ACTION_ARRIVAL_HANDOFF` | puerta propia (impl Android + no-op iOS + fake); `ACTION_START_TRACKING` vuelve a significar solo el botón |
| `ParkingSafetyNetWorker` | llama al puerto del handoff, no al del botón |
| `coordinatorMayArm` | el handoff se gatea como cualquier nominador automático |
| `weakLabels` | `arrival_handoff` dentro: sin conducción medida se PREGUNTA |
| copy de arranque (DEBUG) | "el móvil se alejó de la plaza vigilada…", en vez de "pulsaste 'Estoy conduciendo'" |
| `docs/detection/PARKING-DETECTION.md` | entrada del cambio |

Tests nuevos: gate (handoff rechazado bajo BLUETOOTH/NONE + "MANUAL es la ÚNICA exención",
que fallará si alguien vuelve a colar un trigger por ahí), nudge del pending huérfano, y el par
prompt-sin-conducción / confirm-con-conducción en `EvaluateParkingDecisionUseCase`.

## Consumidores auditados

Barrido de `DetectionTrigger.MANUAL`, `ArmEvidence.Manual` y `LABEL_MANUAL`:

| Sitio | Asumía | Clasificación |
|---|---|---|
| `ParkingStrategyResolver.coordinatorMayArm` | MANUAL siempre admitido | **cerrado** — el handoff ya no entra por ahí; test que fija la exención en exactamente 1 |
| `CoordinatorDetectionService.startParkingDetection` (exención del gate) | MANUAL = intención humana | **cerrado** — comentario y comportamiento alineados |
| `EvaluateParkingDecisionUseCase.weakLabels` | `manual` = evidencia fuerte | **cerrado** — `arrival_handoff` añadido |
| `PendingNudgeDecision` | trigger MANUAL con trato propio | **cerrado** — handoff incluido, con su razón propia (la salida ya se cometió) |
| `CoordinatorDetectionService` copy de arranque | "pulsaste 'Estoy conduciendo'" | **cerrado** — `when` exhaustivo obligó a cubrir el valor nuevo |
| `ConfirmParkingUseCase:182` (`ArmEvidence.isVerifiedLabel`) | solo `verified_*` saltan el guard de repark | **exento** — `arrival_handoff` no es verificado, así que el guard le aplica; es el lado correcto |
| `ArmEvidence.isVerifiedDeparture` (seed de `hasEverReachedDrivingSpeed`) | solo speed/enter siembran | **exento** — el handoff no siembra nada; la sesión mide su propio viaje |
| Room `parking_sessions.armEvidence` · `ParkingHistoryDto.armEvidence` | etiqueta como `String?` libre | **exento** — aditivo, sin migración |
| `EnrichParkingSessionWorker.PIN_TO_PIN_ELIGIBLE_PATHS` | lee `detectionPath`, no `armEvidence` | **exento** — otro eje |
| `ManualParkingDetection` (`HomeViewModel`, `SaveManualParkingUseCase`) | puerta del botón | **cerrado** — vuelve a tener un solo llamador por significado |

### B · consumidores auditados

| Sitio | Asumía | Clasificación |
|---|---|---|
| `RunDepartureCheckUseCase` | toda salida confirmada se comete igual | **cerrado** — deriva `DepartureProof` y lo pasa |
| `CoordinatorDetectionService.handleWatchdogDeparture` | idem | **cerrado** — `Witnessed` explícito (palabra del usuario) |
| `ReportSpotScheduler` / `ReportSpotWorker` / `IosReportSpotScheduler` | un solo TTL por tipo | **cerrado** — `provisional` extremo a extremo; iOS incluido |
| `SpotTtlPolicy` | 2 h auto / 15 min manual | **cerrado** — tercera vida, en la única fuente de verdad compartida con iOS |
| `ConfirmParkingUseCase` (replace) | la sesión previa ya estaba liberada al aparcar de nuevo | **cubierto por convergencia** — `getActiveSessionByVehicle` + `removeGeofence(replacedId)` ya sustituye la sesión que ahora sobrevive |
| Guard `ImplausibleRepark` | un pin nuevo cerca de otro fresco sin conducción medida es sospechoso | **exento** — con prueba de conducción el finalize ya liberó la vieja; sin ella, desconfiar es correcto |
| `UserParkingReconcile` (sync remoto) | los campos locales se preservan explícitamente | **cerrado** — la marca es local y se preserva |
| `GeofenceJanitorWorker` (dedup de sesiones activas) | una activa por vehículo | **exento** — §B no crea una segunda: mantiene la que ya existía |
| Dev Catalog / galería mock | — | **exento** — §B no añade pantalla ni estado nuevo; el badge "sin confirmar" del spot llega con B.3 |

## Follow-up separado

`DET-HUMAN-POWERED-EARLY-CLOSE-001` — la sesión de esta noche siguió viva 19 min tras llegar a
casa, reciclando CANDIDATE↔Notified cada 5 min, porque el veredicto `humanPoweredRide` solo se
consulta en la rama del timeout de respuesta (15 min). El veredicto debe emitirse cuando la
evidencia está, no cuando expira un reloj.
