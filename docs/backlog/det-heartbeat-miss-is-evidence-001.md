# DET-HEARTBEAT-MISS-IS-EVIDENCE-001 · Un carril rápido que ha dejado de latir tiene que decirlo

**Estado:** ✅ Done · en master vía squash · **1365 tests verdes** · `prod` + `mock` compilan ·
⏳ campo (esperar a que el Oppo estampe `exactHeartbeatLaneDead: true` en su próxima sesión)

## Problema

Field 2026-08-21/22, **Oppo CPH2371** (ColorOS, Android 13). El heartbeat exacto de 5 min está
muerto y **nadie se entera**:

| Hecho | Dato |
|---|---|
| Último `⏰ exact heartbeat fired` del día | **18:28** (uno suelto a las 21:16, nada después) |
| `⏰ exact net ARMED (exact=true, every 5 min)` | 22:41:07 |
| Ticks periódicos del worker desde entonces | 00:01:39 · 00:16:39 · 00:31:39 · 00:46:39 · 01:01:40 · 01:16:41 · 01:31:41 — **clavados cada 15 min durante 3 h** |
| Heartbeats en esas 3 h | **cero** |
| El Redmi al lado, el mismo día | **34 heartbeats** |

Y no es que estuviéramos mal configurados. Comprobado en el device a las 01:43:

- proceso **vivo**, con el FGS `CoordinatorDetectionService` corriendo;
- standby bucket **5 = EXEMPTED** (el mejor posible);
- `SCHEDULE_EXACT_ALARM` concedido (`canScheduleExactAlarms` → la app arma con `exact=true`);
- `next_at` = 01:36:41, es decir **la alarma sí estaba armada** y su momento había pasado 7 min antes;
- `dumpsys alarm`: 82 wakeups para nuestro receiver y **registros in-flight acumulándose** — el
  sistema dice que las alarmas SE ENTREGARON.

O sea: la alarma se dispara, el sistema despacha el broadcast, y **nuestro `onReceive` nunca corre**.
Eso es el OS.

## Doctrina violada

El contrato acepta al OS como excusa **con una condición**:

> *La única excusa aceptable es el OS (OEM force-stop, Doze extremo) — y debe ser **detectable a
> posteriori**.*

No lo era. Y peor: la métrica que existía para esto, `ExactHeartbeatScheduler.firedDelayMs`, **se lee
DENTRO del receiver que no corre**. Por construcción el fallo era inobservable.

La consecuencia práctica: en ese Oppo la red de seguridad llevaba horas degradada a la rejilla de
15 min — que es exactamente el tamaño del agujero por el que se cayó el viaje de 7 min 43 s de esa
noche (`DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001`) — y diagnosticarlo costó un cable y `dumpsys`,
porque Firestore no decía nada.

## Señales / datos disponibles

Todo estaba ya en disco: `exact_heartbeat.next_at`, escrito en cada armado. Sólo faltaba **leerlo
antes de sobrescribirlo**.

## Diseño

**No cambia ninguna decisión.** Nada arma, confirma o libera distinto. Sólo hace que un carril muerto
lo diga.

1. **Política pura** — `domain/detection/ExactHeartbeatHealth.kt`:
   - `nextExactHeartbeatMissStreak(previousStreak, scheduledAtMs, nowMs, config)` — un tick cuyo
     momento pasó sin que el receiver volviera a empujar el horario es uno que el carril perdió,
     *porque un receiver que SÍ corre lo habría adelantado antes de que esto se vuelva a leer*.
   - `isExactHeartbeatLaneDead(streak, config)`.
2. **`ExactHeartbeatScheduler.sync`** lee el armado SALIENTE antes de pisarlo y pliega la racha.
   Un único escritor, así que la cuenta no puede derivar. La racha va a disco junto al armado que
   juzga, para que un OEM-kill entre ticks no la resetee y esconda un carril muerto hace horas.
3. **`ExactHeartbeatReceiver`** llama a `markLaneAlive` lo primero: llegar ahí **es** la prueba de
   que el carril funciona en este device.
4. **`DeviceInfoProvider.isExactHeartbeatLaneDead`** → `exactHeartbeatLaneDead` en la cabecera de
   cada sesión de Firestore, al lado de `requiresOemBatteryFreeze`. Medido en vivo (como la exención
   de batería), no modelado por marca: un carril puede morir o revivir entre dos sesiones del
   mismo día.

### El número que el test corrigió

La primera versión ponía la gracia en **20 min**, razonando «hay que superar el estirón de Doze
(~9-15 min)». El test de replay del Oppo la tumbó: sus armados estaban exactamente **10 min**
rancios en cada pasada del periódico, así que una gracia de 20 min habría reportado **un carril
sano durante tres horas de silencio absoluto**.

El razonamiento correcto: *una sola mirada a un horario rancio NO puede distinguir «perdido» de
«estirado por Doze y aún pendiente»*, y ninguna gracia puede, porque ambos se leen igual. Lo que los
separa es el TIEMPO: un tick estirado acaba llegando y limpia la racha; uno perdido no. Así que la
gracia se queda corta (5 min = un intervalo entero de retraso) y **la racha lleva el veredicto**
(3 pasadas ≈ 45 min sin una sola entrega). Hay un test que fija esa relación para que nadie vuelva a
ensanchar la gracia hasta cegar la métrica.

## Consumidores auditados

| Sitio | Clasificación |
|---|---|
| `ParkingSafetyNetWorker` (único llamador de `sync`) | **cerrado** — pasa `config` |
| `ExactHeartbeatReceiver` | **cerrado** — `markLaneAlive` + `config` vía Koin (idioma de `BootCompletedReceiver`) |
| `DeviceInfoProvider` + `UnknownDeviceInfoProvider` + `IosDeviceInfoProvider` | **cerrados** — iOS y el fallback devuelven `false`: sin carril de alarma exacta no hay nada que se muera |
| `AndroidDeviceInfoProvider` + `AndroidPlatformModule` | **cerrado** — recibe `ParkingDetectionConfig`, lectura viva por acceso |
| `DetectionEventDto` + `FirestoreDetectionEventLogger` | **cerrados** — campo nuevo en la cabecera de sesión, nullable (las sesiones viejas no mienten, callan) |
| `firedDelayMs` | **exento** — sigue midiendo el estirón de Doze cuando el tick SÍ llega; es la métrica complementaria, no la sustituida |

## Lo que este ticket NO hace

⛔ **No intenta reparar el carril.** Cambiar el `PendingIntent.getBroadcast` por
`getForegroundService` (el mismo truco que ya usa el carril de AR) es un candidato razonable, pero
es una apuesta sobre conducta de ColorOS que **no se puede validar sin horas de campo**, y meterla
en el mismo commit que la telemetría haría imposible saber cuál de las dos cosas cambió algo.

Primero se mide, luego se repara. Cuando el Oppo estampe `exactHeartbeatLaneDead: true` en una
sesión, tendremos el antes/después que hace falta para juzgar el remedio.

→ Candidato anotado en `docs/backlog/det-heartbeat-lane-repair-001.md`.

## Criterio de éxito

- Test de replay con la cadencia real de esa noche (7 pasadas del periódico, 6 ticks perdidos) → DEAD.
- Test con la cadencia del Redmi (ticks que vuelven) → racha 0, nunca DEAD.
- Test que fija gracia < (periódico − heartbeat), para que la métrica no se vuelva a cegar.
- En campo: el Oppo estampa `exactHeartbeatLaneDead: true`; el Redmi, `false`.
