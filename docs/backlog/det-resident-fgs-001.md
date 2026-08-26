# DET-RESIDENT-FGS-001 — FGS residente en modo centinela (GPS apagado en reposo) para no perder salidas por re-arranque

**Estado:** ✅ F1 + F2 + F3 COMPLETOS en `feature/DET-RESIDENT-FGS-001` (rebasada 2026-08-05 sobre
master `c415e36b`, que ya incluye TRIP-WITNESS/BACKFILL-TAINT/EXACT-HEARTBEAT/JAM-WINDOW/
ROUTE-ORIGIN): F1 + SigMotion directo, F2 telemetría (2026-08-04), F3 gating producto (2026-08-06,
ver Fases abajo). Suite prod completa verde + `assembleMockDebug` verde + `SentryLifecycleDecisionTest`
(9 casos). **✅ EN MASTER** (mergeada FF 2026-08-06 tras completar F3, go del user; fases F1/F2/F3
conservadas como commits; la rama ya no existe). Campo cubierto por la validación hasta `1a4128d5`
(23-08-2026) — el sentry lleva semanas conduciéndose en ambos OEMs.
**Field-tests 28/29-07 (Rota) y 29/30-07 (El Puerto): 0 FN en ambos OEMs, Oppo revive tras muerte
por batería, todo trigger disparó. Field 30-07 tarde (Redmi): MIUI mató el sentry tras la sesión
21:19 y NADA lo revivió (deep-kill ≈ force-stop; palanca = autostart, no código) — exactamente el
evento que la telemetría F2 hace visible como `sentry killed`.** F3 ✅ 2026-08-06 (ver Fases);
pendiente acotar el GPS de sesiones desatendidas sin conducción (coste batería en despertares
falsos). Q1/Q2 decididas (ver abajo).
**Origen:** Pieza 1 del plan derivado del análisis decompilado de Driversnote
(`project_det_driversnote_learnings_plan` en memoria; competidor = plugin Transistor
`react-native-background-geolocation`). Ataca la subclase crónica de FN "OEM/Doze mató el
proceso y el evento no pudo re-arrancarlo".

---

## El gap (confirmado en nuestro código, 2026-07-28)

Hoy `CoordinatorDetectionService` **ata "detección activa" a "proceso vivo"**:

- Despierta por evento (AR `IN_VEHICLE ENTER` decision-lane / `GEOFENCE_EXIT` / tap manual),
  ejecuta el `detectionJob`, y al terminar lo **mata** (`stopIfIdle()` → `stopSelfResult(startId)`
  en `ForegroundServiceController`). Entre parkings: **proceso muerto, sin FGS, sin notificación**.
- Mientras está aparcado, la vigilancia de salida recae en tres señales que **no tienen proceso
  propio vivo**: geocerca EXIT y AR (ambas dentro de Play Services, que un OEM puede matar de
  hambre) + `SignificantMotionMonitor` (sensor one-shot que, por su propia doc, **NO sobrevive a
  la muerte del proceso** — no hay PendingIntent para sensores). El único resucitador es
  `ParkingSafetyNetWorker` (WorkManager 15 min).

Consecuencia: cuando el SO mata el proceso por cached/Doze y luego el evento de salida llega, en
Android 12+ **no se puede arrancar un FGS desde background** (`ForegroundServiceStartNotAllowedException`),
o el evento simplemente no se re-entrega a tiempo. Es un FN recurrente en los field-tests (Redmi/
Oppo). El `SignificantMotionMonitor` ya documenta esta limitación como "capa de inmediatez" que
depende del worker de 15 min para resucitar.

**Driversnote no tiene este gap** porque su FGS es **eterno**: siempre hay un proceso vivo donde
el trigger de salida aterriza al instante. Pueden permitírselo porque **apagan el GPS en reposo**
(la Motion API + una geocerca lo despiertan), así que un FGS residente cuesta ~cero batería. Ver
`reference_driversnote_detection_stack`.

---

## La decisión

