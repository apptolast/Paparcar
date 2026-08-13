# Plan de implementación iOS — Detección de aparcamiento

**Fecha:** 2026-08-11 · **Base:** `docs/ANDROID-DETECTION-AUDIT.md` (auditoría del mismo
día). **Sustituye** a `docs/IOS_PLAN.md` (2026-05-24, pre-DET-SOLID — mover a
`docs/archive/`; su parte de CI/certs/Fastlane sigue vigente y se referencia en §9).

**Pregunta guía:** no "¿cómo traduzco esto?" sino **"¿qué necesita iOS para cumplir la
misma función, y la necesita siquiera?"**.

**Premisas verificadas contra código (correcciones al encargo):**
- El modo replay YA existe (`DetectionTraceReplayer`, commonTest) — la Fase 0 lo
  promueve, no lo crea.
- BT no es señal de scoring: es **override determinista** (`EvaluateBtArbitrationUseCase`)
  y selector de estrategia por conexión ACL viva. En iOS desaparece entero (§2.4).
- iOS ya tiene 20/26 puertos con impl real (CLLocation, CLCircularRegion, CMPedometer,
  UNUserNotificationCenter, CMMotionActivity, CLGeocoder, Overpass, NSUserDefaults…).
  El gap es el **orquestador**, no los sensores.
- DET-TIERS-001 ya declara: "iOS: todos quedan en nivel asistido". El plan lo implementa,
  no lo decide.

> **DECISIONES RESUELTAS 2026-08-13** — los 9 puntos `DECISIÓN:` de este plan y de la
> auditoría fueron resueltos por el usuario. Tabla de resoluciones y desglose en tickets
> de la Fase 0: **`docs/backlog/ios-f0-001.md`** (IOS-F0-01…09). En resumen: Always tras
> el primer park; tiers iOS = Asistido +/Asistido con Automático oculto; radio mínimo
> 100 m explícito en contrato; heurística de audio aparcada a F5+; `Kind.ACTIVITY` sí
> (F0); nudge retroactivo sí; alarma exacta → asumir inexacto; toggles muertos →
> eliminar; allowBackup → excluir estado de detección.

---

## 1. El modelo mental: push/streaming → wake-and-query

Android es **push**: el proceso (FGS/SENTRY) o Play Services empujan eventos a un proceso
que puede estar siempre listo. iOS es **wake-and-query**: el sistema despierta la app
(~10–30 s, o indefinidamente si arranca una sesión de localización) en momentos que él
decide, y en esa ventana la app **consulta el pasado** y decide.

La arquitectura de Paparcar está inusualmente bien preparada para esto, por tres
propiedades que ya existen:
1. **"El evento NOMINA, solo el movimiento MEDIDO confirma"** — un wake iOS es una
   nominación como cualquier otra; la confirmación sigue siendo del evaluador puro.
2. **El núcleo es replayable** (primitivas dentro, reloj inyectable): reconstruir un
   trayecto desde historial CMMotionActivity + CMPedometer + fixes y pasárselo en batch
   es exactamente lo que hace `DetectionTraceReplayer` con las trazas de campo.
3. **La durabilidad ya vive en side-records, no en el proceso** (auditoría §5.4): el
   estado in-memory del coordinator se pierde en Android igual que se perderá en iOS, y
   el sistema ya sabe recuperarse de eso.

```
ANDROID                                   iOS
───────                                   ───
SENTRY FGS residente + sig-motion    →    CLCircularRegion + SLC + CLVisit (el "sentry"
+ alarma exacta + worker 15 min           lo mantiene el OS, con proceso MUERTO)
FGS ACTIVE trip-scoped (GPS vivo)    →    sesión CLLocationManager background acotada
                                          al trayecto (arrancada en el wake del EXIT,
                                          cerrada al confirmar/abortar)
AR transitions push                  →    CMMotionActivityManager: query histórica en el
                                          wake + updates en vivo durante la sesión
Step detector/counter push           →    CMPedometer: query retroactiva por rango de
                                          fechas (mejor que el contador acumulativo MIUI)
```

---

## 2. Tabla de mapeo funcionalidad Android → decisión iOS

Categorías: **NO APLICA** · **EQUIVALENTE** · **OTRA FORMA** · **IMPOSIBLE/DEGRADADO**.

### 2.1 Lifecycle y triggers

