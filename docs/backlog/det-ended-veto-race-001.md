# DET-ENDED-VETO-RACE-001 — El teardown del FGS se vetaba a sí mismo (zombi con proceso vivo)

> **Estado**: ✅ Done · en master como `bfa3648a` — *"send DetectionEnded on job completion, not from
> its finally"*. La rama `bugfix/DET-ENDED-VETO-RACE-001` (que iba apilada sobre
> `feature/DET-FGS-REAPER-001`) ya no existe.
> *(Corregido el 2026-08-30 por [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001]: la cabecera solo nombraba
> la rama, sin decir que estuviera cerrado.)*
> Fix implementado 2026-07-23 y **✅ VALIDADO en device el mismo día** (Redmi, repro natural:
> relaunch lejos del coche → abort 4 min → teardown en 25 ms, `stopSelfResult(1)=true` +
> `onDestroy`; antes 2/2 zombis, después 1/1 limpio). ⏳ install pendiente en el Oppo (USB caído).
> Origen: field 2026-07-23 — FGS colgada 1h24m en el Oppo Y reproducida a voluntad en el Redmi.

## Síntoma de campo

Tras un abort silencioso (`aborted_no_movement` a las 14:11, Oppo), la notificación FGS de detección
(id 1001) quedó pegada **1h24m+ con el proceso VIVO** (safety-net corriendo, heartbeat fresco). No es
el fantasma post-freeze de DET-FGS-REAPER-001: aquí el pending se limpió correctamente y el proceso
nunca murió — el reaper, por diseño (candado `hasStalePending`), no aplica.

Reproducido en el Redmi con logcat instrumentado (15:45:50):

```
✓ coordinator returned NORMALLY
⑊ honest close: aborted_no_movement stayed silent
■ finally → DetectionEnded(startId=1) → intake
← y NUNCA aparece "stopIfIdle(detection-ended) → stopSelfResult(…)"
```

La línea de `stopIfIdle` se loguea DESPUÉS de su guard `if (detectionJob?.isActive == true) return`.
Su ausencia prueba que el guard retornó.

## Causa raíz

El `finally` del detection job enviaba `Command.DetectionEnded` al intake **desde dentro del propio
job**. Con `lifecycleScope` (dispatcher `Main.immediate`) y el consumer suspendido en `receive` sobre
el mismo hilo, `trySend` reanuda al consumer **en línea, síncronamente dentro del `trySend`**
(resume unconfined de kotlinx.coroutines cuando `isDispatchNeeded == false`). Resultado:

1. `stopIfIdle("detection-ended")` corre mientras el job sigue ejecutando su `finally`;
2. un job en su `finally` está en estado *Completing* → `isActive == true` → el guard retorna;
3. el intake es "un comando, una decisión" → **nadie reintenta jamás** → FGS zombi indefinida.

Latente desde DET-INTAKE-001. Invisible en uso activo porque **cualquier intent posterior**
(tick AR, EXIT, abrir la app) trae un startId nuevo cuyo epílogo sí para el servicio — solo se
manifiesta cuando el móvil queda quieto tras un abort y no llega nada más (p. ej. en casa). No es
OEM: reproducido en ColorOS y MIUI el mismo día.

Disparador típico: cada (re)launch de la app lejos del coche re-registra la valla del pin activo y
Play Services entrega un EXIT inmediato (d ≫ radio) → sesión GPS de 4 min condenada
(`aborted_no_movement`, coste aceptado por [DET-RIDE-PROOF-001]) → con este bug, zombi cada vez.

## Fix (sistémico, una pieza)

Mover el envío de `DetectionEnded` fuera del `finally` a `detectionJob.invokeOnCompletion`
(registrado en `startParkingDetection`):

- `invokeOnCompletion` se invoca solo con el job en estado **terminal** → cuando el consumer corre
  `stopIfIdle` — incluso reanudado en línea dentro del `trySend` — `isActive` ya es `false` y el
  stop procede.
- El guard de identidad `detectionJob === startedJob` conserva la regla de supersede
  ([DETECT-SERVICE-RACE-001]): el callback de un job reemplazado no para el servicio que usa su
  reemplazo.
- Un intent más nuevo entregado tras el envío sigue vetando vía `stopSelfResult` (mismatch de
  startId), exactamente como antes. `setRunning(false)`, `heartbeat.cancel()` y la limpieza del
  pending se quedan en el `finally` (estado por-job, debe voltear inmediatamente).

Sin cambios en commonMain ni en la lógica de decisión. Cero strings.

## Validación

- Suite unitaria verde (sin tests del service Android; la pieza pura no cambió).
- **Device (repro natural)**: instalar, lanzar la app lejos del coche → EXIT inmediato → abort a los
  ~4 min → la notificación 1001 debe desaparecer **en segundos** tras
  `■ job complete → DetectionEnded` + `stopIfIdle(detection-ended) → stopSelfResult(N)=true` en
  logcat. Antes del fix: 2/2 zombis; criterio de éxito: 2/2 teardowns limpios.
- Invariante intocado: sesión viva (job activo) sigue bloqueando el stop vía el guard de
  `stopIfIdle`; el veto por startId sigue protegiendo comandos en vuelo.