**Desacoplar "proceso residente" de "detección activa".** Cuando hay una sesión aparcada, en vez
de matar el FGS y depender del re-arranque del SO, el service permanece **residente en modo
CENTINELA (sentry)**: vivo, con **GPS apagado**, vigilando solo con el sensor de movimiento +
AR + geocerca. Al detectar movimiento, transiciona a **modo ACTIVO** (enciende GPS, corre el
`detectionJob`). Al confirmar/abortar, **vuelve a centinela** (no muere).

Sinergia clave: con el proceso residente, el listener de `SignificantMotionMonitor` **vive todo el
tiempo** → deja de depender del worker de 15 min para resucitar, y puede arrancar el job en el acto
(elimina la latencia del WorkManager). El sensor pasa de "capa de inmediatez frágil" a "trigger
fiable en proceso".

**Lo que NO cambia (crítico):** la doctrina de confirmación es intocable. `EvaluateParkingDecisionUseCase`,
el fallo asimétrico, la exigencia de conducción medida, el anclaje — todo idéntico. Esto es
**puramente ciclo de vida del proceso + gating de GPS**. La DECISIÓN de plantar un pin no se toca.
No se planta ni una plaza más ni menos: solo se captura la salida de forma más fiable en los
móviles donde hoy se pierde por re-arranque.

---

## Máquina de estados (nueva)

```
        ┌──────────────────────────────────────────────────────────────┐
        │  (sin sesión aparcada)                                        │
        │   DEAD  ── evento arm (AR ENTER / GEOFENCE_EXIT / manual) ──▶ ACTIVE
        └──────────────────────────────────────────────────────────────┘

   ACTIVE (GPS on, detectionJob corriendo, notif "detección en curso")
     │
     │ confirm / abort / timeout   ── ¿hay sesión aparcada resultante? ──┐
     │                                                                    │
     ├── NO sesión aparcada  ────────────────────────────▶ DEAD (como hoy)
     │
     └── SÍ sesión aparcada + tier lo permite ───────────▶ SENTRY
                                                              │
   SENTRY (GPS OFF, detectionJob=null, notif centinela de bajo perfil,        │
           SigMotion listener + AR decision-lane + geocerca EXIT armados)     │
     │                                                                        │
     │ SigMotion trigger / AR IN_VEHICLE / GEOFENCE_EXIT (en proceso vivo) ──▶ ACTIVE
     │                                                                        │
     │ usuario borra sesión / detección OFF / tier baja ─────────────────────▶ DEAD
     └────────────────────────────────────────────────────────────────────────┘
```

- **DEAD** = comportamiento actual entre parkings (tier no-automático, o sin sesión aparcada).
- **ACTIVE** = comportamiento actual del `detectionJob` (GPS adaptativo 5s/30s, burst 3 min).
- **SENTRY** = nuevo. GPS apagado, proceso vivo, coste ~batería del sensor-hub (nulo).

---

## Diseño concreto por pieza

### 1. Estado residente en el runtime
`DetectionRuntimeState` / `MutableDetectionRuntimeState` hoy expone `isRunning: Boolean` (job
activo sí/no). Añadir un tercer estado explícito para no confundir "job corriendo" con "proceso
centinela":
```
enum class ServicePresence { Dead, Sentry, Active }
```
El service publica la transición; la UI y `ObserveDetectionReadinessUseCase` ya distinguen
Ready/Monitoring — `Sentry` mapea a "armado y vigilante" sin puck de conducción.

### 2. Gating de GPS (el que hace asumible la residencia)
`ObserveAdaptiveLocationUseCase` no se toca en su lógica 5s/30s; simplemente **no se suscribe** en
SENTRY. El service arranca el flujo de localización solo en ACTIVE. En SENTRY: cero
`requestLocationUpdates`. (Hoy el GPS es continuo durante toda la sesión — este es el cambio que
evita que un FGS residente drene batería.)

