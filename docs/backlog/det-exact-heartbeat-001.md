# DET-EXACT-HEARTBEAT-001 — Red de polling con exact alarm (~5 min) mientras hay sesión aparcada

**Estado:** ✅ IMPLEMENTADO 2026-07-30 en `feature/DET-EXACT-HEARTBEAT-001-exact-alarm-net`
(worktree `../Paparcar-exactnet`), pendiente commit + device/field-test (medir batería 12 h aparcado
+ deltas de estiramiento Doze en Redmi). AÚN MÁS SIMPLE que la spec: ni un call-site nuevo de
armado — el ciclo de vida se espeja en `ParkingSafetyNetWorker.doWork` junto al
`significantMotionMonitor.sync` existente (`ExactHeartbeatScheduler.sync(parkedAndIdle)` en cada
tick: arma/re-arma/desarma en UN sitio, self-healing tras process-kill), y el primer armado tras un
parking lo siembra la malla `enqueueCheckNow` que ya existía (detection-end / bt-park / app-start /
boot). Piezas: `ExactHeartbeatScheduler` (setExactAndAllowWhileIdle 5 min, gate
`canScheduleExactAlarms()`, fallback `setAndAllowWhileIdle`, delta programado→real persistido =
métrica de estiramiento Doze) + `ExactHeartbeatReceiver` (re-arma + `enqueueCheckNow(exact-alarm)`)
+ `SOURCE_EXACT_ALARM` (provenance en el verdict) + manifest (SCHEDULE_EXACT_ALARM + receiver).
DIFERIDO a field-test: pre-filtro lastKnown (pieza 3), degradar cadencia en SENTRY (pieza 4 — la
rama no tiene ServicePresence, es de DET-RESIDENT-FGS sin mergear).
**Pieza 2 (CTA special-access) DESCARTADA 2026-08-13 [IOS-F0-09]:** con targetSdk 36 el acceso
especial viene denegado por defecto → en campo la red corre en modo inexacto y así se acepta;
no se pedirá el permiso al usuario. "Exact" = techo de capacidad, no garantía.
Sin test unitario propio: cero lógica de decisión nueva (el scheduler es I/O AlarmManager puro);
prod suite + mock compile verdes.
**Origen:** Pieza 6/2bis del plan Driversnote (`project_det_driversnote_learnings_plan`).
Driversnote (config decompilada) corre `heartbeatInterval: 300s` por **AlarmManager exact alarm**
con `SCHEDULE_EXACT_ALARM`: cada 5 min un callback sin notificación puede pedir un fix y
compararlo contra el ancla → cualquier salida que AR no anunció se pilla con retraso acotado.
El user comprobó en campo que AR con proceso muerto NO rinde igual que con proceso vivo; esta es
la muleta de Driversnote para vivir sin FGS residente.

---

## El gap (confirmado en nuestro código, 2026-07-30)

Nuestra red de reconciliación es buena decidiendo pero lenta despertando:

- `detection/worker/ParkingSafetyNetWorker.kt` — **WorkManager periódico de 15 min**
  (`INTERVAL_MINUTES = 15L`, :631). Doze agrupa los workers en ventanas de mantenimiento: el
  intervalo real aparcado-en-Doze puede irse muy por encima de 15 min.
- **No existe NINGÚN AlarmManager en el proyecto** ni `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` en
  el manifest (verificado 30-07). Las exact alarms con `setExactAndAllowWhileIdle` disparan
  incluso en Doze (estiradas a ~9-15 min en Doze profundo, pero disparan) y además son una vía
  EXENTA para arrancar un FGS desde background en Android 12+.
- Los triggers de evento (AR / geocerca / SigMotion) dependen de Play Services vivo y entrega
  puntual — justo lo que falla en los FN de Doze/cached-kill de los field-tests.

Consecuencia: cuando AR no dispara y el proceso está muerto, la salida espera al worker de 15 min
(o más). Con DET-ROUTE-ORIGIN-001 el retraso no se vería en la ruta, pero el freed-spot sale tarde.

---

## La decisión (sistemas, no parches)

**El alarm NO introduce un segundo evaluador.** Es un trigger más puntual del camino que YA
existe: receiver de la alarm → `ParkingSafetyNetWorker.enqueueCheckNow` (expedited one-time) →
`EvaluateSafetyNetCheckUseCase` decide exactamente como hoy (presupuesto de pasos, conjunción
EXIT∧ENTER, física peatonal; nunca liberar por distancia sola). Cero lógica nueva de decisión;
solo cadencia. Despertar ≠ confirmar — doctrina intacta.

