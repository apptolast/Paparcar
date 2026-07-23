# DET-FGS-REAPER-001 — Segar la FGS fantasma + teardown lean del trigger falso

> **Estado**: rama `feature/DET-FGS-REAPER-001`. **F1 reaper ✅ commiteado 2026-07-22** (`32550292`,
> build + 4 tests verdes; ⏳ pendiente device + field-test Oppo). **F2 teardown-lean ✅ AUDITADA
> 2026-07-23 → cerrada sin cambios** (2 puntos ya cubiertos, 1 —la ventana de 4 min— requiere datos
> antes de tocar; ver abajo). Origen: field-test 2026-07-21 (Oppo/ColorOS). No toca la lógica de
> decisión del coordinator.

## Problema (evidencia de campo, Oppo 2026-07-21)

Tras aparcar en Calle Aurora (~19:24, honest-close approximate pin), el usuario salió **a pie** hacia el
centro. A las 21:53 un EXIT de la valla de Aurora armó una sesión que **abortó `false_enter` en 12 s**
(1 fix, 16 pasos, 2 km/h — ibas andando). Correcto: era un trigger falso. Pero:

- La **notificación FGS de detección** (`DETECTION_NOTIFICATION_ID` = 1001) se quedó **colgada ~2 h**
  (21:53 → ~01:xx) mostrando "detección activa" sin que hubiera nada detrás.
- **Cero telemetría** en esa franja (ni un fix, ni una sesión). El silencio total ES la prueba: el
  proceso estaba **congelado por ColorOS** (freeze, no kill). Un proceso congelado no puede loguear ni
  ejecutar su propio teardown.

El teardown del servicio ES correcto para un proceso vivo (`finally` → `DetectionEnded` → `stopIfIdle`
→ `stopForegroundAndSelf`; + red en `onDestroy`). El comentario del `finally`
(`CoordinatorDetectionService`) ya lo dice: *"ONLY a process death skips this finally"*. Cuando el SO
congela el proceso justo tras promover la FGS, la notificación queda pegada a un cadáver y Android no la
recoge (ColorOS congela en vez de matar). El único auto-cura hoy es el sticky-restart null-intent
(`DET-B-02`) — pero **solo dispara si el SO REINICIA el servicio**; si te congela sin reiniciar, la
fantasma persiste hasta que despiertas.

Además: una FGS-LOCATION que el SO ve colgada **penaliza la app** (sube prioridad de kill / restricción
de batería), realimentando el propio OEM-kill.

## Doctrina

*Un pending muerto ⇒ el proceso del servicio que poseía su FGS está muerto ⇒ su notificación es una
fantasma y hay que segarla.* La segada es **idempotente** (dismiss de un id ausente es no-op) y
**jamás toca una sesión viva**: sólo descarta una notificación huérfana, nunca mata un proceso.

**Invariante inviolable**: el reaper NO se ejecuta si hay una sesión real viva. Garantizado por dos
candados independientes (defensa en profundidad), no por uno solo.

## F1 — Reaper de la FGS fantasma (recuperación) · este commit

Extensión mínima de `ParkingSafetyNetWorker.checkStalePendingDetections()` (ya escanea pendings con
heartbeat muerto cada 15 min y los limpia). Al detectar stale pendings, **también sega la fantasma**:

```
dismiss(DETECTION_NOTIFICATION_ID)   // solo cuando se cumplen AMBOS candados
```

**Los dos candados (por qué nunca golpea una sesión viva):**

1. **`!detectionRuntime.isRunning.value`.** `isRunning` es la autoridad in-memory que el servicio pone
   `true` al armar y `false` en el `finally`. Sesión viva ⇒ `true` ⇒ reaper NO actúa. Un pending fresco
   nunca es stale, pero un pending viejo-stale NO puede colateralizar la FGS de una sesión NUEVA viva —
   este candado lo impide.
2. **`source == SOURCE_PERIODIC`.** El worker postea su PROPIA FGS `1001` (`getForegroundInfo`) sólo en
   runs **expedited** (`enqueueCheckNow` → `setExpedited`). El tick **periódico** corre en background y
   nunca postea 1001, así que ahí un 1001 presente es SIEMPRE la fantasma. Los runs expedited ya
   auto-limpian la fantasma como efecto colateral (WorkManager postea 1001 al promover y lo retira al
   terminar), así que NO necesitan —ni deben— llamar al reaper. Este candado elimina la colisión con la
   notificación propia del worker.

