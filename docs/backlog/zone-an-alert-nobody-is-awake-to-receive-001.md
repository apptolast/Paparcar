# ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001 · La única alerta que ya cobramos solo llega con la app abierta

**Estado:** ✅ Done — en master (15-09-2026).

> ✅ **El choque de versiones se resolvió al mergear, como estaba previsto.** Esta rama se escribió
> como v10 → v11 y `FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001` también: dos ramas
> paralelas sobre la misma v10 reclamando el mismo número. Git no ve esa colisión — son ficheros
> distintos tocados en sitios distintos y un NÚMERO compartido en silencio —, así que la segunda en
> entrar renumera. La de zonas entró segunda: **v11 = vigilancia de precios, v12 = vigilancia de
> zona**, con `12.json` reexportado y la suite verde encima del master nuevo.

Sucesor directo de `ZONE-SUBSCRIBE-001`, que `MONEY-001` dejó escrito como «follow-up» y nunca se
abrió. La vía definitiva (push) está aparcada en
[`infra-an-alert-that-travels-needs-a-backend-001.md`](infra-an-alert-that-travels-needs-a-backend-001.md).

## Problema

La alerta de zona **está implementada y vendida**: `ObserveZoneSpotAlertsUseCase` emite una vez por
plaza nueva dentro de una zona con `notifyOnSpot`, con gate Plus vivo, master switch en Ajustes y
paywall soft. Lo que no está es la **entrega**:

```kotlin
// ObserveZoneSpotAlertsUseCase, comentario propio:
// Deliberately client-side for 1.x: it runs while the app process is alive (the
// AppViewModel collects it), because geofences are frozen and push fan-out needs a backend
```

La colecta el `AppViewModel`. Proceso muerto, alerta muerta. Y la app está cerrada exactamente
cuando la alerta sirve para algo: **mirar el mapa es gratis** — si ya lo tienes abierto no
necesitas que nadie te avise. O sea que la única función que cobramos por vigilar solo vigila
cuando no hace falta.

## Doctrina violada

- **«Free = tú preguntas y la app responde. Plus = la app te avisa a ti»**
  (`plus-alerts-and-tiers-001.md`). Una alerta que solo llega con la app delante **es free
  disfrazado de Plus**: no añade vigilancia, añade un duplicado de lo que ya se ve.
- **Fallo asimétrico y silencios** (doctrina de detección): un aviso que no llega no avisa de que no
  ha llegado. El usuario no puede distinguir «no había plazas» de «no estaba mirando».
- **Al perder Plus nada se borra, se pausa** (`MONEY-001`) — hay que preservarlo al mover el gate a
  un worker, que es donde es fácil romperlo.

## Señales / datos disponibles

- Zonas con `notifyOnSpot` en Room, y `SpotRepository.observeNearbySpots(center, radius)` — la misma
  geo-query, ya escrita y ya probada.
- `showZoneSpotAlert` en `AppNotificationManager`, con su ID (2009) y su canal.
- WorkManager con 13 workers y su patrón establecido; `ParkingSafetyNetWorker` ya corre cada 15 min.
- Gate Plus vivo (`ObserveIsPlusUseCase`) + master switch (`AppPreferences.notifyZoneSpots`).
- **TTL medido** (`SpotTtlPolicy`): plaza automática **2 h**, provisional **12 min**, gracia de
  retracción 2 min.

## Diseño

### 1 · El fallo que hay que arreglar ANTES de poner el worker

Hoy el «ya avisado» es estado **en memoria** dentro del stream:

```kotlin
var baselineTaken = false
val alerted = mutableSetOf<String>()
```

Y el baseline es lo que impide que, al arrancar, te avise de las plazas que ya estaban («alertar el
mapa entero enseñaría al usuario a deslizarlas»). Los dos son correctos para un stream largo y
**letales para un worker**, que arranca de cero cada 15 minutos:

- si toma baseline en cada ejecución → **no avisa nunca**;
- si no lo toma → **te vuelca la zona entera cada cuarto de hora**.

O sea que el worker no es «lo mismo pero periódico»: exige que el conjunto de avisados **baje a
disco**, con su poda. Esto es el corazón del ticket, no un detalle de implementación.

Forma: tabla `zone_alerted_spots` (`spotId`, `zoneId`, `alertedAtMs`), podada por el TTL más largo
que puede tener una plaza. Se poda **por caducidad de la plaza**, no por antigüedad del aviso: lo
que evita repetir es que la plaza siga viva, y cuando muere, su fila sobra.

### 2 · El worker

`ZoneSpotWatchWorker`, periódico 15 min (el mínimo de WorkManager), con el mismo patrón que
`ParkingSafetyNetWorker`. Por cada zona suscrita: geo-query **una vez** (`first()`, no `collect`),
filtrar con el mismo `isAlertable` que ya existe — no retractada, no tuya, dentro del círculo real —
restar las ya avisadas, notificar y anotar.

⛔ **El `isAlertable` no se duplica**: hoy es privado del use case. Sube a función pura de nivel
superior para que stream y worker compartan literalmente el mismo veredicto. Dos copias de esta
regla es una divergencia esperando a pasar.

### 3 · Lo que este diseño NO puede prometer, dicho antes de prometerlo

- **Una plaza provisional dura 12 min** y el poll es de 15: por esta vía **no se verá nunca**.
  Se excluyen explícitamente. Prometer y fallar es peor que no cubrirlas.
- Una automática dura **2 h**: llega con hasta 15 min de retraso y aún le quedan **1 h 45**. Aquí el
  poll sí sirve, y es la razón de que este ticket exista en vez de esperar al backend.
- **Doze**: sin exención de batería, de madrugada el retraso real es mucho mayor que 15 min. La
  exención ya se pide para detección; aquí **no se vuelve a pedir** ni se dramatiza.
- 💸 **Coste**: 96 ejecuciones/día × zona × usuario Plus contra Firestore. Es el argumento económico
  que hace que la vía push acabe ganando — y la razón de medir este número en el ticket, no después.

### 4 · Gating, sin perder la regla de degradación

El worker se programa y se cancela con el mismo par que ya decide el stream: **Plus ∧ master switch
∧ existe alguna zona con `notifyOnSpot`**. Perder Plus **cancela el worker** sin tocar el
`notifyOnSpot` de ninguna zona — pausar, no olvidar. Recuperar Plus lo reprograma.

### 5 · Qué pasa cuando llegue FCM

Este worker se **retira**, no se queda de respaldo: dos vías de entrega para el mismo aviso es la
forma más fácil de notificar dos veces la misma plaza. La tabla de avisados sobrevive y sigue
sirviendo — con push también hay que saber qué ya se dijo.

## Criterio de éxito — medido

| | |
|---|---|
| **Dos miradas seguidas sobre las mismas plazas avisan UNA vez** — el fallo que cierra el ticket | ✅ `RunZoneSpotWatchUseCaseTest` (12 tests) |
| Primera mirada a una zona: apunta el suelo, no lo cuenta | ✅ |
| Provisional (12 min) · propia · retractada · caducada · fuera del círculo → no se avisa | ✅ |
| Plus caído → `Skipped`; el `notifyOnSpot` y las filas **sobreviven** | ✅ |
| Una consulta que falla **no** deja la zona marcada como vista | ✅ |
| Dos zonas solapadas sobre la misma plaza → un aviso por zona | ✅ |
| La memoria sobrevive al proceso, se poda por la muerte de la PLAZA y se barre con la zona | ✅ `ZoneWatchStoreTest` (4, Robolectric, SQLite real) |
| Stream y worker resuelven idéntico `zoneSpotsWorthTelling` | ✅ una función, dos llamadas |
| Suite completa · `compileMockDebugKotlin` · `compileProdDebugKotlin` · iOS · guardarraíles con `--rerun-tasks` | ✅ |

