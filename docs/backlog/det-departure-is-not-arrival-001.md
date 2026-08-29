# DET-DEPARTURE-IS-NOT-ARRIVAL-001 · la prueba de que te fuiste no es la prueba de que has llegado

**Estado:** ✅ Done · mergeado a master 2026-08-25 (squash) · implementado en
`bugfix/DET-DEPARTURE-IS-NOT-ARRIVAL-001-backfill-needs-arrival`, abierto sobre `a2f2bff9` y
rebasado sobre el refactor de física antes de cerrar. ⏳ **Sin validar en campo** — sólo el Xiaomi
de la novia (heartbeat exacto vivo) puede ejercitar esta vía; el pin falso `850807ff` sigue activo
y hay que borrarlo antes de medir.

## Problema

Field 2026-08-24, **Xiaomi 23117RA68G** (uid `12ck5eNWl2ONpMLN05e8jUrXER33`, Land Rover Range Rover
simulado — el coche físico es el Ford Focus de siempre). Reporte del user: *«un falso positivo […]
es el semáforo de hospital, que pensaba que ese FP lo teníamos más que mirado, pero parece que en
este dispositivo nuevo funciona distinto»*.

⚠️ Ese uid **no tiene documento en `diagnostics_config`** → no sube nada a Firestore. Todo esto sale
del `parkdiag.log` local del propio móvil (`adb exec-out run-as com.rndeveloper.paparcar cat
files/parkdiag.log`).

```
19:26:58  FenceEnter: ✓ re-entered own fence (f5e5c6e5) — steps@anchor=43658, d=59m
19:29:13  ARReceiver: → IN_VEHICLE ENTER  ✓ bus stamped (trueTime=1787592552913, lag=245ms)
19:29:16  Service:    ⊘ AR ENTER not armable (TickOnly, lag=269ms) — evaluator's call
19:29:16  StepCounter: cumulative steps read → 43755          ← 97 pasos desde el ancla
          ················ 5 min y 2.976 m de coche ················
19:34:16  ExactNet:   ⏰ exact heartbeat fired (doze stretch: 7 ms)
19:34:20  OneFix:     fix 36.5983975,-6.2369172 speed=0.0m/s acc=17.357m
19:34:20  StepCounter: cumulative steps read → 43755          ← EL MISMO NÚMERO. Cero pasos.
19:34:20  SafetyNet:  ▶ far with vehicle evidence — dispatching departure (preconfirmed=true steps=97 d=2976m)
19:34:20  SafetyNet:    → chaining parking backfill at wake-up fix (steps=97 acc=17.357)
19:34:20  Backfill:   ✓ backfilled parking at 36.5983975,-6.2369172 (reliability=0.5) session=850807ff
```

Pin resultante: **`850807ff`** · `detectionPath=safety_net_backfill` · rel 0.5 · Calle Valdés 42 ·
**sigue `isActive=true`**. El coche no estaba ahí: iba en marcha, parado en un semáforo. El destino
real estaba **~450 m más adelante** (36.5963,-6.2323, Plaza de Elías Ahuja — el hospital), donde el
Oppo que iba en el mismo coche puso su pin a las 19:39 vía `closed_approximate_zone`, correcto.

### Por qué el móvil nuevo "funciona distinto"

El Xiaomi tiene el **heartbeat exacto vivo**: 217 disparos ese día, `doze stretch` de 6-9 ms. En el
Oppo lleva **muerto** desde [DET-HEARTBEAT-MISS-IS-EVIDENCE-001] y en el Redmi tampoco se ve. La red
de seguridad de 15 min **nunca llegaba a correr a mitad de viaje** en nuestros dos móviles de campo.

El FP del semáforo no estaba arreglado: estaba **invisible**. Lo que sí arreglamos
([DET-STEP-SPEED-GATE-001], FP de Avenida de los Mástiles, 2026-07-12) vive sólo en
`EvaluateParkingDecisionUseCase` (`isRolling` veta TODA vía de auto-confirm) — la vía del safety-net
nunca lo recibió. Es el corolario de siempre: cerrar la vía donde mordió no es cerrar el invariante.

## Doctrina violada

*El evento NOMINA, sólo el movimiento MEDIDO confirma.* Aquí ni siquiera hay nominación de llegada:
**no existe un solo dato que diga que el coche paró en ese punto**. Un fix con `speed=0.0` es
exactamente lo que produce un semáforo.

Y el fallo asimétrico: ante la duda se PREGUNTA. La red ya tenía la salida honesta a mano —
[DET-ARRIVAL-HANDOFF-001] exige que una salida despachada acabe en *backfill* **o** en detección
viva **o** en el prompt de "¿sigues aparcado?". Prefirió adivinar.

## La causa raíz, en una frase

**El mismo número responde a dos preguntas opuestas.** `trustedStepsSinceAnchor = 97` se usa para:

- **soltar** la plaza vieja — `!walkExplainsDisplacement(97, 2976 m)` ⇒ *«esos pasos NO caminan esta
  distancia, luego el cuerpo rodó»*. **Correcto.**