Ciclo: se ARMA al confirmar parking (y en boot con sesión activa), se RE-ARMA en cada disparo
(one-shot encadenado, patrón obligatorio de `setExactAndAllowWhileIdle`), se DESARMA al liberar la
sesión o apagar detección. **Solo corre mientras hay sesión aparcada** — cero coste el resto del
tiempo.

## Diseño por pieza

1. **`ExactHeartbeatScheduler` (androidMain, `detection/`).** Arma/desarma/re-arma
   `setExactAndAllowWhileIdle(RTC_WAKEUP)` con `INTERVAL_MS ≈ 5 min` (constante en companion) →
   `BroadcastReceiver` (manifest) que hace `enqueueCheckNow` y re-arma. Idempotente
   (`PendingIntent` único).
2. **Permiso y fallback.** `SCHEDULE_EXACT_ALARM` en manifest. Con targetSdk 33+ está DENEGADO
   por defecto: gate con `alarmManager.canScheduleExactAlarms()`. Sin permiso → fallback
   automático a `setWindow` inexacta (mejor que nada) + el worker de 15 min como hoy. Ofrecer la
   special access "Alarmas y recordatorios" SOLO desde la pantalla de salud de detección
   (tier automático / opt-in), con copy causa+consecuencia+remedio sin mecánica interna
   (`feedback_no_internals_in_user_copy`). NO pedirla en onboarding — footprint mínimo
   (`project_det_testing_minimum`) y decidir en field-test si el fallback inexacto ya basta.
3. **Coste batería.** El check ya empieza barato (`EvaluateSafetyNetCheck` pide UN fix). Añadir
   pre-filtro: mirar `lastKnownLocation` pasiva primero; si está fresca y pegada al ancla, saltar
   el fix activo. Presupuesto objetivo: irrelevante en 12 h aparcado (medir en validación).
4. **Interacción con SENTRY (DET-RESIDENT-FGS-001).** Con SENTRY vivo, SigMotion cubre la
   inmediatez y esta red es redundante en caliente → opcional degradar el intervalo (p.ej. 15 min)
   cuando `ServicePresence == Sentry`, y a 5 min cuando el proceso está DEAD. El intake serializado
   (DET-INTAKE-001) deduplica si ambos disparan.
5. **Residual honesto.** El force-stop OEM (deep-kill MIUI/ColorOS = stopped state) también
   cancela las alarms — esta red NO lo vence (nada lo vence salvo autostart/whitelist). Su terreno
   es Doze + cached-kill + Play Services estrangulado, que es donde WorkManager llega tarde.
6. **Telemetría.** Evento `exact_net_fired` con delta programado→real (mide el estiramiento de
   Doze) y, si el check despacha una salida, el `detectionPath` existente ya registra la
   provenance (`feedback_detection_trigger_provenance`).

## Riesgos

1. **Play policy de exact alarms**: hay que declarar el uso en la ficha. Justificación real:
   reconciliación puntual de un evento físico (salida del coche). Documentar en la declaración.
2. **Batería** si el pre-filtro falla y cada tick pide fix activo → telemetría de coste + medir
   12 h aparcado en validación.
3. **Permiso revocable por el user** (special access) → el gate + fallback lo hacen degradación
   suave, nunca rotura.

## Validación

- Unit: scheduler idempotente (armar dos veces = una alarm), desarme al liberar, fallback sin
  permiso.
- Device (Redmi, el más hostil): aparcado de noche en Doze → telemetría de deltas de disparo
  (¿5 min reales? ¿9-15?); comparar latencia salida→primer-check vs. histórico del worker 15 min.
- Batería: % en 12 h aparcado con la red armada (~nulo esperado).

## Ficheros previstos

- `detection/ExactHeartbeatScheduler.kt` + receiver (nuevos, androidMain).
- `AndroidManifest.xml` (`SCHEDULE_EXACT_ALARM` + receiver).
- Armado/desarmado: side-effects Android de confirmar/liberar parking (donde se registra/borra la
  geocerca) + `BootCompletedReceiver` + toggle detección OFF.
- `ParkingSafetyNetWorker.kt` (reuso de `enqueueCheckNow`; pre-filtro lastKnown si se decide ahí).
- Pantalla salud detección/Ajustes (CTA opcional special access) + strings 9 locales si hay copy.
- Tests + `docs/detection/PARKING-DETECTION.md` (changelog, misma tarea).

## Relacionados

- `project_det_driversnote_learnings_plan` (pieza 6/2bis) · `reference_driversnote_detection_stack`.
- Sinergia con DET-ROUTE-ORIGIN-001: la red acota el retraso a ~5 min; el origen retrodatado hace
  que ese retraso no se vea. Juntos = "viaje completo sin BT".
- Complementa (no sustituye) DET-RESIDENT-FGS-001: SENTRY = inmediatez con proceso vivo; esta red
  = techo de latencia cuando el proceso está muerto.
