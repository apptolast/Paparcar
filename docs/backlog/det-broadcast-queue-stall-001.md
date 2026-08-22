# DET-BROADCAST-QUEUE-STALL-001 · La cola de broadcasts del Oppo se atasca durante horas, y de ahí cuelgan tres síntomas

**Estado:** 🔵 Abierto · **paraguas** · primer paso = una prueba de 2 minutos que decide si esto es
un ticket o tres · descubierto el 22-08 investigando `DET-HEARTBEAT-MISS-IS-EVIDENCE-001`

## Hipótesis

En el **Oppo CPH2371** (ColorOS, Android 13) los `BroadcastReceiver` declarados en manifiesto
**dejan de recibir durante horas**, mientras el arranque de servicio (`getForegroundService`) sigue
funcionando con normalidad en el mismo instante y en el mismo device.

No es «la app está muerta»: durante todo ese tiempo el proceso está vivo, con el FGS corriendo, en
standby bucket **EXEMPTED** y con los workers de `WorkManager` disparando clavados cada 15 min.

## Evidencia (21/22-08)

El carril de broadcast murió **a una hora exacta: 21:16:56**. Los **dos** receivers de la app se
callaron en el mismo segundo y nunca más volvieron:

| Carril | Mecanismo | Última entrega | Después de las 21:17 |
|---|---|---|---|
| Geofence EXIT → servicio | `getForegroundService` | 22:39:46 | ✅ 2 entregas |
| `ExitWitness` | `getBroadcast` | **21:16:56** | ❌ ninguna |
| Heartbeat exacto | `getBroadcast` | **21:16:56** | ❌ ninguna |

**El dato que lo cierra:** a las 22:09:23 el **mismo evento físico de geocerca** disparó los DOS
PendingIntents que arma `GeofenceManagerImpl` — el de servicio (línea 156) y el de broadcast
(línea 169). Entró el de servicio. El broadcast no.

**Cómo murió:** las tres últimas entregas fueron una **ráfaga en 5 segundos** (21:16:51 · 21:16:56 ·
21:16:56), varias con `fixAge` de **7.214.891 ms ≈ 2 horas**, y el heartbeat con
`doze stretch: -295554 ms`. Eso es una **cola vaciándose de golpe con datos rancios**, no entrega
puntual. Corroborado desde fuera con `dumpsys alarm`: registros in-flight acumulándose para el
receiver, `aggregateTime` ≈ 5,2 h.

## Las tres víctimas

| # | Qué | Consecuencia | Ticket |
|---|---|---|---|
| 1 | **Heartbeat exacto** (`ExactHeartbeatScheduler:130`) | La red de seguridad cae a la rejilla de 15 min. Un viaje de 7 min cabe entero en una celda | ✅ medido (`DET-HEARTBEAT-MISS-IS-EVIDENCE-001`) · reparación en `DET-HEARTBEAT-LANE-REPAIR-001` |
| 2 | **`ExitWitness`** (`GeofenceManagerImpl:169`) | Alimenta el testigo `last_witnessed_*` de `DET-UNWITNESSED-DISPLACEMENT-001`. Llegó con **2 h de retraso**: testigo rancio pasando por fresco, en un gate que decide si un desplazamiento cuenta como viaje | **sin abrir** |
| 3 | **Acciones de notificación** (`AppNotificationManagerImpl:51`) | Los botones de las notificaciones no responderían. Explicaría el pendiente *«DET-STOP-BUTTON-001 ⏳ la acción de la notificación»* | **sin abrir** |

El carril de **logging** de AR (`ActivityRecognitionManagerImpl:44`) también es broadcast, pero el
de **DECISIÓN** ya va por `getForegroundService`: las decisiones están a salvo, lo que se pudre es
la traza.

## Paso 1 — la prueba de 2 minutos (hacer ANTES que nada)

Con el Oppo en este estado (heartbeat muerto, comprobable en `parkdiag`):

1. Provocar una notificación con acción (p. ej. el prompt «¿sigues aparcado?» o el botón de parar
   detección).
2. Pulsar la acción.
3. Mirar si su `onReceive` corre.

- **No responde** → hipótesis confirmada, esto es un paraguas real: abrir (2) y (3) y tratarlos
  juntos, porque la cura es la misma.
- **Sí responde** → la interacción del usuario descongela la cola. Sigue siendo un problema para lo
  desatendido (1 y 2), pero (3) se cae y el alcance se reduce.

## Cura candidata

La misma para todos: **sacar del broadcast lo que no puede permitirse esperar**, moviéndolo a
`getForegroundService` sobre el servicio de detección, con las guardas que ya están escritas en
`DET-HEARTBEAT-LANE-REPAIR-001` (exención sólo con alarma exacta; ojo con la estrategia Bluetooth y
la notificación cada 5 min).

Para (2) hay una alternativa más barata: el testigo `last_witnessed_*` podría **sellarse desde el
propio servicio** cuando recibe el EXIT por su carril bueno, en vez de depender de un receiver
paralelo — así el testigo nace del mismo evento que ya llega bien, y el receiver pasa a ser
redundante en vez de crítico.

## Criterio de éxito

- El Oppo deja de tener ventanas de horas sin entregas por los carriles que importan.
- `last_witnessed_*` nunca vuelve a llegar con horas de `fixAge`.
- Las acciones de notificación responden con la app en background.
- El Redmi no se degrada en ninguno de los tres.
