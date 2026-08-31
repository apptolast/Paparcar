# DET-BACKFILL-MUST-NOT-PIN-A-MOVING-CAR-001 · el backfill no planta un pin sobre un coche en marcha

**Estado:** ✅ Done · mergeado a master por squash · ⏳ **sin validar en campo**

## Problema

Field test **30-08-2026**, Oppo (uid `90lnZzs5PzLNjVzn99U7rs9YUAU2`, vehículo "Prueba Oppo" Ford
Focus, estrategia COORDINATOR). Único falso positivo del día: un pin en **Calle del Verdugo 24**
(`36.6273783,-6.22443`), **en mitad del trayecto, sobre la carretera**, con **plaza publicada**
(`publishedSpot=1`). Vivió 52 s (21:27:34 → 21:28:26), pero la plaza ya había salido a la comunidad.

El coche estaba realmente aparcado en Av. Blas Infante (geofence `785dabe3`, pin de las 20:40:29).
El móvil no despertó durante el viaje de vuelta; despertó a las **21:27:33 ya a 3.652 m del coche y
circulando**. Secuencia literal del `parkdiag.log` del Oppo:

```
21:27:33.967 SafetyNet ▶ far with vehicle evidence — dispatching departure geofence=785dabe3
                          (preconfirmed=true steps=24 d=3652m)
21:27:33.968            → chaining parking backfill at wake-up fix (steps=24 acc=6.6, arrivalWalk=12 steps)
21:27:34.263 ExitWitness ⚑ EXIT emitted geof=a99ff278 ... fixAge=6075717ms      ← 101 MINUTOS
21:27:34.317 ARReceiver  ✓ IN_VEHICLE ENTER ... lag=6069910ms                   ← 101 MINUTOS
21:27:34.649 Backfill   ✓ backfilled parking at 36.6273783,-6.22443 (reliability=0.5)
```

El fix con el que se plantó el pin decía **`speed=0.0 acc=6.6m`** yendo por carretera. **Siete
segundos después el propio coordinator lo refutó**, con un guard que ya existe:

```
21:27:41.731 Coord ⚓✗ stop REFUTED by its own track — 134m from the stop origin in 5s
                     while reporting 0.0 m/s (envelopes 6.9+6.8m); the car was still moving
                     — not evidence of rest [DET-STOP-MUST-BE-STILL-IN-SPACE-001]
```

## ⛔ Esto ya estaba escrito: el precedente lo predijo textualmente

`DET-BACKFILL-TAINT-001` (04-08-2026, `PARKING-DETECTION.md` §2), sobre el mismo
`ParkingBackfillWorker` plantando **en el wake fix**:

> *"It landed right **by luck (short hole)**; over a 2 km hole it lands 2 km wrong with the same
> confidence."*

El 30-08 el hueco fue de **101 minutos y 3.652 m**, y el pin cayó ~3,6 km mal — con la misma
`reliability=0.5`. El guard que aquel ticket instaló (`EvaluateBackfillDeferralUseCase`) sólo cubre
*«el coordinator ya resolvió esta llegada como nudge»*: el 30-08 no había resolución previa, así que
no aplicaba. Se cerró la cara conocida del defecto y se dejó abierta la que el propio doc anunciaba.

**Lección para este ticket:** el problema nunca fue *quién más había decidido*, sino que **el
backfill acepta un fix suelto como prueba de reposo**. Ese es el invariante, y va arreglado en la
raíz — no con un tercer guard sobre el mismo síntoma.

## Doctrina violada

- **«El evento NOMINA, solo el movimiento MEDIDO confirma. Un evento re-entregado (Doze/OEM) nunca
  coloca un pin.»** Aquí un EXIT con `fixAge` de 101 min y un AR ENTER con `lag` de 101 min
  colocaron un pin **y publicaron una plaza**.
- **«Fallo asimétrico: mejor falso negativo que falso positivo.»** Ante un despertar tardío a 3,6 km
  del coche, con un único fix, la salida correcta era preguntar o no plantar — nunca plantar.

## Señales / datos disponibles

Ya las tenemos todas en el momento de decidir; no hace falta telemetría nueva:

- El fix del despertar (`lat/lon/speed/accuracy/timestamp`) — lo único que se usa hoy.
- El fix inmediatamente siguiente del stream: `loc#2` a **134 m en 5 s** reportando `0.0 m/s`, con
  envolventes de 6,9 + 6,8 m. Es exactamente el insumo de `DET-STOP-MUST-BE-STILL-IN-SPACE-001`.
- `d=3652m` (distancia al coche) y `fixAge` / `lag` de los eventos que dispararon el despacho.

## ⛔ Corrección del diagnóstico: el guard YA existe, y su premisa escrita es falsa

`EvaluateSafetyNetCheckUseCase.kt:327-331` ya tiene `DET-BACKFILL-CANNOT-PIN-A-MOVING-FIX-001`:

```kotlin
val backfillBounded = trustedStepsSinceAnchor != null &&
    trustedStepsSinceAnchor <= config.backfillMaxSteps &&
    fix.accuracy <= config.minGpsAccuracyForDriving &&
    fix.speed <= config.stoppedSpeedThresholdMps &&      // ← la cláusula "el coche está parado"
    arrivalWalkSteps != null
```

O sea: **no falta la comprobación — falla**. Su comentario (`:320-322`) dice literalmente:

> *"It is «is the car standing still HERE, where the pin is about to go?», and **only this fix's own
> speed measures that**."*

Esa premisa es **falsa, y ya se sabía que lo era**. El 30-08 el fix declaró `speed=0.0` circulando a
~37 km/h. Ocho días antes, `DET-STOP-MUST-BE-STILL-IN-SPACE-001` (22-08) había demostrado justo eso
en el stop tracker, y su propia ficha lo dice: *"The declared `speed` had already been distrusted for
the mute band by DET-CREDIBLE-DRIVE-001; the stop path never got the memo."*

**Es la tercera vez que el mismo error muerde en un sitio distinto**: confiar en el campo `speed`
declarado como prueba de reposo. El guard de 08-27 sólo cazó su caso porque aquel fix declaró
`2.1 m/s` honestamente.

## Diseño

**El invariante es: *el campo `speed` de un fix suelto no prueba reposo; el reposo se prueba
midiendo que el punto no se mueve en el espacio*.** Ya está escrito una vez, como función pura,
pública y `commonMain`: `physics/DriveCorroboration.kt:66` →
`isCorroboratedVehicleHop(prev, curr, hopMarginMeters, minRateMps)`. **Se reusa, no se duplica**, y
por [DET-VERDICT-NOT-PREDICATE-001] no se crea ningún `Evaluate*UseCase` nuevo.

⚠️ **Lo que NO vale: refutar contra el testigo anterior del safety net.** Ese testigo es de varios
minutos antes y está junto al coche viejo, así que en **todo** backfill —también en los legítimos—
el salto sale "corroborado como coche". Eso convertiría el FP en un falso negativo permanente.
Para medir *"¿está el coche quieto AHORA, aquí?"* hace falta un segundo fix **próximo en el tiempo**
al que va a llevar el pin. En la traza real esa medición existe y es inequívoca — pero llegó en la
sesión ARRIVAL_HANDOFF, 7 s tarde: `loc#1`→`loc#2` = **134 m en 5 s declarando 0,0 m/s**.

**Opción elegida (user, 31-08): ceder la llegada a la detección viva.** *Live detection MEASURES;
the backfill GUESSES. Cuando ambos están disponibles, gana la medición — siempre.*

La precedencia estaba **invertida**: un backfill acotado se adelantaba al handoff, y la detección
viva sólo arrancaba cuando la red no tenía nada que colocar (`ParkingSafetyNetWorker`, el antiguo
`val backfillChained = …; if (!backfillChained) { start() }`). Eso se lee como *«coloca siempre que
puedas»*, que es justo lo contrario de la doctrina.

Se invierte, y la decisión deja de ser folclore del worker: pasa a **función pura de `commonMain`**,
`domain/detection/ArrivalOwnership.kt` → `arrivalOwner(handoffStarted, departurePreconfirmed,
backfillBounded): ArrivalOwner` (`LiveDetection` / `Backfill` / `UserPrompt`). Patrón ya establecido
(`SentryLifecycleDecision.kt`, `HoldLifecycle.kt`); **no** es un `Evaluate*UseCase` nuevo
[DET-VERDICT-NOT-PREDICATE-001].

Ventaja decisiva: **no introduce ninguna calibración nueva** — ni umbral, ni segundo fix, ni reloj.
Se apoya en una medición que ya existía y que ya estaba llegando: el 30-08 el coordinator del
handoff refutó el fix mentiroso 7 s después de que el backfill lo hubiera usado.

**El backfill NO se borra.** Sigue siendo el backstop de su caso original: la detección viva no puede
arrancar (FGS en background denegado en Android 12+/OEM), o sea no queda nadie que pueda medir.
**Residual aceptado:** en esa rama un fix mentiroso todavía puede colocar mal un pin — acotado por
ser la última opción antes del prompt, y porque la alternativa es perder la llegada entera, que la
doctrina de fallo asimétrico considera peor que un pin con duda.

Telemetría nueva: `BACKFILL_CEDED_TO_HANDOFF` (`DetectionEvent.Decision`) + línea de log que dice
que había un backfill acotado en la mano y por qué se declinó — para que una traza distinga *«cedió
ante una medición»* de *«la red no tenía nada»*.

**Bug de plomería a arreglar en cualquier caso:** `ParkingBackfillWorker.buildRequest` (`:197-214`)
no serializa `fix.speed`, y `doWork` (`:84`) reconstruye el `GpsPoint` con **`speed = 0f`
hardcodeado**. El worker que planta el pin es, por construcción, incapaz de saber si el coche se
movía: cree que todo fix está parado.