| Android | Decisión iOS | Justificación |
|---|---|---|
| `CoordinatorDetectionService` (FGS ACTIVE, GPS vivo durante el trayecto) | **OTRA FORMA** | Nuevo `IosDetectionController` (iosMain): en el wake que arma (region EXIT / manual), arranca `allowsBackgroundLocationUpdates=true` + `pausesLocationUpdatesAutomatically=false` (ya así en `IosLocationDataSourceImpl`) y alimenta el MISMO `CoordinatorParkingDetector` con el Flow de fixes. Mientras fluyen updates, iOS mantiene la app viva — es el equivalente funcional del FGS sin notificación obligatoria. Cierre: al confirmar/abortar, `stopUpdatingLocation` y la app vuelve a suspenderse |
| SENTRY residente entre aparcamientos (DET-RESIDENT-FGS-001) | **NO APLICA** | La razón de existir del SENTRY es que Android 12+ prohíbe arrancar un FGS desde background cuando el OEM mata el proceso. En iOS `CLCircularRegion` y SLC **relanzan la app terminada** (incluida tras reboot, tras primer unlock) — el "residente" lo pone el OS gratis, sin proceso, sin notificación, sin batería. La telemetría de kills SENTRY tampoco aplica |
| `SignificantMotionMonitor` (wake por movimiento en SENTRY) | **EQUIVALENTE** (aprox.) | Significant Location Changes (SLC, ~500 m/celda) + `CLVisit` (salida de un lugar) despiertan la app muerta. Menos granular que el sensor de sig-motion, pero el rol es el mismo: wake barato para pasar la safety net |
| `ExactHeartbeatScheduler` (alarma exacta 5 min aparcado) | **NO APLICA** | No hay AlarmManager. Su terreno (Doze + Play Services muerto) no existe: los wakes de región/SLC/visita los entrega el propio OS. No inventar un sustituto — el heartbeat era una muleta específica de Android |
| `BootCompletedReceiver` (re-registro tras reboot/update) | **NO APLICA** (con matiz) | Las CLRegion sobreviven reboots y relanzan la app muerta. Matiz: el inventario de regiones puede desincronizarse de Room (reinstalación, "Reset Location & Privacy", monitoringDidFail). El equivalente del janitor es un **reconcile `monitoredRegions` ↔ sesiones activas** en cada arranque de app y en cada wake — código trivial, mismo `VehicleFenceOwnershipPolicy` |
| Sticky-restart null-intent (DET-B-02) | **NO APLICA** | No hay START_STICKY. El equivalente conceptual (relanzamiento con estado in-memory perdido) se cubre con la reconstrucción de §4 |
| `ManualParkingDetectionImpl` ("estoy conduciendo") | **EQUIVALENTE** | `IosDetectionController.start(MANUAL)` — reemplaza el stub no-op actual |

### 2.2 Geocercas

| Android | Decisión iOS | Justificación |
|---|---|---|
| 3 fences GMS por sesión (EXIT + ENTER twin + witness) | **EQUIVALENTE con colapso a 1** | Una `CLCircularRegion` entrega `didExitRegion` Y `didEnterRegion` — el twin ENTER no se necesita. El witness (diagnóstico de entrega) tampoco: en iOS el fallo típico es distinto (radio < ~100 m poco fiable, sin celdas no despierta). **1 región por sesión.** Crítico por el **límite de 20 regiones por app** (vs 100 de GMS): con 1/sesión y sesiones activas acotadas, sobra; el reconcile de §2.1 vigila el presupuesto |
| Radio adaptativo 60–200 m (`geofenceRadiusFor`) | **EQUIVALENTE con suelo** | `IosGeofenceManagerImpl` ya existe pero fuerza `MIN_RADIUS_M = 100` silenciosamente. Correcto en espíritu (bajo ~100 m iOS es poco fiable), mal en forma (sin telemetría del override). Formalizar: radio = `max(geofenceRadiusFor(...), iosMinRadius)` con el override logueado. **DECISIÓN: [radio mínimo iOS]** — aceptar 100 m fijo o medir en field-test |
| EXIT → `getForegroundService` privilegiado → intake serializado | **OTRA FORMA** | `didExitRegion` llega al delegate con la app relanzada en background. `IosGeofenceEventBusImpl` (Channel) ya existe y en iOS SÍ es el canal real (en Android está muerto — auditoría G2). El controller consume el bus y aplica `EvaluateGeofenceExitUseCase` (ya extraído para esto, [AUDIT-A9-KMP-001]) |
| Boundary vs stale EXIT (split por distancia de entrega) | **EQUIVALENTE** | Mismo evaluador; en iOS el análogo del "zombie EXIT de MIUI" es el wake tardío — el split y el probe corto (DET-ZOMBIE-PROBE) aplican igual |
| Límite GMS 100 fences sin manejar (auditoría G3) | — | En iOS nace manejado (presupuesto 20 explícito en el reconcile). Android debería copiarlo |