- **acotar** la plaza nueva — `backfillBounded = 97 ≤ backfillMaxSteps(150)` ⇒ *«el cuerpo está como
  mucho a 97 × 0,75 m = 73 m del coche recién aparcado»*. **Imposible**: acaba de establecerse que
  esos 97 pasos se gastaron en el desplazamiento, no *después* de él.

Las dos ramas no pueden ser honestas a la vez. En cuanto se entra por `!walked`, el presupuesto de
pasos **está gastado** y por construcción ya no puede acotar nada. En este viaje los 97 pasos fueron
la caminata **hacia** el coche (ancla 19:26:58 → embarque 19:29:13) y entre el embarque y el backfill
el contador acumulado no se movió **ni un paso**: cero pasos en 2.976 m es la firma exacta de ir
montado, y es justo lo contrario de haber aparcado y bajado.

Encima había una prueba positiva descartada: el **IN_VEHICLE ENTER de 19:29:13**, admisible
(post-sesión) y sin retirar. `lastVehicleEnteredAtMs` ya es parámetro del evaluador — pero sólo se
lee como evidencia *a favor* de liberar, nunca como veto de la **colocación**.

## Señales / datos disponibles

- `EvaluateSafetyNetCheckUseCase` ya recibe `stepsSinceAnchor`, `lastVehicleEnteredAtMs`,
  `lastSeenNearCarAtMs`, `fix` (con `speed` y `accuracy`) y `nowMs`.
- El worker mantiene ya un mecanismo de *baseline* del contador acumulado (el sellado del ancla,
  `steps@anchor`) — la infraestructura para un segundo baseline existe.
- `config.backfillMaxSteps = 150`, `maxBoardingSteps = 300`, `minGpsAccuracyForDriving = 50 m`.

## Diseño

**El invariante, en el evaluador puro y en un solo sitio:**

> Un presupuesto de pasos que ya se ha gastado en PROBAR el trayecto no puede además ACOTAR la
> llegada. Colocar una posición exige testigo de llegada propio; sin él, se libera la plaza vieja y
> la llegada se delega — nunca se adivina.

`EvaluateSafetyNetCheckUseCase:223` — `backfillBounded` deja de ser sólo *"pocos pasos + fix
decente"* y pasa a exigir **evidencia de llegada independiente del presupuesto que probó la salida**:

1. Entrada nueva `stepsSinceLastWitness: Long?` — delta del contador acumulado desde la **última
   observación independiente del cuerpo**, no desde el ancla. Se apoya en el slot de testigo que
   [DET-UNWITNESSED-DISPLACEMENT-001] ya mantiene (`KEY_LAST_WITNESSED_POS/ACC/AT`), al que se le
   añade un cuarto campo `KEY_LAST_WITNESSED_STEPS`. El mismo testigo que acota **dónde** estaba el
   cuerpo acota ahora **cuánto ha andado desde entonces**. Se elige este baseline y no el del
   embarque porque no depende del AR: funciona igual en un móvil con Activity Recognition mudo.
2. Posición y muestra de pasos son **un solo sello**, escrito por los dos writers que ya existían
   (el tick del safety-net y `CoordinatorDetectionService.stampLastWitnessedFix()`). Contador
   ilegible → se **borra** el slot en vez de dejar el valor viejo: una posición fresca emparejada
   con un conteo rancio inventaría una caminata que no ocurrió.
3. `backfillBounded` exige, además de lo de hoy, `stepsSinceLastWitness` presente, **> 0** y dentro
   de `backfillMaxSteps`. El caso canónico para el que se construyó el backfill (2026-07-06, Oppo,
   10 pasos ≈ 8 m, con el proceso dormido todo el viaje) lo cumple de sobra; el semáforo da 0.
4. Sin ese testigo → `backfillBounded = false`. La salida se despacha igual (la plaza vieja se
   libera, que es lo correcto) y la rama `else` que [DET-ARRIVAL-HANDOFF-001] **ya tenía** enruta a
   detección viva o, si el arranque del FGS se deniega, al prompt de "¿sigues aparcado?".
5. El rechazo se estampa en la traza como `BACKFILL_ARRIVAL_UNWITNESSED` sobre el path
   `safety_net_backfill` — sin eso, "la red se negó a adivinar" es indistinguible de "la red no
   corrió".

**Lo que NO se hace:** meter un `isRolling` en el worker mirando `fix.speed`. Es el parche que
parece obvio y no cubre el caso: el fix del semáforo marcaba **0,0 m/s**. La velocidad instantánea
no distingue un semáforo de un aparcamiento; los pasos posteriores sí.

## Criterio de éxito

- ✅ Test réplica del semáforo (`should_releaseButNotBoundBackfill_when_noStepWasWalkedSinceTheLastWitness`):
  ancla + 97 pasos gastados antes del embarque, **0** desde el último testigo, fix `speed=0`
  `acc=17,36 m` a 2.976 m → `DispatchDeparture(preconfirmed=true, backfillBounded=false)`. Se
  asertan **las dos mitades**: que la salida sigue siendo correcta y que la colocación se niega.
