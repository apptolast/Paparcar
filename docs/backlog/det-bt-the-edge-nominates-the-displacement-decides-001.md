# DET-BT-THE-EDGE-NOMINATES-THE-DISPLACEMENT-DECIDES-001 · el borde BT nomina, el desplazamiento decide

**Estado:** ✅ Done — en master. Sale del diagnóstico de campo de
[[home-an-unseeded-camera-is-not-a-place-001]] (16-09).

> ⚠️ **No confundir con `det-bt-veto-must-not-orphan-a-session-001`**, que sigue abierto y es OTRA
> cosa: allí el «veto» es el de identidad BT que impide al safety-net LIBERAR una sesión de larga
> estancia (`a786c135`), y el problema es el coste de no poder cerrarla. Aquí el veto es el
> **supersede del arbitraje**, que MATA una sesión del coordinator en marcha. Comparten la forma
> —un carril sin veredicto manda sobre otro— y por eso el nombre se parecía; el código que tocan no
> se solapa.

## Problema

Fernando (`8fhNJGct…`) tiene su Skoda Kamiq emparejado al MAC `BT-HANDSFREE-AFTERMARKET`. Ese dispositivo
**no se comporta como un coche** — medido sobre su parkdiag de 11 días (52.724 líneas):

| Señal | Valor |
|---|---|
| Enganches connect→disconnect | **237** |
| Descartados por `not a ride` (< 90 s) | **235** |
| Mediana de enganche | **17 s** |
| Máximo de enganche | **57 s** — ninguno llega al minuto |
| Enganches realmente ride-shaped en 11 días | **2** (292 s el 08-09, 361 s el 12-09; el segundo abortó a media conducción) |

Mientras tanto, el estéreo real del Kamiq (`BT-CAR-KAMIQ`) aparece **70 veces** y siempre como
*«no vehicle paired — ignoring»*. Es decir: emparejó unos auriculares (o un wearable) como si fueran
el coche, y el coche de verdad lo ignora la app.

La consecuencia no es que el carril BT no sirva — es que **mata al otro**:

```
09-16 09:09:09.128 Coord:   ✓ hasEverMoved → true (dist=181.6m)
09-16 09:09:09.128 Coord:   ✓ drive PROVEN by displacement [DET-SHORT-HOP-PROOF-001]
09-16 09:09:18.668 BTReceiver: ⚡ BT arbitrates over the coordinator: te0 — signalling abort
09-16 09:09:18.677 Service:   → BT_OVERRIDE (te0) — Bluetooth supersedes, aborting session
09-16 09:09:18.708 BTDetector: BT engagement was not a ride (proximity 10s < 90s) — asking instead of placing
```

Nueve segundos después de PROBAR la conducción, un disconnect de 10 s la aborta — y el propio carril
que abortó declara acto seguido que él tampoco va a colocar nada. **36 sesiones del coordinator
abortadas así** en 11 días, varias justo tras `drive PROVEN`.

Por eso su detección vive del nudge: **32 de sus 42 confirmaciones son `reliability=1.0`**, o sea la
app preguntando. Y el nudge era justo la puerta que llevaba a Null Island.

## Doctrina violada

*El evento NOMINA, solo el movimiento MEDIDO confirma* — y aquí un evento que el propio evaluador
califica de no-viaje **desmiente** movimiento medido. La arbitración BT sobre el coordinator existe
para que no haya dos carriles decidiendo lo mismo; pero cuando el carril BT ya ha resuelto que no
tiene veredicto (`DET-BT-DISCONNECT-WITHOUT-RIDE-001`), su veto deja la sesión huérfana: nadie
decide, y el fallo asimétrico se paga en falso negativo permanente.

## Doctrina que fija este ticket (dictada por el user, 16-09)

> **El BT es el TRIGGER y la señal fuerte, no la total.** Un connect/desconnect admite variantes:
> - conectar y desconectar **sin desplazamiento** → abortar; no ha pasado nada;
> - desconectar **conduciendo** y volver a conectar → la detección **sigue corriendo**, porque ya hay
>   conducción probada;
> - y hay que **medirlo bien**: mirar si el usuario ha parado de verdad o sigue conduciendo.

Es la doctrina general del proyecto aplicada al carril BT: *el evento NOMINA, solo el movimiento
MEDIDO confirma*. Hoy el carril BT es el único que decide con un RELOJ.

## Hallazgo: la medición ya existe, la puerta de duración corta antes

Master ya tiene todo el vocabulario que esta doctrina pide —
`evaluateRideDisplacement()` → `BtRideProof.Moved` / `DidNotMove` / `Unmeasurable`, con el testigo
del grabador de ruta (`drivingRouteStore.points()`) y el fix del connect (`BtConnectionStore`)— y
también la comprobación de «¿sigue conduciendo?» (`hunt.aborted` ante un fix de conducción creíble
durante el muestreo, [DET-AUDIT-002 T2]).

**Nada de eso se ejecuta cuando el enganche es corto.** `BluetoothParkingDetector:94` pregunta
PRIMERO por la duración y sale por la puerta de atrás:

```kotlin
val engagement = evaluateBtPark.evaluateEngagement(connectedAtMs, disconnectedAtMs)
if (engagement !is BtEngagement.Ride) {            // < 90 s
    …"not a ride (proximity 10s < 90s) — asking instead of placing"
    notificationPort.showMarkParkingNudge(…)        // ← nudge a ciegas, sin medir un metro
    return
}
```

Esos son los **235 nudges** de Fernando. Y el `evaluateEngagement` lo justifica en su KDoc: *"at this
instant nothing has been measured yet"* — cierto cuando se escribió, **falso hoy**: el fix del
connect y la ruta grabada están en disco antes de esa línea.