### 2.3 Señales de actividad y pasos

| Android | Decisión iOS | Justificación |
|---|---|---|
| AR transitions push (IN_VEHICLE ENTER/EXIT, doble carril evidencia+decisión) | **OTRA FORMA** | `CMMotionActivityManager.queryActivityStarting(from:to:)` reconstruye automotive→walking **hacia atrás** en la ventana de wake (hasta 7 días de historial). En vivo (sesión GPS abierta), `startActivityUpdates` alimenta `onVehicleExit()`/`DepartureEventBus.onVehicleEntered()` igual que hoy (`IosActivityRecognitionManagerImpl` ya lo hace, menos STILL que fue eliminada). La **escalera AR de armado** (`EvaluateArEnterArmUseCase`) se alimenta con el timestamp del ENTER histórico — misma regla enter-precedes-exit, misma admisibilidad de session-birth |
| Step detector (eventos) + step counter (acumulativo) + seals (counter, position, moment) | **OTRA FORMA — y mejor** | `CMPedometer.queryPedometerData(from:to:)` da pasos exactos ENTRE dos instantes. El diseño del seal (DET-STEP-BUDGET-ORIGIN + DET-TRIP-WITNESS) se simplifica: no hay contador acumulativo que congele MIUI — la patología frozen-counter (DET-FROZEN-COUNTER) casi desaparece; el liveness-witness se sustituye por la disponibilidad de la query. `IosStepDetectorSource` ya existe (live); faltan `StepCounterSource` y `DetectionStepAnchors` iOS (query + NSUserDefaults) |
| Presupuesto de pasos de la safety net / honest close | **EQUIVALENTE** | Mismos evaluadores puros; la impl iOS de `DetectionStepAnchors` responde con queries de rango en lugar de deltas de contador |

### 2.4 Bluetooth

| Android | Decisión iOS | Justificación |
|---|---|---|
| Estrategia BT completa: receiver ACL de manifest, `BtConnectionStore`, `BluetoothDetectionService`, `BluetoothParkingDetector`, árbitro `EvaluateBtArbitrationUseCase`, tier AUTOMATIC | **IMPOSIBLE / DEGRADADO** | Todo cuelga de Bluetooth **Classic**: broadcasts ACL del stack y `bondedDevices`. CoreBluetooth es solo BLE-GATT: no hay eventos de conexión/desconexión del manos libres del coche para apps de terceros, no hay lista de emparejados. **No existe árbitro BT en iOS.** `IosBluetoothScanner` ya lo refleja (`getBondedDevices()` vacío) |
| Consecuencia en estrategia | — | `ParkingStrategyResolver.strategyFor` en iOS nunca resolverá BLUETOOTH (sin MACs emparejadas): siempre COORDINATOR o NONE. Sin cambios de código — la degradación es emergente y correcta |
| Consecuencia en tiers | — | El tier AUTOMATIC no existe en iOS. Los tiers DET-RELIABILITY/DET-TIERS **colapsan a "asistido con Always" / "asistido sin Always"** (§6). Regla de oro intacta: falso negativo avisado > falso positivo silencioso — el nudge de confirmación ES el tier |
| Exploración futura (no bloqueante) | — | **DECISIÓN: [heurística de audio del coche]** — `AVAudioSessionRouteChangeNotification` puede indicar conexión/desconexión del audio del coche, pero solo con la app despierta y sesión de audio activa: no sirve como trigger de proceso muerto. Catalogar como experimento post-MVP, no como pilar |

### 2.5 Workers → ¿qué los reemplaza?

Regla de valoración: BGTaskScheduler tiene garantías **mucho** más débiles que WorkManager
(puede tardar horas/días, presupuesto opaco). Por cada worker: ¿inline en la ventana de
wake, BGTask asumiendo latencia, o replanteo?