### ⚠️ Una conducta que CAMBIA, y es una decisión, no un efecto colateral

Una plaza que aparece **mientras el Plus está caído** y sigue viva al volver: antes se tragaba para
siempre, ahora se avisa una vez.

Aquel silencio no era una decisión, era dónde vivía el conjunto: en memoria. Al recuperar Plus el
stream se reconstruía, tomaba baseline nuevo y se la comía. El conjunto **tenía** que salir de la
memoria — un worker no puede heredarlo —, así que había que decidir. Lo decidido: una plaza que
**sigue libre** cuando la vigilancia vuelve merece una interrupción. No es un volcado: el filtro de
TTL lo acota a lo que de verdad está ahí (dos horas como mucho) y las dos vías comparten id de
notificación, así que lo que el usuario ve es **una** tarjeta.

⛔ No es la misma pregunta que el master switch de Ajustes, que **sigue tragando**: silenciar un
canal es el usuario diciendo «no me lo cuentes», y respetarlo no puede ser contárselo todo de golpe
al reabrirlo. `ObserveZoneSpotAlertsUseCaseTest` lo deja escrito en los dos tests, uno al lado del otro.

### Lo que NO está medido

1. **Campo, pendiente**: móvil con la app cerrada, plaza publicada desde el otro móvil dentro de la
   zona → notificación en ≤ 15 min. Es la única prueba que vale de este ticket.
2. **Sin test del salto v10 → v11 con un fichero v10 real** — mismo hueco, misma razón y mismo
   ticket-aparte que en `FUEL-A-PRICE-REMEMBERED…`: sembrar una v10 exige el DDL de las once tablas
   porque Room valida el esquema entero tras migrar.
3. 💸 **Coste sin medir**: 96 ejecuciones/día × zona × usuario Plus contra Firestore. Es el número
   que acabará justificando la vía push; no se ha medido en factura real.

## Consumidores auditados

| Quién | Asumía | Estado |
|---|---|---|
| `ObserveZoneSpotAlertsUseCase` | el «ya avisado» era suyo y vivía en memoria | ✅ reescrito sobre `ZoneWatchRepository`; **de paso arregla** que renombrar una zona reseteaba su baseline |
| `isAlertable` (privado del stream) | un solo carril | ✅ sube a `domain/zone/isAlertableInZone`, con la exclusión de PROVISIONAL dentro — donde los dos carriles la heredan |
| `AppViewModel` | colecta el stream | ✅ intacto: la firma del use case cambió, el contrato no |
| `PaparcarApp` | no programaba nada de zonas | ✅ `ZoneWatchScheduler` como **stream**, no lectura única — la trampa que `DET-WATCH-REACTIVATE-001` ya tuvo que deshacer |
| `showZoneSpotAlert` + id 2009 | un solo emisor | ✅ el worker usa **el mismo** id, así que stream y worker no pueden apilar dos tarjetas por la misma plaza |
| Regla de degradación `MONEY-001` | perder Plus no borra | ✅ el gate vive **solo** dentro del use case; el scheduler deliberadamente NO lee la titularidad |
| `ZoneEntity` / borrado de zonas | nadie limpiaba detrás | ✅ barrido de huérfanos por `zoneId NOT IN (SELECT id FROM zones)`, en un sitio |

## Seguimiento abierto

**El baseline es local a este dispositivo.** Marca cuándo ESTE móvil empezó a mirar, así que un
segundo teléfono toma el suyo y puede callarse una plaza que el primero anunció. La solución no es
otra tabla: es saber cuándo se hizo la SUSCRIPCIÓN, no cuándo miró un aparato — o sea un campo en la
zona (`notifySince`), con su migración y su paridad de serializadores. Fuera de alcance aquí; entra
solo, y con más razón, el día que llegue el push.
