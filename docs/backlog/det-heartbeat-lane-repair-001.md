# DET-HEARTBEAT-LANE-REPAIR-001 · La cola de broadcasts del Oppo se atasca; el carril rápido tiene que entrar por donde sí se entrega

**Estado:** 🟡 **Código en master** (13-10-2026) · ⏳ **falta medirlo en el Oppo**, que es la única
cosa que puede cerrarlo · decisión del user 22-08: *«no sería mala decisión»* · depende de
`DET-HEARTBEAT-MISS-IS-EVIDENCE-001` (✅ master `0a0832cf`)

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

## Orden de trabajo — y el orden en que pasó de verdad

El plan era 1 → 2 → 3. Se hizo **el 3 primero**, y queda dicho porque cambia qué significa el
estado de este doc: el código está escrito y **no está medido**. Se puede hacer en este orden sin
apostar a ciegas por una razón concreta: los pasos 1 y 2 necesitan **el móvil del user**, y el 3
no — y la telemetría que hace juzgable el experimento viaja DENTRO del cambio (ver abajo), así que
la primera sesión del build nuevo ya trae su propio antes/después.

1. ⏳ **USER · La línea base.** Que el Oppo estampe `exactHeartbeatLaneDead: true` en Firestore con
   el build ANTERIOR. Si ya hay sesiones con ese campo a `true`, la línea base existe y este paso
   está hecho.
2. ⏳ **USER · La prueba barata de 2 minutos** (punto 2 de «lo que rompe más allá del heartbeat»):
   con el Oppo en este estado, pulsar la acción de una notificación y ver si `onReceive` corre. Si
   no corre, esto no era un ticket sino el primero de tres →
   [`det-broadcast-queue-stall-001`](det-broadcast-queue-stall-001.md).
3. ✅ **El cambio, con las dos guardas.** Hecho.

## ✅ Lo que se implementó (13-10-2026)

- **La decisión, pura y en `commonMain`**: `domain/detection/sentry/HeartbeatDeliveryLane.kt` —
  `heartbeatDeliveryLane(canScheduleExact, sentryResident)` → `SERVICE` / `BROADCAST`, con las dos
  guardas del diseño y 5 tests. Uno de ellos recorre las cuatro combinaciones y exige que **sólo**
  «las dos guardas puestas» abra el carril de servicio: la versión barata de esta función —una sola
  guarda, la que uno recuerde— es la que revienta en un install sin el permiso o parpadea una
  notificación a cada conductor con Bluetooth, y las dos sólo se verían en campo, en el móvil de
  otro.
- **El carril nuevo**: `ACTION_HEARTBEAT` en `CoordinatorDetectionService`, pasamanos como
  `ACTION_RESUME_SENTRY`, por el intake único. No arma el coordinator.
- **Qué hace un tick, en UN sitio**: `ExactHeartbeatScheduler.onTickDelivered`, compartido por las
  dos puertas. El receiver se queda siendo **sólo la puerta**.
- **El carril, estampado en la cabecera de sesión**: `exactHeartbeatLane` (`service`/`broadcast`),
  end-to-end — contrato `DeviceInfoProvider` + Android + iOS + fallback + logger + DTO.

### ⛔ La trampa que el diseño no vio: la alarma zombi

`AlarmManager.cancel()` casa **por PendingIntent**, y los dos carriles son dos registros distintos
(tipo distinto, Intent distinto). Re-armar tras un cambio de carril sin cancelar el otro deja la
alarma vieja pendiente — un tick entrando por la puerta que acabábamos de descartar. Y el cambio de
carril **no es hipotético**: la residencia cambia cuando un coche BT aparca al lado de uno
Coordinator, y el permiso se puede revocar entre dos ticks. Por eso `sync` cancela todos los
carriles menos el elegido, y el desarmado cancela los dos. El PendingIntent de broadcast se queda
**byte por byte** como estaba: una app actualizada en sitio puede tener un tick armado con ése, y
sólo uno igual lo cancela.

### ⚠️ El alcance honesto de este fix

En un install targetSdk 33+ el acceso especial de alarma exacta está **denegado por defecto**, y el
proyecto decidió no pedirlo [DET-EXACT-HEARTBEAT-001]: `exact=false` es el caso NORMAL, así que la
mayoría de devices **se quedan en broadcast y este fix no les llega**. El Oppo del campo sí tenía el
permiso concedido —está escrito en el KDoc de `isExactHeartbeatLaneDead`, medido el 21-08— y por eso
ahí sí se puede medir. Esa es también la razón de estampar el carril: sin él, «el carril nuevo
también murió» y «el carril nuevo no se usó aquí» se leen igual desde Firestore, y piden movimientos
opuestos.

## Consumidores auditados

| Camino | Veredicto |
|---|---|
| `ParkingSafetyNetWorker` → `sync(parkedAndIdle)` | cerrado: firma intacta, el carril se decide dentro de `sync` |
| `ExactHeartbeatReceiver` | cerrado: pasa a ser sólo la puerta |
| `SOURCE_EXACT_ALARM` | **exento con razón**: los dos carriles son el mismo trigger; separarlos ensuciaría la métrica del carril |
| `markLaneAlive` / `firedDelayMs` / racha | cubierto por convergencia: los escribe `onTickDelivered`, común a las dos puertas |
| `armedLane` → cabecera | cerrado con paridad end-to-end (contrato + Android + iOS + fallback + logger + DTO) |
| request code `41_006` | cerrado: libre en todo el repo (verificado por `grep`) |
| manifest | cerrado: el servicio ya está declarado y `SCHEDULE_EXACT_ALARM` también; el receiver sigue declarado porque su carril sigue existiendo |
| Dev Catalog / `MockScenario` | **exento con razón**: ni pantalla, ni estado MVI, ni routing — es el mecanismo de entrega de un trigger |
| `detectionPath` / `armEvidence` | **exento con razón**: no nace un camino de confirmación; el tick no confirma nada |
| Strings | **exento con razón**: cero texto de usuario |

**Suite:** `./gradlew testAll` — **3.898 tests, 0 fallos** (leído de los XML), con
`HeartbeatDeliveryLaneTest` (5) y `ExactHeartbeatHealthTest` (9) entre ellos. Compilan Android **e
iOS** (`compileKotlinIosSimulatorArm64`).