### 3. Ciclo de vida del service (`CoordinatorDetectionService` + `ForegroundServiceController`)
- Tras `detectionJob` terminal, `stopIfIdle()` decide hoy `stopSelfResult()`. Nueva bifurcación:
  si (tier automático **y** existe sesión aparcada) → **no** matar; degradar a SENTRY
  (`enterSentry()`): re-promover el FGS con la notificación centinela, soltar el GPS, armar
  `SignificantMotionMonitor` in-process. Si no → `stopSelfResult()` como hoy.
- `SignificantMotionMonitor.onTrigger` en SENTRY: hoy hace `ParkingSafetyNetWorker.enqueueCheckNow`
  (fast-lane legal desde un proceso que podría estar muerto). Con proceso vivo, además puede
  **arrancar el job directamente** (sin latencia de WorkManager). Se mantiene el enqueue como
  respaldo idempotente.
- `onDestroy`: si el SO nos mata en SENTRY (force-stop OEM), el re-enqueue de `ParkingSafetyNetWorker`
  + `BootCompletedReceiver` siguen siendo la capa de resurrección (sin cambios). SENTRY reduce la
  frecuencia con que llegamos aquí, no la elimina.

### 4. Tipo de FGS en SENTRY (cuestión de diseño a decidir — ver Riesgos)
El service está promovido con `FOREGROUND_SERVICE_TYPE_LOCATION`. Con el GPS realmente apagado
durante horas en SENTRY, mantener el tipo `location` es lo más simple (el warning es informativo)
pero cuestionable ante políticas de Play. Alternativas: re-promover a `specialUse`/`shortService`
al entrar en SENTRY y volver a `location` al pasar a ACTIVE. **Decidir en implementación.**

### 5. Notificación centinela
Nueva notificación de **bajo perfil** (silenciosa, prioridad mínima), copy sin mecánica interna
(regla `feedback_no_internals_in_user_copy`): causa+consecuencia+remedio en llano, p.ej. "Vigilando
tu plaza para avisar cuando salgas. Puedes desactivarlo en Ajustes." Strings en los 9 locales.

### 6. Gating por tier + setting
Solo en tier "automático" (DET-TIERS-001) **o** un setting explícito opt-in, porque implica
notificación permanente. En asistido/manual: comportamiento actual (DEAD entre parkings). Reflejar
en Ajustes + Dev Catalog (galería/escenarios mock, regla `feedback_keep_dev_catalog_in_sync`).

---

## Riesgos y mitigaciones

1. **Force-stop OEM (MIUI/ColorOS) mata también el FGS residente.** Residual honesto: SENTRY no
   vence el force-stop explícito (eso necesita autostart/whitelist, otro eje que hoy NO pedimos por
   doctrina de footprint mínimo, `project_det_testing_minimum`). SENTRY convierte las FN de tipo
   "muerto como cached + no puede re-arrancar" en éxitos; deja intactas las de force-stop. La red de
   worker 15 min + boot receiver se mantienen para esas.
2. **Notificación permanente = regresión de UX** para una app que quiere ser ambiente. Mitigación:
   gating por tier/setting (§6) + notificación silenciosa de perfil mínimo.
3. **Políticas de Play sobre FGS location con GPS off** (§4) — decidir tipo de servicio en SENTRY.
4. **Fuga de batería si el gating de GPS falla** (§2). Test: verificar cero `requestLocationUpdates`
   en SENTRY; telemetría de tiempo-en-SENTRY con GPS confirmado apagado.

---

## Validación (experimento A/B, un móvil)

- Redmi (el más hostil a background) con tier automático + SENTRY ON vs. comportamiento actual.
- Métrica: tasa de FN por OEM-kill/Doze (salida no capturada con datos presentes) sobre N viajes,
  comparada con el histórico wake-and-kill. Objetivo: reducir la subclase "muerto + no re-arranca".
- Batería: medir % consumido en 12 h aparcado en SENTRY (debe ser ~nulo si el gating de GPS funciona).