| Worker Android | iOS | Forma |
|---|---|---|
| `DepartureDetectionWorker` (check con escalera de retries 15/45/105 s) | **Inline en el wake** | El wake del EXIT abre la sesión de localización → la app queda viva → `RunDepartureCheckUseCase` corre con su escalera tal cual (coroutines + delay, sin WorkManager). La supervivencia a process-death que justificaba WM la da la reconstrucción de §4 (el siguiente wake re-deriva el departure del historial) |
| `ParkingSafetyNetWorker` (15-min periodic + check-now mesh) | **Replanteo: mesh de wakes del OS** | No hay periodic fiable. La malla iOS: region ENTER (cure/re-seal — ya existe el carril), SLC/CLVisit wakes, app-start, y `BGAppRefreshTask` como tick oportunista. En cada wake corre `EvaluateSafetyNetCheckUseCase` (puro, compartido). La pérdida del "cada 15 min garantizados" se acepta: en iOS el EXIT de región es mucho más fiable que en OEMs agresivos — la red existe para un fallo que iOS tiene menos |
| `ParkingBackfillWorker` | **Inline** tras el dispatch de departure en el mismo wake (mismos guards: `isRunning`, `EvaluateBackfillDeferralUseCase`) |
| `GeofenceJanitorWorker` (12 h + once) | **Replanteo: reconcile on-wake** | `monitoredRegions` ↔ Room en app-start y en cada wake (§2.1). Sin scheduler |
| `RegisterActivityTransitionsWorker` | **NO APLICA** | No hay registro AR que se pierda: CMMotionActivity es query-based |
| `SaveNewParkingSessionWorker` / `ClearActiveParkingSessionWorker` / `UpdateParkingSessionAddressAndPlaceWorker` (Firestore) | **Inline + cola persistida + BGProcessingTask de arrastre** | Intento inmediato (online normal); si falla, encolar en una cola durable (Room/NSUserDefaults) drenada en cada wake y en un `BGProcessingTask(requiresNetworkConnectivity=true)`. El `IosParkingSyncScheduler` actual (coroutine+retry sin persistencia) se completa con la cola — es la carencia que ya anotaba IOS-SYNC-001 |
| `EnrichParkingSessionWorker` (geocode + route snap) | **Split** | Geocode inline en el wake (CLGeocoder funciona offline-ish y es rápido). Route-snap (Overpass + matcher HMM) → cola diferida: BGProcessingTask o al abrir la app. Resuelve de paso la deuda del presupuesto de retry compartido (auditoría) |
| `ReportSpotWorker` (publicar plaza, TTL-in-queue) | **Inline + cola con el MISMO gate TTL** | El gate "si expiró en cola, no publicar" es oro en iOS, donde la cola puede tardar horas |
| `FirstParkNudgeWorker` (24 h) | **Replanteo trivial** | `UNCalendarNotificationTrigger` programada localmente; la evaluación de throttle al abrir la app |

### 2.6 Permisos, notificaciones, fiabilidad

| Android | Decisión iOS | Justificación |
|---|---|---|
| Notificación FGS persistente + canal SENTRY MIN | **NO APLICA** | Sin FGS no hay notificación obligatoria. El indicador azul/flecha de localización del sistema es la superficie honesta equivalente (y no la controlamos) |
| Notificaciones accionables (prompt, saved-confirm morphing, nudge, still-parked) | **EQUIVALENTE** | `UNUserNotificationCenter` + categorías con acciones; `IosAppNotificationManagerImpl` ya implementa Confirm/Deny. Portar el resto de la superficie (nudge persist-FIRST incluido — el contrato está en el puerto común) |
| Exención de batería + autostart OEM + `isAggressiveOem` | **NO APLICA** | No existe el concepto. `IosOemBackgroundReliabilityManagerImpl` ya devuelve N/A. Queda P5 de la auditoría: que el evaluador de fiabilidad no invente un issue de batería en iOS (§6) |
| `POST_NOTIFICATIONS`, background location, AR | **EQUIVALENTE** con flujo Apple (§5) |
| PARKDIAG + Firestore diagnostics + trace2fixture | **EQUIVALENTE gratis** | `FirestoreDetectionEventLogger` es commonMain: las sesiones iOS ya podrán volcar diagnostics y convertirse en fixtures con el MISMO pipeline. El stamp de fiabilidad de sesión cambia de campos (sin `batteryUnrestricted`; añadir `hasAlwaysAuthorization`, `lowPowerModeEnabled`, `backgroundRefreshStatus`) |

---

## 3. ¿Necesita iOS un "servicio escuchando todo el tiempo"? — NO

Análisis con el ciclo de vida real:

1. **Entre trayectos (equivalente SENTRY):** con permiso Always, `CLCircularRegion`
   monitoring y SLC corren **con la app terminada**; el OS la relanza en background al
   cruzar la región (`application(_:didFinishLaunchingWithOptions:)` con
   `UIApplication.LaunchOptionsKey.location`). No hay nada que mantener vivo. Un proceso
   residente sería imposible además de inútil (iOS lo suspendería).