- ✅ Test del caso legítimo (2026-07-06, Oppo): 10 pasos de ancla **y** 10 desde el testigo →
  `backfillBounded = true`, se sigue colocando.
- ✅ Test presupuesto de llegada desconocido (`null`) → `backfillBounded = false`.
- ✅ Test caminata de llegada por encima del cap → `backfillBounded = false`.
- ✅ **Verificado neutralizando el guard**: quitando `arrivalWalkSteps != null` las TRES aserciones
  nuevas se ponen rojas. No son tests verdes que no demuestran nada.
- ⏳ Campo: repetir el trayecto Cádiz → hospital con el Xiaomi y comprobar que en el semáforo **no**
  aparece pin y que la llegada la coloca la sesión viva.

## Estado de la implementación

| Fichero | Cambio |
|---|---|
| `domain/usecase/parking/EvaluateSafetyNetCheckUseCase.kt` | param `stepsSinceLastWitness`; `arrivalWalkSteps` gatea `backfillBounded` |
| `detection/worker/ParkingSafetyNetWorker.kt` | lee el delta ANTES de resellar; estampa `KEY_LAST_WITNESSED_STEPS`; traza del rechazo |
| `detection/service/CoordinatorDetectionService.kt` | `stampLastWitnessedFix()` pasa a `suspend` y sella también los pasos |
| `commonTest/.../EvaluateSafetyNetCheckUseCaseTest.kt` | 3 tests nuevos + el existente pasa a exigir caminata de llegada |
| `docs/detection/PARKING-DETECTION.md` | entrada de sección 2 |

**Suite: 1.503 tests, 0 fallos.**

## Consumidores auditados

Barrido de *todo lo que puede COLOCAR una posición de llegada*:

| Sitio | Qué usa para colocar | Clasificación |
|---|---|---|
| `EvaluateSafetyNetCheckUseCase` → `ParkingBackfillWorker` | `stepsSinceAnchor` como cota | ⛔ **donde mordió** — se cierra |
| `EvaluateHonestCloseUseCase.artifactFor` | **el mismo patrón**: `stepsSinceStalePin × stride` como radio de duda | ⚠️ **a clasificar en este ticket** — mitiga (dibuja ZONA, no punto, y corre sobre una sesión viva que acaba de observar el móvil), pero la conflación es idéntica. El 24-08 acertó por suerte de timing |
| `EvaluateParkingDecisionUseCase` (fase candidate) | `isRolling` + egress + `sessionSawDriving` | ✅ ya cerrado por [DET-STEP-SPEED-GATE-001] |
| Guardado por *unattended timeout* | zona con radio propio, sobre sesión viva | ✅ cubierto por convergencia |
| Nudge / prompt "marca tu plaza" | no coloca: pide | ✅ exento con razón |
| `BluetoothDetectionStrategy` | fix tras desconexión + ≥30 m medidos | ✅ exento con razón (carril determinista) |
| `SaveManualParkingUseCase` | posición elegida por el usuario | ✅ exento con razón |

## Provenance / telemetría

No hay camino nuevo de confirmación → **ningún valor nuevo de `detectionPath`**. Sí hace falta que
la traza diga por qué NO se colocó: el `DetectionEvent.Decision` del worker debe emitir el motivo
(`arrival_unwitnessed`) junto al `PATH_SAFETY_NET_BACKFILL`, igual que ya hace
`BACKFILL_DEFERRED_TO_NUDGE` en [DET-BACKFILL-TAINT-001]. Sin eso el no-pin es indistinguible de un
worker que no corrió.

## Acción inmediata para el user

El pin falso `850807ff` **sigue activo** en el móvil de tu novia. Conviene borrarlo antes del
siguiente field-test o contamina el ancla de la próxima sesión.

## Seguimiento aparte

El heartbeat exacto muerto en Oppo/Redmi ([DET-HEARTBEAT-MISS-IS-EVIDENCE-001], señal
`exactHeartbeatLaneDead: true`) queda como está — pero este incidente demuestra que **mientras siga
muerto, toda la rama del safety-net está sin probar en campo** en los dos móviles principales. El
Xiaomi pasa a ser el único banco de pruebas real de esa vía.

## Rebase

2026-08-25 · rama puesta al día sobre master `46b83012` (a2f2bff9 → 3377e78d · cfe2e025 ·
46b83012, las tres del refactor de física). Sin conflictos, pese a que `3377e78d`
[DET-PHYSICS-FENCE-CONTAINMENT-001] toca el mismo `EvaluateSafetyNetCheckUseCase.kt`: fusionó los
chequeos de contención, que están por encima del presupuesto de llegada. Verde: 1.525 tests,
0 fallos, `compileMockDebug` + `compileProdDebug`.