## Telemetría — ✅ implementada en F2 (2026-08-04)
- Evento `DetectionEvent.Sentry` (wire `SENTRY`) con subevento `entered` / `woke` (señal = trigger
  de armado, cualquier canal) / `killed` (gap del heartbeat + duración de la residencia). El kill se
  detecta por el sello durable de `SentryResidenceStore` sobreviviendo al proceso, con el gap medido
  contra `KEY_LAST_ALIVE_AT` de `ParkingSafetyNetWorker` (leído ANTES del re-stamp del tick).
- `detectionPath` sin cambios (la confirmación no se toca); esto es lifecycle, no provenance de pin.

## Cuestiones abiertas — RESUELTAS 2026-07-28
- **Q1 · Tipo de FGS en SENTRY** → mantener `FOREGROUND_SERVICE_TYPE_LOCATION`, sin conmutar.
  `shortService` (tope ~3 min) inútil; `specialUse` exige justificación de Play y es menos honesto
  (el propósito del servicio ES localización); el tipo declara capacidad, no uso constante.
- **Q2 · Trigger del SigMotion** → directo in-process (SENTRY→ACTIVE sin latencia de WorkManager) +
  `enqueueCheckNow` de respaldo. El intake serializado (DET-INTAKE-001) deduplica. En F1 se
  implementó el arming (`significantMotionMonitor.sync(true)` en `enterSentry`); el callback directo
  a job es el pequeño resto opcional de F1.

## Fases (para revisar incremental, no big-bang)
- **F1** ✅ COMPLETO (flag OFF) — `ServicePresence{Dead,Sentry,Active}` en el runtime; decisión pura
  `resolvePostDetectionLifecycle` (+ test); `resolveIdleEpilogue`/`enterSentry` en el service
  bifurcando el teardown normal (DetectionEnded + epílogo de processIntent); GPS ya off en SENTRY
  (el job terminó); SigMotion re-armado + safety-net seed (lo que hacía onDestroy, pero el proceso
  vive). `stopIfIdle` se conserva para paths de error (nunca SENTRY en error).
  **Callback directo SigMotion:** `SignificantMotionMonitor` inyecta `DetectionRuntimeState`; si
  `presence == Sentry` (solo con flag ON) hace `startService(ACTION_SENTRY_WAKE)` al proceso vivo
  (re-entrega legal, sin latencia WorkManager) con fallback al worker si falla; si no, worker como
  hoy. `handleSentryWake` arma con `DetectionTrigger.SIGNIFICANT_MOTION` + `ArmEvidence.Unverified`
  (guardas anti-walking activos; solo conducción medida confirma); guarda job-activo y
  stand-down si no hay sesión aparcada.
- **F2** ✅ COMPLETO (2026-08-04) — telemetría de residencia + detección de kill. Nuevo
  `DetectionEvent.Sentry` (wire `SENTRY`, sessionId = geofenceId vigilado, convención fuera-de-sesión;
  columnas DTO reutilizadas: señal→`source`, tiempo-en-SENTRY→`sessionAgeMs`): `entered` (razón del
  epílogo como señal), `woke` (trigger de armado como señal — el punto único en
  `startParkingDetection` ve TODOS los canales de despertar: SigMotion directo, GEOFENCE_EXIT, AR,
  manual), `killed` (+ `gapMs` ventana a oscuras + `residencyMs`). **Sello de residencia durable**
  (`SentryResidenceStore`, mismo prefs que el safety-net, slot único
  `geofenceId|enteredAt|enteredElapsed`): se estampa en `enterSentry`, se limpia en toda salida
  DELIBERADA (wake→ACTIVE, teardown idle/error) → un sello que sobrevive al proceso = prueba de que
  el OS mató al vigilante. Veredicto puro compartido `resolveSentryKillVerdict` (commonMain, 5 tests)
  en DOS carriles: tick periódico del worker (`detectSentryKill` ANTES del re-stamp del heartbeat →
  `gapMs` real desde `KEY_LAST_ALIVE_AT`) y el propio arm del service (trigger que revive un proceso
  muerto con sello puesto = kill presenciado en vivo). Reboot explica el sello inocentemente (clear
  silencioso, misma regla que `BackgroundKillSuspected`). La resolución del tipo de FGS (§4) quedó
  cerrada en Q1: mantener `LOCATION`. Telemetría silenciosa — regla "earn the ask" intacta.