2. **Durante el trayecto (equivalente FGS ACTIVE):** la sesión
   `startUpdatingLocation` con `allowsBackgroundLocationUpdates=true` mantiene la app
   ejecutando en background mientras entrega fixes — indefinidamente, mientras no la
   paremos y el usuario conserve Always. Cobertura del caso: el EXIT despierta → se abre
   la sesión → el coordinator sigue el trayecto en vivo → confirmación por egreso →
   `stopUpdatingLocation` → suspensión. **La sesión GPS acotada al trayecto cubre el
   caso.**
3. **Los agujeros reales** no se tapan con un servicio sino con la reconstrucción (§4):
   - El wake del EXIT puede llegar tarde (región de 100+ m, sin celdas) → el arranque de
     ruta ya se backdatea (DET-ROUTE-ORIGIN) y la escalera AR admite ENTER históricos.
   - iOS puede matar la app mid-trip por presión de memoria (raro con sesión de
     localización activa, posible) → §4.
   - El usuario con solo When-in-use: no hay wake con app cerrada → tier degradado (§6),
     detección solo con la app usada recientemente + nudge.

Conclusión: **no construir ningún "keep-alive"** (timers de audio, location-fantasma,
etc. son patrones de rechazo en review y traicionan el diseño). El sistema push del OS +
sesión trip-scoped + reconstrucción cubren la función.

---

## 4. Process death mid-trip: persistencia y reconstrucción

Doctrina heredada (auditoría §5.4): estado vivo in-memory, durabilidad en side-records,
recuperación por reconciliación. En iOS se mantiene, con una mejora estructural: el
pasado es consultable (motion + pedometer), así que la recuperación no depende solo de
side-records — puede **re-derivar el trayecto**.

**Side-records iOS (puerto común nuevo `DetectionSideStore` o impls NSUserDefaults de
los existentes — ver F0):**
- `pending_arm` (armId, trigger, armedAt, sawDriving) — igual que `PendingDetectionStore`.
- Anclas + seals por sesión (posición, momento; el counter se sustituye por queries).
- `exit_delivered_*`, `arrival_resolution_*` — mismos contratos.
- Nudge pendiente — ya portable (AppPreferences común, impl NSUserDefaults existente).

**Protocolo de reconstrucción (el corazón del port):**
```
wake (region EXIT | ENTER | SLC | visit | app-start | BGAppRefresh)
  1. Leer side-records + sesión Room. ¿Había un arm pendiente sin resolver?
  2. Query CMMotionActivityManager [armedAt … now] → segmentos automotive/walking
  3. Query CMPedometer por sub-rangos → pasos por segmento
  4. Fixes disponibles: el fix del wake + (si la sesión GPS siguió viva) el trail
  5. Componer List<TraceEvent> (FIX/STEP) → ingesta batch del coordinator
     (el replayer promovido a commonMain, reloj virtual = timestamps reales)
  6. El evaluador decide como siempre: Confirmed / Prompt / zona / nudge — con las
     MISMAS reglas de admisibilidad (session-birth, enter-precedes-exit, gap-anchor)
```
Nota honesta: la reconstrucción tiene menos densidad de fixes que el streaming (a menudo
solo el fix del wake). Eso empuja hacia `Prompt`/zona en vez de confirmación silenciosa —
**correcto por doctrina**: es el tier asistido comportándose como promete. El caso rico
(sesión GPS viva todo el trayecto) confirma en silencio igual que Android.

**Extensión del modelo TraceEvent (F0):** añadir `Kind.ACTIVITY` (automotive/walking
enter/exit) o alimentar la escalera AR por separado — decidir en diseño de F0 con un
fixture de prueba. Los eventos FIX/STEP actuales ya cubren el 90 %.

---

## 5. Mapa de permisos iOS

**Orden de petición (cumpliendo el flujo que Apple exige y el tiering CORE/PRODUCER):**

| Paso | Permiso | Momento | Qué desbloquea |
|---|---|---|---|
| 1 | Location **When-in-use** (`NSLocationWhenInUseUsageDescription`) | Onboarding, pantalla de permisos (= CORE) | Mapa, spots cercanos, aparcado manual, find-my-car con app abierta |
| 2 | **Notificaciones** (`UNUserNotificationCenter.requestAuthorization`) | Onboarding (= PRODUCER, como Android) | Prompts/nudges — sin esto el tier asistido pierde su superficie de pregunta |
| 3 | **Motion & Fitness** (`NSMotionUsageDescription`) | Onboarding, tarjeta PRODUCER | CMMotionActivity + CMPedometer: reconstrucción, egreso, presupuesto de pasos |
| 4 | Location **Always** (`NSLocationAlwaysAndWhenInUseUsageDescription`) | **Escalado contextual** — NO en el onboarding | Wakes con app cerrada: región EXIT, SLC, visitas → detección automática real |