**"Earn the ask" (respeta OEM-KILL-001):** el reaper es **silencioso** cuando no se perdió nada
(`sawDriving=false`, p.ej. el trigger falso andando) — sega la fantasma sin nudge. El nudge
"¿dónde aparcaste?" sólo se dispara si el pending era un viaje real con conducción
(`shouldNudgeForStalePending`), como ya hoy. La segada de la fantasma es independiente del nudge.

**Latencia:** ≤ 15 min (periódico) en el peor caso vs las ~2 h observadas; los runs expedited
(sig-motion al moverte) la limpian antes vía el ciclo de vida propio de WorkManager.

## F2 — Teardown lean del trigger falso (prevención) · AUDITADA 2026-07-23

**Resultado: F1 era el arreglo real. De los 3 puntos, 2 ya están cubiertos y 1 requiere datos.
Ningún cambio de código a ciegas — se cierra sin tocar, salvo que datos de campo lo justifiquen.**

1. **`stopForegroundAndSelf` vetado por `stopSelfResult` — NO ES BUG.** `stopForegroundAndSelf(startId)`
   (`ForegroundServiceController:75`) retira la notificación **solo si el stop se acepta** (`startId` es el
   más reciente); un veto significa que un comando más nuevo ya re-promovió la FGS y **su** epílogo es el
   dueño del teardown (BUG-FGS-100 / DET-INTAKE-001). Correcto por diseño. La fantasma de campo NO fue un
   veto — fue **freeze de proceso** (territorio F1, ya resuelto).

2. **`observeAdaptiveLocation` ya se suelta antes del I/O de honest-close.** Es el Flow que consume el
   coordinator; al retornar el coordinator el `collect` termina y la suscripción de ubicación se corta
   — **antes** de `maybeRunHonestClose` (`CoordinatorDetectionService:840-852`). Punto ya resuelto.

3. **La FGS se retiene poco durante honest-close, y en `KeepSilent` (el trigger falso) honest-close NO
   hace I/O pesada.** `maybeRunHonestClose` (`:722`) en el camino silencioso solo hace lecturas Room
   (vehículo activo + sesión activa + step baseline) y retorna null **sin** `confirmParking`. Soltar la
   FGS ANTES de honest-close exigiría `stopSelf` mientras honest-close corre en la coroutine → `onDestroy`
   cancelaría el job (el `withContext(NonCancellable)` existe justo para evitar que el servicio muera a
   mitad del release). **Riesgo > beneficio → se deja como está.**

4. **`maxNoMovementMs` = 4 min (`ParkingDetectionConfig:200`) es la palanca real, pero requiere
   medición.** El misfire **andando** ya se corta rápido (`falseEnterAbortSteps`,
   `CoordinatorParkingDetector:682` — 12 s en campo 21-jul). Los 4 min solo muerden en el misfire
   **estacionario** (AR ENTER sentado en el coche parado, 0 pasos, `:694`). Acortarlo cambia FGS-más-corta
   por riesgo de **falso negativo** en salidas reales con GPS lento de arranque. La nota del propio
   `PARKING-DETECTION.md` (~L931) ya lo reconoce. **No tocar a ciegas: medir en el replay harness + datos
   de campo primero.** Prioridad baja — el abort de 4 min self-termina correcto con proceso vivo, y F1 ya
   cubre el peor caso (el cuelgue de horas).

## Validación

- Test unitario del reaper: (a) stale pending + `!isRunning` + periódico → `dismiss(1001)` llamado;
  (b) `isRunning=true` → NUNCA se llama a `dismiss(1001)` (invariante inviolable); (c) source expedited
  → no reap; (d) sin stale pendings → no reap.
- Field-test Oppo: forzar un `false_enter` andando, congelar el proceso (o esperar el freeze de ColorOS),
  verificar que a los ≤ 15 min la notificación fantasma desaparece sin nudge.

## Criterio de éxito

Una FGS de detección nunca vuelve a quedar colgada horas sobre un proceso muerto: o el sticky-restart la
strippea al reiniciar, o el reaper periódico la siega en ≤ 15 min — y **jamás** se toca la notificación
de una sesión viva.