- **F3** ✅ COMPLETO (2026-08-06) — gating de producto, con una decisión del user que SIMPLIFICA la
  spec original (§6): **el centinela NO tiene interruptor propio** — el toggle de auto-detección de
  Ajustes lo gobierna ("una idea, un interruptor"; un 2º toggle expondría mecánica interna y crearía
  el estado absurdo detección-sí-centinela-no). Y **sin gating por tier**, invirtiendo la propuesta
  original: los tiers ASISTIDOS (sin receiver BT que reviva un proceso muerto) son justo donde la
  residencia salva salidas; su coste es una notificación silenciosa. Implementación: (a)
  `resolvePostDetectionLifecycle(autoDetectEnabled, hasParkedSession)` — el const `SENTRY_ENABLED`
  eliminado, `resolveIdleEpilogue` lee `AppPreferences.autoDetectParking`; (b)
  `watchSentryPreconditions()` en SENTRY observa toggle + sesiones activas y ante cualquier caída
  se auto-envía el STOP serializado (mismo intake/epílogo → teardown deliberado, sello limpiado) —
  cubre "apagar detección en Ajustes" y "liberar la plaza desde Home" con el FGS residente; (c)
  notificación centinela real: `buildSentryNotification()` en canal propio `sentry_channel`
  (IMPORTANCE_MIN, silenciosa, sin badge), copy en llano causa+consecuencia+remedio en 9 locales
  (`notif_sentry_title/text`, `channel_sentry_name/desc`), swap in-place al entrar en SENTRY y
  vuelta a la de detección activa en el siguiente promote; (d) Dev Catalog: sin cambios — no hay
  pantalla/estado/routing nuevo (solo notificación de sistema + toggle existente).
  Tarea aparte creada: `det-stop-button-001.md` (botón de usuario para PARAR una detección en
  curso — pendiente de definir; no confundir con este gating).
- **F4** — (FUERA de esta rama) Pieza 2: ancla estacionaria pasiva-continua encima de SENTRY.

## Ficheros previstos
- `detection/service/CoordinatorDetectionService.kt` (bifurcación `stopIfIdle`→`enterSentry`,
  transición SENTRY→ACTIVE, no suscribir GPS en SENTRY).
- `detection/service/ForegroundServiceController.kt` (re-promoción centinela / tipo de FGS).
- `detection/SignificantMotionMonitor.kt` (trigger in-process además del enqueue).
- `domain/detection/DetectionRuntimeState.kt` (`ServicePresence`).
- `domain/notification/*` + `composeResources/values*/strings.xml` (notif centinela, 9 locales).
- Ajustes + `StateGalleryScreen.kt`/`MockScenario` (gating + Dev Catalog).
- `CoordinatorDetectionServiceTest.kt` (confirmar en tier automático → SENTRY, no DEAD; SigMotion
  en SENTRY → ACTIVE sin WorkManager).
- `docs/detection/PARKING-DETECTION.md` (changelog, misma tarea — regla
  `feedback_document_parking_detection_changes`).

## Relacionados
- Plan raíz: `project_det_driversnote_learnings_plan` (memoria). Competidor: `reference_driversnote_detection_stack`.
- Depende conceptualmente de: DET-TIERS-001 (gating por tier), DET-SIGMOTION-001 (el sensor que se
  vuelve fiable), DET-SAFETY-NET-001 (resurrección residual para force-stop).
- Habilita: Pieza 2 (ancla estacionaria pasiva-continua) — F4, rama aparte.
- Doctrina que respeta: fallo asimétrico + conducción medida (intactos); footprint mínimo
  (`project_det_testing_minimum`) — por eso el gating por tier y sin pedir exención de batería.
```