> Fuera de alcance deliberado: el **doble despacho** de la misma salida
> (`[geofence-enter]` 21:27:33.974 y `[ar-enter]` 21:27:34.613, 596 ms de diferencia), por el que el
> veto `DET-DEPARTURE-IS-NOT-ARRIVAL-001` se evaluó **después** de que el pin ya estuviera puesto.
> Es un invariante distinto (dedupe de intake) → ticket propio
> `DET-A-DEPARTURE-DISPATCHES-ONCE-PER-FENCE-001`. Con este ticket el FP no ocurre aunque aquél siga
> abierto.

## Criterio de éxito

- Test que **reproduce la traza real del 30-08** (fix de despertar `speed=0.0 acc=6.6` + `loc#2` a
  134 m en 5 s) y comprueba que **no se planta pin**. Validado por falsación: neutralizar el guard
  debe ponerlo en rojo (si no, el test no está mirando lo que dice mirar).
- Un backfill legítimo (coche realmente quieto al despertar) **sigue plantando** — si no, esto es un
  falso negativo nuevo, no un arreglo.
- Suite completa verde.

## Consumidores auditados

**a) ¿Quién más puede colocar un pin de backfill?** `ParkingBackfillWorker.buildRequest` tiene **un
único call site** en todo el repo: `ParkingSafetyNetWorker:528` — el que se cambia. **Cerrado.**

**b) ¿Quién más arranca el handoff?** `arrivalHandoffDetection.start()`: un único call site de
producción (`ParkingSafetyNetWorker:482`). El resto son DI, `FakeArrivalHandoffDetection` (mock/iOS)
y el puerto. **Cerrado.**

**c) ¿Quién produce/consume `backfillBounded`?** Producido en `EvaluateSafetyNetCheckUseCase:327` y
propagado a las 5 construcciones de `DispatchDeparture` (`:365 :403 :440 :467 :479`); consumido
**solo** en el worker. **Cubierto por convergencia** — todas las ramas pasan por el mismo `when`.

**d) ¿Quién más trata el `speed` declarado de un fix suelto como prueba de reposo?**
`grep stoppedSpeedThresholdMps` da tres consumidores de decisión:
- `EvaluateSafetyNetCheckUseCase:330` — el de este ticket. **Cubierto**: la cláusula se queda (no
  estorba, y sigue cazando el caso honesto del 27-08), pero ya no es la única defensa, porque su
  rama sólo se alcanza cuando nadie puede medir.
- `StopTracking.kt:86` — **exento con razón**: es precisamente el sitio que ya fue blindado por
  `DET-STOP-MUST-BE-STILL-IN-SPACE-001`; refuta contra el origen espacial del propio stop.
- `EvaluateBtParkUseCase.kt:136` — **exento con razón**: carril Bluetooth, determinista. Su reposo no
  licencia una posición por sí solo; exige además el disconnect de la MAC y ≥30 m de alejamiento.

**e) Doble red que se conserva:** `ParkingBackfillWorker` mantiene su guard `isRunning`
[DET-ARRIVAL-DOUBLE-PIN-001]. Con el handoff arrancando ANTES, ese guard cubre además la carrera
inversa. No se toca.

**f) Fuera de alcance, con ticket propio:** el **doble despacho** de la misma salida —
`DET-A-DEPARTURE-DISPATCHES-ONCE-PER-FENCE-001`. Sigue abierto; este ticket hace que deje de
producir un pin fantasma, no que deje de ocurrir.

## Bug de plomería, arreglado de paso

`ParkingBackfillWorker.buildRequest` no serializaba `fix.speed` y `doWork` reconstruía el `GpsPoint`
con **`speed = 0f` hardcodeado**: el worker que planta el pin creía que **todo** fix que recibía
estaba parado — un falso reposo por construcción. Ahora el speed viaja (`KEY_SPEED`).

Y **no lleva default**, por lo que `DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001` (`c604a058`,
entró en master mientras se hacía este ticket) acababa de dejar escrito: *un default es una respuesta
silenciosa permanente*, y `0f` es la permisiva — responde «parado» por un fix que nadie midió. La
única población que puede llegar ahí es una petición encolada por la build ANTERIOR que sobreviva a
la actualización, y para ella la respuesta honesta no es adivinar: **sin speed, no se planta**. Un
backfill perdido en la ventana de una actualización cuesta un nudge; un pin fantasma cuesta una plaza
fantasma.

## Estado de verificación

- ✅ `:shared:testDebugUnitTest` → **1.990 tests verdes** (1.985 de base + 5 nuevos).
- ✅ **Falsación hecha**: invertidas las dos primeras ramas de `arrivalOwner` (bug reintroducido),
  `should_giveArrivalToLiveDetection_when_handoffStartedAndBackfillAlsoBounded` y
  `should_alwaysGiveArrivalToLiveDetection_when_handoffStarted` se ponen **ROJOS**. El test mira lo
  que dice mirar.
- ✅ `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin`.
- ⏳ **Sin validar en campo.** No hay pantalla, estado MVI ni condición de routing nuevos → el Dev
  Catalog no necesita entrada (sí hay telemetría nueva, que no es superficie de UI).
- ⏳ Sin strings nuevos → no toca los 9 locales.