Reglas duras del paso 4 (comportamiento iOS real):
- Solo se puede pedir Always **después** de When-in-use; iOS puede conceder "Always
  provisional" y preguntar al usuario más tarde por su cuenta; una sola oportunidad de
  diálogo — si se pide en frío, se quema.
- Patrón recomendado (y amable con la review): pedir Always tras el **primer aparcado
  manual** o al activar "detección automática" en Ajustes — con pantalla previa de valor
  ("para liberar tu plaza al irte incluso con la app cerrada"). Es el análogo exacto del
  guide-dialog de background location Android ya existente.
- **DECISIÓN: [momento del escalado Always]** — post-primer-park (recomendado) vs toggle
  de Ajustes vs banner Home tipo `Blocked`.

**Funcionalidad viva por nivel (colapso de tiers DET-RELIABILITY-001 en iOS):**

| Nivel de permiso | Estado de detección | Tier mostrado |
|---|---|---|
| When-in-use + Motion + Notifs + **Always** | Wake-and-query completo + sesión GPS trip-scoped → **asistido de alta calidad** (confirmación silenciosa con egreso medido; prompt cuando la evidencia es débil) | **Asistido** (el techo iOS; sin fila BT, sin fila batería) |
| When-in-use + Motion + Notifs, **sin Always** | Sin wakes con app cerrada. Detección solo si la app se usó durante/tras el trayecto; nudge retroactivo al abrir ("¿aparcaste a las 18:32?" vía historial motion) | **Asistido (limitado)** — `DetectionReadiness.Blocked(BACKGROUND_LOCATION)` reutilizado |
| Solo When-in-use | Consumidor + manual (= CORE Android) | `Inactive` |
| "Approximate location" activado (iOS 14+) | Detección inviable (anclas de ±1–2 km) | Tratar como Blocked con CTA a Ajustes — análogo del caso COARSE Android (auditoría P1) |
| Motion denegado | Sin pasos/actividad: cae el egreso por pasos; queda egreso cinemático + prompts | Degradación parcial — issue propio en el report de fiabilidad |

**Cambios de modelo (F0, auditoría P5):** `DetectionReliabilityInput` necesita entradas
platform-aware: en iOS la pata "batería" no existe (ni satisfecha ni issue — N/A) y la
pata BT es estructuralmente imposible (no debe generar el issue "empareja tu coche" con
un CTA imposible). Propuesta: `DeviceCapabilities(supportsBtStrategy, supportsBatteryExemption)`
inyectado al evaluador; el tier iOS se computa `ASSISTED` techo con sub-estado
con/sin-Always. **DECISIÓN: [naming de tiers iOS en UI]** — "Asistido" a secas vs
"Asistido +" cuando hay Always (reutilizando la copy existente).

---

## 6. Info.plist y review de Apple

Ya presente (verificado): las 4 usage descriptions + `UIBackgroundModes: location,
fetch, processing`. Falta:
- `BGTaskSchedulerPermittedIdentifiers` — p. ej. `io.apptolast.paparcar.sync`
  (BGProcessingTask, red) y `io.apptolast.paparcar.refresh` (BGAppRefreshTask).
- Revisar el TEXTO de las usage descriptions con el criterio de review: la justificación
  que pasa es la de **valor personal primero** (doctrina DET-READY-001): "recordar dónde
  aparcaste y avisarte al salir" — no "compartir plazas con la comunidad" como primera
  línea. Location Always es lo más auditado: la descripción debe explicar el caso de
  background concreto (detectar que te vas de tu plaza con la app cerrada).
- `GoogleService-Info.plist` sigue ausente (bloqueante de build Firebase, ya trackeado).
- La parte de certs/Fastlane/CI del viejo IOS_PLAN.md §4 sigue válida tal cual.

Riesgo de review conocido: apps con `location` en UIBackgroundModes sin uso evidente son
rechazadas — la primera build para review debe llevar la detección funcionando o el modo
retirado.

---

## 7. Estructura final de puertos (expect/actual NO; Koin ports SÍ)

Decisión ratificada por la auditoría: la detección no usa expect/actual y no debe
empezar a usarlo. Contratos y semántica **push vs pull** por puerto:

