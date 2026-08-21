# DET-HEARTBEAT-LANE-REPAIR-001 · Intentar que el heartbeat exacto vuelva a entregarse en ColorOS

**Estado:** 🔵 Abierto, sin código · **bloqueado hasta tener medición** ·
depende de `DET-HEARTBEAT-MISS-IS-EVIDENCE-001`

## Problema

En el Oppo CPH2371 (ColorOS, Android 13) el heartbeat exacto de 5 min **se dispara pero su broadcast
nunca llega a la app**. Evidencia recogida el 22-08-2026 a las 01:43, con el problema activo:

- `next_at` = 01:36:41 → la alarma **estaba armada** y su momento pasó 7 min antes sin re-armado.
- `dumpsys alarm`: 82 wakeups para `.detection.receiver.ExactHeartbeatReceiver` y varios registros
  **in-flight acumulados** (`nesting` creciente, `aggregateTime` ≈ 5,2 h) → el sistema despachó los
  broadcasts y **nunca completaron**.
- Proceso vivo con FGS corriendo · standby bucket **EXEMPTED** · `SCHEDULE_EXACT_ALARM` concedido ·
  batería sin restricciones.
- El Redmi al lado, misma app y misma versión: 34 heartbeats ese mismo día.

O sea: no es Doze, no es el bucket, no es el permiso, no es que el proceso esté muerto. Es la
entrega del broadcast.

## Candidato a remedio

Cambiar el `PendingIntent.getBroadcast(...)` de `ExactHeartbeatScheduler` por
**`PendingIntent.getForegroundService(...)`** apuntando a `CoordinatorDetectionService` con una
acción propia (`ACTION_HEARTBEAT`). Razones:

- Es el mismo truco que la app **ya usa y le funciona** en el carril AR (`getForegroundService`,
  exento de la restricción de FGS-desde-background).
- Un arranque de servicio no depende de la cola de broadcasts del proceso, que es exactamente lo que
  parece estar atascado.
- El intake de `CoordinatorDetectionService` ya serializa triggers [DET-INTAKE-001], así que el
  heartbeat entraría por la misma puerta que todo lo demás en vez de por una lateral.

Alternativas si eso falla: `AlarmManager.setAlarmClock` (la categoría que ningún OEM se atreve a
diferir, a costa de un icono de alarma en la barra de estado — probablemente inaceptable), o aceptar
la degradación y bajar la cadencia del worker periódico en devices con el carril muerto.

## Por qué está bloqueado

Es una **apuesta sobre conducta de ColorOS que no se puede validar sin horas de campo**. Meterla en
el mismo commit que la telemetría haría imposible saber cuál de las dos cosas cambió algo.

El orden es: primero se mide (`DET-HEARTBEAT-MISS-IS-EVIDENCE-001`, ya en master), luego se repara.
En cuanto el Oppo estampe `exactHeartbeatLaneDead: true` en una sesión de Firestore tendremos la
línea base, y este ticket se juzga por si ese campo pasa a `false`.

## Criterio de éxito

- Con el build nuevo, el Oppo vuelve a emitir `⏰ exact heartbeat fired` con cadencia ~5-15 min.
- `exactHeartbeatLaneDead` pasa de `true` a `false` en sus sesiones.
- El Redmi (que ya funcionaba) no se degrada: mismo número de heartbeats por hora que antes.
