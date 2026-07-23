# DET-FGS-REAPER-001 — Segar la FGS fantasma + teardown lean del trigger falso

> **Estado**: 🔴 EN CURSO 2026-07-22 (rama `feature/DET-FGS-REAPER-001`). Origen: field-test 2026-07-21
> (Oppo/ColorOS). Dos frentes complementarios: **F1 reaper** (recuperación, este commit) + **F2 teardown
> lean** (prevención, auditoría). No toca la lógica de decisión del coordinator.

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

## F2 — Teardown lean del trigger falso (prevención) · AUDITORÍA (no en este commit)

Reducir la ventana/huella de "FGS-LOCATION colgada" que el SO penaliza:

- En abort silencioso (`KeepSilent`), soltar FGS + cortar `observeAdaptiveLocation` **antes** del I/O de
  `maybeRunHonestClose`, no después.
- Cuestionar la ventana `no_movement` = 4 min (4 min de FGS-LOCATION sobre un trigger falso es lo que
  más "mal actor" nos hace ver). ¿Se acorta sin arriesgar FN? Medir en el replay harness, no a ciegas.
- Verificar que `stopForegroundAndSelf` en `false_enter` retira la notificación de verdad (no vetado por
  `stopSelfResult`).

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