| Puerto (commonMain) | Semántica | Android | iOS |
|---|---|---|---|
| `LocationDataSource` | **push** (Flow de fixes) | Fused | `IosLocationDataSourceImpl` ✅ |
| `GeofenceManager` | comandos (create/remove/removeAll) | GMS 3-fence | CLRegion 1-fence ✅ (ajustar suelo de radio) |
| `GeofenceEventBus` | **push** (bus) | muerto (G2) → unificar | canal real ✅ |
| `ActivityRecognitionManager` | hoy: register/unregister (**push**) | Play Services ✅ | live ✅; **AGREGAR** `queryTransitions(fromMs, toMs)` (**pull**) para la reconstrucción — Android lo implementa devolviendo vacío o su bus histórico |
| `StepDetectorSource` | **push** (eventos) | sensor ✅ | CMPedometer live ✅ |
| `StepCounterSource` | **pull** (lectura acumulada) | sensor ✅ | **AGREGAR** impl query CMPedometer |
| `DetectionStepAnchors` | **pull** (seals) | prefs+counter ✅ | **AGREGAR** impl NSUserDefaults+query |
| `DetectionTraceIngestion` (**nuevo**, F0) | **pull→batch** (replay) | tests/regresión | vía de ingesta wake-and-query |
| `DetectionSideStore` (**nuevo o portificación**, F0) | KV durable | SharedPrefs (hoy sin puerto) | NSUserDefaults |
| `ManualParkingDetection` | comando | service ✅ | `IosDetectionController` (reemplaza no-op) |
| `AppNotificationManager` | comandos | ✅ | ✅ (completar superficie) |
| `ParkingSyncScheduler`/`ReportSpotScheduler`/`ParkingEnrichmentScheduler` | comandos diferibles | WorkManager ✅ | cola persistida + BGTask (completar) |
| `TripTrail` / `DrivingRouteStore` / `RoadNetworkDataSource` | mixto | ✅ | P2 — `getOrNull` ya degrada; ruta dibujada llega después del MVP detección |
| `PermissionManager` / `OemBackgroundReliabilityManager` / `BluetoothScanner` | pull | ✅ | ✅ (F7/P5: poblar capacidades, no fingir Android) |

El orquestador (`IosDetectionController`) es **iosMain puro**, espejo funcional del
service Android: consume buses/wakes, aplica los evaluadores comunes, ejecuta side-effects
(sesión GPS, notificaciones, colas). Puede compartir con Android piezas de orquestación
pura si emergen (p. ej. un `DetectionIntakeReducer` común) — no forzarlo en F1.

---

## 8. Plan por fases

**F0 — Habilitadores en común (beneficia a Android aunque el port se retrase)**
1. Promover replay → `DetectionTraceIngestion` en commonMain (+ decidir `Kind.ACTIVITY`).
   Los tests actuales pasan a consumir el puerto. _Dep: ninguna._
2. `DeviceCapabilities` + fiabilidad/tiers platform-aware (P5). _Dep: ninguna._
3. Contrato único de eventos de geocerca (G2: bus como contrato, Android lo consume o
   deja de emitir). _Dep: ninguna._
4. `ActivityRecognitionManager.queryTransitions` (pull) + diseño de seals sobre
   query-based steps. _Dep: ninguna._
5. Portificar side-stores mínimos (`pending_arm`, seals, `exit_delivered`,
   `arrival_resolution`). _Dep: ninguna._
6. (Higiene bloqueante de la auditoría §8.1 que toque estos ficheros, de paso.)

**F1 — Orquestador iOS mínimo (camino feliz, app viva)**
7. `IosDetectionController`: armado MANUAL + sesión GPS trip-scoped + coordinator en vivo
   + confirmación → `ConfirmParkingUseCase` (pipeline común ya funciona) + notificaciones.
   _Dep: F0.2, F0.5._
8. Registro de región al confirmar + `didExitRegion` → `EvaluateGeofenceExitUseCase` →
   re-armado. Reconcile de regiones on-start. _Dep: F0.3._
9. Validación en device: ciclo completo park→exit→re-park con app en foreground/background
   reciente.

**F2 — Wake-and-query (el corazón)**
10. Reconstrucción del protocolo §4: side-records + queries motion/pedometer → batch →
    evaluadores. Cubre wake tardío, process death mid-trip y el caso sin sesión GPS.
    _Dep: F0.1, F0.4, F1._
11. Departure inline con escalera de retries + publicación de plaza + backfill guards.
    _Dep: F1.8._
12. Safety-net mesh: ENTER cure, SLC/visit wakes, app-start, BGAppRefresh tick.
    _Dep: F2.10._

**F3 — Permisos y producto**
13. `PermissionsScreen.ios` real: flujo §5, escalado Always contextual, estados
    Approximate/Motion-denegado. Tier card iOS (sin filas BT/batería). _Dep: F0.2._