## Diseño

**1 · La duración NOMINA, el desplazamiento DECIDE.** Un enganche corto deja de disparar un nudge a
ciegas y pasa a medirse con las mismas herramientas que ya usa el enganche largo:

| Enganche corto + … | Hoy | Con el fix |
|---|---|---|
| `Moved` (desplazamiento medido) | nudge a ciegas | **fue un viaje** → sigue la caza, como un enganche largo |
| `DidNotMove` (medido, no se movió) | nudge a ciegas | **abortar en silencio** — no ha pasado nada (regla del user) |
| `Unmeasurable` (no se pudo medir) | nudge | **nudge** — duda → se pregunta; el fallo asimétrico manda |

⛔ **`DidNotMove` ≠ `Unmeasurable`**, y confundirlos sería el bug siguiente: «no se movió» es una
medición; «no pude medir» (garaje, sin fix de origen, origen rancio) es su ausencia. Solo la primera
autoriza el silencio.
⛔ El enganche RIDE-shaped con `DidNotMove` **sigue preguntando** como hoy: esa rama la decidió
[DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001] a propósito y este ticket no la reabre.

**2 · El supersede no puede matar conducción probada.** `EvaluateBtArbitrationUseCase:97` devuelve
`SupersedeWithBluetooth` en TODO DISCONNECT del mismo coche, y el servicio hace `cancelDetectionJob()`.
Pasa a exigir que no haya conducción probada en la sesión del coordinator: si la hay, el carril BT
nomina pero **no mata al que está midiendo** — es el caso 3 del user (desconexión + reconexión a
media conducción). Requiere subir `driveProven` de `DetectionSessionState` al `DetectionRuntime`,
que es lo que el árbitro lee; la decisión sigue pura en el use case, que lo recibe como parámetro.

⛔ La doctrina de carriles separados NO se toca: cero señales BT en el scoring del coordinator. Lo
que cambia es quién puede ABORTAR a quién, que es arbitraje, no scoring.

## Diseño previo (descartado)

Dos mitades, seguramente independientes:

1. **El veto se retira cuando el carril BT se declara sin veredicto.** Si el enganche no fue un
   viaje, el `BT_OVERRIDE` no debe abortar una sesión del coordinator que ya tiene conducción
   medida — debe devolverle el mando. Ojo con el orden: hoy el abort llega ~40 ms ANTES de que el
   detector BT evalúe la proximidad, así que la decisión hay que tomarla en el intake, no después.
2. **Un MAC que nunca se comporta como un coche debería poder decirse.** 235 enganches de mediana
   17 s son un dato, no una sospecha. Candidato a veredicto propio (citable en diagnóstico) que
   proponga al usuario revisar qué dispositivo emparejó — sin desemparejar nada por su cuenta.

## Criterio de éxito

- Con un MAC que flapea, un trayecto real con conducción medida **termina en pin**, no en pregunta.
- Lo que `DET-BT-DISCONNECT-WITHOUT-RIDE-001` protege sigue protegido: un enganche corto **sigue sin
  colocar** plaza por sí mismo.
- Replay de la traza del 16-09 09:08–09:10 como caso de regresión.


## Lo hecho

- `EvaluateBtParkUseCase` — nace `BtShortEngagementCall` (`WasARide` / `NothingHappened` / `Ask`) y
  `evaluateShortEngagement()`. El evaluador de desplazamiento **no se toca**: ya sabía declarar sin
  candidato usando el grabador de ruta.
- `BluetoothParkingDetector` — la lectura del grabador **sube por encima** de la puerta de duración,
  y el enganche corto se mide antes de decidir. Verdicto remoto nuevo `bt_short_no_move_abort`, para
  que el campo pueda separar «no se movió» de «no pude medirlo» sin columna nueva.
- `DetectionRuntimeState` — `driveProven` (lectura) + `DetectionPhaseSink.setDriveProven` (escritura),
  reseteado en los dos bordes de `setRunning` para que una sesión nueva no herede la prueba anterior.
- `CoordinatorParkingDetector` — colector reactivo gemelo al de la fase, cancelado en el mismo
  `finally`.
- `EvaluateBtArbitrationUseCase` — `coordinatorDriveProven`, **sin valor por defecto**: un `= false`
  dejaría nacer roto al siguiente call site, que es el bug que cierra.
- `docs/detection/PARKING-DETECTION.md` §2 con la entrada del fix.
- Sin strings nuevos, sin pantallas ni estados nuevos (no toca Dev Catalog), sin `detectionPath`
  nuevo. iOS: `DetectionRuntimeState`, ambos evaluadores y el coordinator son `commonMain`; el
  detector y el servicio son `androidMain` y ya lo eran — **no se abre hueco nuevo de plataforma**.

**Verificación:** `./gradlew testAll` → **3318 tests, 0 fallos**. 7 tests nuevos, comprobados uno a
uno en los XML: 3 del árbitro (mata/no mata/el CONNECT sigue vetando) y 4 del enganche corto
(`DidNotMove` aborta · `Moved` sigue · `Unmeasurable` pregunta · `Unknown` pregunta).

⏳ **Pendiente:** campo con un MAC que flapee.

## Fuera de alcance (ticket aparte, decidido por el user)

- **Avisar de que el dispositivo emparejado no parece un coche** — 235 enganches de mediana 17 s no
  son una sospecha, son un dato; y mientras tanto el estéreo real se ignora por no estar emparejado.
  Es producto (copy, dónde y cuándo se dice), no detección.
