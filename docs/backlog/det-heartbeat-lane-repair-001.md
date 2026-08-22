# DET-HEARTBEAT-LANE-REPAIR-001 · La cola de broadcasts del Oppo se atasca; el carril rápido tiene que entrar por donde sí se entrega

**Estado:** 🟠 Abierto, sin código, **con evidencia de campo suficiente para decidir** ·
decisión del user 22-08: *«no sería mala decisión»* · depende de `DET-HEARTBEAT-MISS-IS-EVIDENCE-001`
(✅ master `0a0832cf`)

## Problema

En el **Oppo CPH2371** (ColorOS, Android 13) el heartbeat exacto de 5 min **se dispara pero su
broadcast nunca llega a la app**. Diagnóstico completo en
`docs/backlog/det-heartbeat-miss-is-evidence-001.md`.

## La evidencia nueva (22-08) — casi un experimento controlado

El carril de broadcast del Oppo murió **a una hora exacta: 21:16:56**. Los **dos** receivers de
broadcast que tiene la app se callaron en el mismo segundo, y el carril de servicio siguió:

| Carril | Mecanismo | Última entrega | Después de las 21:17 |
|---|---|---|---|
| Geofence EXIT → servicio | `getForegroundService` | 22:39:46 | ✅ 2 entregas |
| `ExitWitness` | `getBroadcast` | **21:16:56** | ❌ ninguna |
| Heartbeat | `getBroadcast` | **21:16:56** | ❌ ninguna |

**El dato que lo cierra:** a las 22:09:23 **el mismo evento físico de geocerca disparó los dos
PendingIntents a la vez** — `GeofenceManagerImpl` arma `getForegroundService` para el EXIT
(línea 156) y `getBroadcast` para el testigo (línea 169). El del servicio entró; el broadcast no.
Mismo device, mismo instante, mismo evento, dos mecanismos, uno funciona y el otro no.

**Cómo murió** — las tres últimas entregas fueron una **ráfaga en 5 segundos** (21:16:51 ·
21:16:56 · 21:16:56), varias con `fixAge` de **7.214.891 ms ≈ 2 horas**, y el heartbeat con
`doze stretch: -295554 ms` (o sea, «adelantado» 5 min respecto a su horario). Eso no es entrega
puntual: es una **cola atascada vaciándose de golpe** con datos rancios. Encaja con `dumpsys alarm`:
registros in-flight acumulándose para el receiver, `aggregateTime` ≈ 5,2 h. Los broadcasts se
encolan, se drenan a ráfagas, y desde las 21:16 no se drenaron más.

## Diseño propuesto

Entregar el heartbeat por **`PendingIntent.getForegroundService`** a `CoordinatorDetectionService`
con acción propia (`ACTION_HEARTBEAT`), en vez de por broadcast.

Beneficio secundario: entra por el **intake único** [DET-INTAKE-001] en vez de por una puerta
lateral. El handler es un **pasamanos**: re-armar + encolar el check de la red de seguridad, y
**NO** armar el coordinator. Exactamente lo que hace hoy el receiver.

### ⚠️ Las dos trampas (un cambio a pelo NO vale)

**1. La exención de FGS depende del permiso exacto.** `getForegroundService` desde alarma sólo está
exento de la restricción de arranque-desde-background si la alarma es **exacta** y la app tiene
`SCHEDULE_EXACT_ALARM`. `ExactHeartbeatScheduler` **cae a `setAndAllowWhileIdle`** (inexacta) cuando
el permiso falta — y esa vía **no** está exenta. Cambiarlo a ciegas convertiría un carril atascado
en uno que revienta.
→ **Entrega por servicio sólo si `canScheduleExact(alarmManager)`; broadcast en caso contrario.**

**2. La estrategia Bluetooth.** El heartbeat se arma con `parkedAndIdle`, que **no mira la
estrategia**. Bajo BT el sentry **no** se queda residente [DET-STRATEGY-GATE-001], así que un
arranque de FGS cada 5 min significaría **una notificación parpadeando cada 5 minutos** con el
Kamiq aparcado. Bajo Coordinator no cuesta nada: el servicio ya está en foreground y sólo recibe un
`onStartCommand` más — sin proceso nuevo, sin notificación nueva.
→ **Atar la entrega por servicio a que el sentry esté residente**; si no, broadcast.

### Se audita solo

La telemetría de `DET-HEARTBEAT-MISS-IS-EVIDENCE-001` **mide igual sea cual sea el mecanismo**. Si
el carril nuevo también muere, `exactHeartbeatLaneDead` lo dirá igual. Eso es justo lo que se compró
midiendo primero: este cambio no es una apuesta a ciegas, es una apuesta **instrumentada**.

### Alternativas descartadas / de reserva

- `AlarmManager.setAlarmClock` — la categoría que ningún OEM difiere, a costa de un **icono de
  alarma permanente en la barra de estado**. Casi seguro inaceptable para el user; queda de último
  recurso.
- Aceptar la degradación y **bajar la cadencia del worker periódico** en devices con el carril
  muerto. Barato, pero WorkManager también se batchea en Doze: compra menos de lo que parece.
- Entrega **dual** (broadcast + servicio, el que llegue primero). Descartada: duplica el trabajo y
  ensucia la métrica que acabamos de montar.

## Lo que este atasco rompe MÁS ALLÁ del heartbeat

Misma causa, tres víctimas. **No se arreglan en este ticket**, pero quedan nombradas:

1. **`ExitWitness`** (`GeofenceManagerImpl:169`) alimenta el testigo `last_witnessed_*` de
   `DET-UNWITNESSED-DISPLACEMENT-001`. En el Oppo llegó con **2 horas de retraso** — un testigo
   rancio pasando por fresco. La cadena de evidencia se degrada en silencio, y ese gate decide si un
   desplazamiento cuenta como viaje.
2. **Las acciones de las notificaciones** (`AppNotificationManagerImpl:51`) también son
   `getBroadcast`. Conecta con un pendiente que ya existía: *«DET-STOP-BUTTON-001 ⏳ campo + **la
   acción de la notificación**»*. Puede que no fuera un bug del botón, sino de la cola.
   **Prueba barata:** con el Oppo en este estado, pulsar la acción de una notificación y ver si
   `onReceive` corre.
3. El carril de **logging** de AR (`ActivityRecognitionManagerImpl:44`, `getBroadcast`) — el de
   DECISIÓN ya va por `getForegroundService`, así que las decisiones están a salvo; lo que se pudre
   es la traza.

→ Si la prueba de (2) confirma, abrir `DET-BROADCAST-QUEUE-STALL-001` como ticket paraguas.

## Criterio de éxito

- Con el build nuevo, el Oppo vuelve a emitir `⏰ exact heartbeat fired` con cadencia ~5-15 min.
- `exactHeartbeatLaneDead` pasa de `true` a `false` en sus sesiones de Firestore.
- El Redmi (que ya funcionaba) **no se degrada**: mismo número de heartbeats por hora que antes.
- Con un vehículo **Bluetooth** aparcado, **cero notificaciones nuevas** cada 5 min.
- Sin `SCHEDULE_EXACT_ALARM`, el carril sigue vivo por broadcast (no revienta).

## Orden de trabajo

1. ⏳ **Primero: la línea base.** Que el Oppo estampe su primer `exactHeartbeatLaneDead: true` en
   Firestore. Sin ese antes/después el cambio no es juzgable.
2. La prueba barata de la acción de notificación (punto 2 de arriba) — 2 minutos, y decide si esto
   es un ticket o el primero de tres.
3. El cambio, con las dos guardas.