14. Nudges/notificaciones completas (persist-FIRST, acciones, deep links). Dev Catalog:
    escenarios iOS (regla MOCKQA-001).

**F4 — Durabilidad de colas y enriquecimiento**
15. Cola de sync persistida + BGProcessingTask; split geocode-inline/route-diferido;
    ReportSpot con gate TTL. _Dep: F1._

**F5 — Validación de campo iOS (gate de confianza, §10)**

Fuera de alcance del port (P2): ruta dibujada/HMM en iOS, widget, MapKit nativo.

---

## 9. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Región de 100 m + wake tardío → ancla pobre en el wake | Pins degradados a prompt/zona más a menudo que en Android | Es la degradación DISEÑADA (zona acotada/nudge). Medir con telemetría F5 antes de tocar umbrales |
| El usuario niega Always (tasa alta esperable) | Tier limitado mayoritario | El nudge retroactivo al abrir la app (historial motion) conserva valor personal; escalado contextual bien hecho sube la tasa |
| BGTask no corre en días | Sync/enrich tardíos | Colas drenadas también en cada wake y app-open; BGTask es backstop, no camino |
| Review de Apple (background location) | Rechazo | §6: justificación de valor personal, demo funcional, sin keep-alives |
| Reconstrucción con densidad de datos baja (sin fixes intermedios) | FN de confirmación silenciosa | Aceptado por doctrina (prompt > fantasma); la sesión GPS viva cubre el caso rico |
| Low Power Mode / Background App Refresh off | Wakes reducidos | Detectar (`isLowPowerModeEnabled`, `backgroundRefreshStatus`) → issue en el report de fiabilidad (análogo honesto del callout OEM) |
| CMMotionActivity requiere device físico (simulador no genera) | QA | Field-test en iPhone real desde F1; fixtures sintéticos para CI |

## DECISIÓN (resolver el usuario, no asumidas)

1. **DECISIÓN: [momento del escalado Always]** — post-primer-park (recomendado) / toggle
   Ajustes / banner Home.
2. **DECISIÓN: [naming de tiers en iOS]** — "Asistido" único vs "Asistido +" con Always.
3. **DECISIÓN: [radio mínimo de región iOS]** — 100 m fijo actual vs calibrar en campo.
4. **DECISIÓN: [heurística de audio del coche]** — catalogar como experimento post-MVP
   ¿sí/no? (nunca como pilar del tier).
5. **DECISIÓN: [Kind.ACTIVITY en TraceEvent]** — extender el modelo de ingesta o
   alimentar la escalera AR por canal aparte (se decide con un fixture en F0.1).
6. **DECISIÓN: [alcance del nudge retroactivo sin Always]** — ¿preguntar al abrir la app
   "¿aparcaste a las X?" usando historial motion, o limitar a detección con Always?
7. Heredadas de la auditoría Android (§8.3): alarma exacta, toggles de notificación,
   allowBackup.

## 10. Criterios de validación (el "field-test Android" en iOS)

1. **Paridad del núcleo por construcción:** las 9 trazas + 12 tests de replay corren
   sobre el MISMO código que ejecuta iOS — cualquier fixture nuevo de campo iOS
   (pipeline Firestore→trace2fixture, que ya es común) protege ambas plataformas.
2. **Protocolo de device físico** (espejo del de DET-SOLID):
   - Aparcar y alejarse andando cruzando la región → NADA (ni pin ni prompt).
   - Salir conduciendo → plaza liberada publicada + siguiente park confirmado (sesión
     GPS viva) o prompt/zona (wake tardío) — nunca silencio con trayecto probado.
   - Repark corto, bus/taxi junto al coche, paseo dentro del radio → tabla S1–S12 de
     DET-SOLID, mismos resultados esperados.
   - Kill de app mid-trip (swipe) → el siguiente wake reconstruye: departure liberado y
     ask/zona en destino.
   - Reboot con sesión activa → el EXIT sigue despertando (región persistida por OS).
   - Sin Always → al abrir la app tras un trayecto: nudge retroactivo correcto.
3. **Telemetría comparable:** sesiones iOS en `diagnostics/` con los mismos eventos
   (SessionStarted/Decision/HONEST_CLOSE/DepartureVerdict) + stamp iOS
   (`hasAlways`, `lowPower`, `bgRefresh`); métrica clave nueva: **latencia de wake**
   (anchor→primer fix, ya definida en DET-ROUTE-ORIGIN).
4. **Umbral de salida de beta:** N trayectos de campo sin falso positivo silencioso
   (regla de oro); los falsos negativos se miden y se aceptan si van acompañados de nudge.
