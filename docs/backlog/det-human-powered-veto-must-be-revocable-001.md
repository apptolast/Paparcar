# DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001 · el veto de tracción humana tiene que poder levantarse con lo que la sesión ya ha medido

**Estado:** ✅ Done · master `c322d8c1` (27-08) · rama y worktree eliminados

## Problema

Field 2026-08-26, Redmi (`WZB7oftWLDY1toGJrDwoRHnnYHx2`, Citroën C5 Aircross, Coordinator puro).
Dos viajes en coche la misma noche, Góndola 1 → Calle Valdés 19 → Góndola 1. El primero se guardó;
**el segundo se perdió**. El Oppo, en el mismo coche y los mismos dos trayectos, guardó los dos por
`steps+egress` con fiabilidad 0,9 (`a53e43c5` 19:27:31 · `e1cb2b34` 20:44:50).

Provenance de los cuatro pines implicados:

| Hora | Móvil | Sesión | armEvidence | detectionPath | outcome |
|---|---|---|---|---|---|
| 19:09→19:27 | Redmi | `73f2781f` | `self_observed` + AR ENTER + GEOFENCE_EXIT | `steps+egress` | confirmado 19:27:37 |
| 19:10→19:27 | Oppo | `a53e43c5` | `enter_at_car` | `steps+egress` | confirmado 19:27:31 |
| **20:19→20:58** | **Redmi** | **nunca nació** | `self_observed` + GEOFENCE_EXIT + AR ENTER | `steps+egress` **degradado a prompt** | **`Ask(HUMAN_POWERED)` a los 901 s** |
| 20:19→20:44 | Oppo | `e1cb2b34` | `self_observed` (GEOFENCE_EXIT) | `steps+egress` | confirmado 20:44:50 |

Ninguna vía BT esta noche: el único `BT DISCONNECTED` del Oppo fue de unos auriculares
(`00:A4:1C:65:2B:3F`, sin vehículo emparejado). **Los cuatro trayectos son Coordinator**, así que la
comparación Redmi/Oppo es limpia: misma estrategia, mismo coche, mismo asfalto.

La sesión perdida del Redmi tuvo TODO lo que hace falta para confirmar:

```
20:41:42  llegada real a Góndola 1 (36.6087, -6.2781)
20:42:09  ⚓ anchor FROZEN — drive-entered stop matured (stableFixes=3, walkFixes=1)
20:42:14  ✦ step #1..#32 — egress: el user se baja y anda
20:42:23  ⊘ steps+egress fast confirm gated (Inconclusive) — anchorSet=true, falling to scoring
20:42:54  ▶ weak-evidence prompt POSTED (score=0.6)
20:57:55  ⑊ no user response after 901315ms → Ask(reason=HUMAN_POWERED) [maxSpeed=26.2m/s …]
```

Lo único que lo bloqueó fue este veredicto, puesto **19 minutos antes**, saliendo de Valdés:

```
20:22:11  ♲ pedal cadence — 12 steps concurrent with 3 above-ceiling fixes
          → human-powered ride, automatic saves degrade to a prompt [DET-MOTOR-PROOF-001]
```

Es un **falso positivo del detector de bicicleta sobre un trayecto en coche**. Los fixes que lo
alimentaron iban a 3,52 · 4,02 · 3,13 · 4,65 · 3,29 · 3,67 m/s — **11 a 17 km/h, coche por el centro
de Cádiz**. El podómetro del Redmi no se calla en el coche: `pedal cadence` aparece **8 veces** en su
log y **en los 2 viajes en coche de esa noche**; en el del Oppo aparece **1 vez** en total.

## Doctrina violada

`HumanPoweredRide.kt:75` lo dice con todas las letras y no lo cumple:

> *"MEASURED MOTOR REFUTES EVERYTHING BELOW … the measurement gets the last word, wherever the claim
> came from — the AR stamp below AND the cadence latch above it."*

Hoy la refutación no llega, por dos razones independientes:

1. **La única puerta de salida exige continuidad de muestreo.** `HumanPoweredRide.kt:91` levanta el
   veto sólo con `sustainedMotorBandMs >= sustainedDriveProofMs` (30 s acumulados por encima de
   11,1 m/s), y `SpeedBandClock.creditSpeedBand` sólo acredita huecos ≤ `driveProofWindowMaxMs`
   (**60 s**). En el viaje perdido el stream se troceó a batching OEM: los fixes en banda motora
   quedaron en **20:33:55 (14,97 m/s) · 20:36:39 (26,2 m/s) · 20:39:58 (13,42 m/s)**, separados
   **163 s y 200 s**. Sin peer dentro de la ventana el reloj acreditó ~1 s de los 30 s exigidos y
   **`MOTOR witnessed` nunca se escribió** — pese a haber medido **94,3 km/h con 15,5 m de
   precisión**. El Oppo, con GPS a 5 s y 2-3 m, escribió `MOTOR witnessed — 37412ms` en el primer
   viaje sin despeinarse.
2. **El embarque presenciado no cuenta.** La rama de cadencia (`:96-100`) devuelve `true` **antes**
   de leer `vehicleRideAtMs`, así que el arbitraje *"la última subida a un vehículo manda"* que la
   rama AR sí aplica (`:113`) es **inalcanzable** cuando habla la cadencia. En el viaje perdido había
   un `IN_VEHICLE ENTER` **verdadero** con `trueTime=20:20:12` — **1 min 49 s ANTES** de que la
   cadencia se disparara — y cero `ON_BICYCLE` en toda la ventana. El clasificador de Android había
   contestado "vas en un vehículo" y el veredicto no lo miró.

El propio fichero declara el punto 2 como coste asumido (`:41-43`, *"a bike→car trip in one session
keeps the cadence latch"*), pero **el caso de campo es el contrario**: no es bici→coche, es coche
desde el primer metro, con el embarque estampado y sin una sola etiqueta de bici.

Y hay una razón de doctrina para no dejarlo así: *fallo asimétrico* dice que ante la duda se
PREGUNTA en vez de plantar un pin fantasma — pero **el prompt sin contestar también pierde la plaza**.
Un veto irrevocable no es "preguntar ante la duda", es perder la plaza por defecto en cuanto el
podómetro tose.

## El caso que este ticket NO puede cerrar solo, y por qué importa

Reporte del user (26-08): *"he ido en coche por el centro de mi ciudad… aquí hay varias zonas donde
en coche los 30 km/h se cogen fácil… y si no hubiese salido del centro?"*.

Correcto, y es la pregunta que ordena el diseño: **si el viaje se hubiese quedado entero en el centro
a 30 km/h, la puerta de la banda motora (11,1 m/s = 40 km/h) no se abre nunca**, por muy sano que
venga el GPS. Por eso la refutación no puede depender de correr rápido. Tiene que apoyarse en lo que
la sesión ya sabe de sobra: **que el user subió a un vehículo**.

De ahí que la puerta A de abajo sea la principal y la B la red de seguridad, y no al revés.

## Señales / datos disponibles — todo esto YA se mide, no hay que instrumentar nada

| Señal | Dónde vive | Valor en el viaje perdido |
|---|---|---|
| `egress.vehicleRideAtMs` | `EgressEvidence:75`, estampado por AR `IN_VEHICLE` ENTER/EXIT | **20:20:12** (verdadero) |
| `egress.bicycleRideAtMs` | ídem, AR `ON_BICYCLE` ENTER | **null** en toda la ventana |
| `SustainedDeparture(distanceMeters, rateMps)` | `DriveCorroboration.kt:100`, ya se calcula por fix | **226 m @ 19,7 · 599 m @ 23,4 · 640 m @ 24,6 · 275 m @ 34,4 m/s** |
| `drive.motorBandMs` | `DriveProof:86` | **~1 s** (necesita 30 000) |
| `drive.provenMaxSpeedMps` | `DriveProof:79` | 26,2 m/s |

`SustainedDeparture` ya se escribe en el `parkdiag` como `⇢ SUSTAINED DEPARTURE`; hoy **se usa y se
tira**, no queda banqueada en el estado.

## Diseño

El invariante vive en **un solo sitio**: `domain/detection/HumanPoweredRide.kt::isHumanPoweredRide`,
que ya es la única función que contesta *"¿esto lo movió un músculo?"* y a la que llegan los tres
consumidores por el envoltorio `DetectionSessionState.humanPoweredRide` (`StageInputs.kt:146`).
**No se toca ningún consumidor** y **no se añade ningún umbral nuevo**: las dos puertas se abren con
evidencia que la sesión ya produce.

Nótese que `isHumanPoweredRide` **ya se re-evalúa en cada fix** — el efecto "pestillo" no es un
booleano guardado, es que su entrada (`fastMotionStepEvents`) es un contador monótono que sólo crece
(`EgressEvidence:147`). Por eso la revocación no necesita maquinaria nueva: basta con que la función
consulte, en cada llamada, evidencia que también crece.

### Puerta A — un embarque presenciado deja sin voz a la cadencia *(la que cierra el caso del centro)*

La regla de cadencia tiene un charter explícito en su propia documentación (`:33-36`): existe **para
los trayectos cortos que AR nunca clasifica** — el caso de campo que la creó (18-08, bici de 6 min)
produjo **CERO eventos AR entre los 316 de la sesión**. O sea: **la cadencia habla cuando AR calla.**

Cuando AR sí ha hablado y ha dicho `IN_VEHICLE`, y no hay ninguna etiqueta `ON_BICYCLE` que la
suceda, la cadencia no tiene standing. La rama pasa a consultar el mismo arbitraje que la rama AR ya
usa, en vez de saltárselo. Cero números nuevos, y el caso Los Toruños / bici-de-6-min queda intacto
por construcción: allí `vehicleRideAtMs` es `null`.

### Puerta B — motor medido por DESPLAZAMIENTO *(red de seguridad para el stream troceado)*

`sustainedMotorBandMs` mide *tiempo en banda* y por eso muere con el batching. La tasa por
desplazamiento no: se calcula entre dos extremos y un delta de tiempo, así que **un hueco de GPS no
la borra, la promedia**. Se banquea en `DriveProof` la mayor `SustainedDeparture.rateMps` creíble de
la sesión y se acepta como segunda vía de refutación motora.

⚠️ Esto **no** es "usar el pico". Un pico es una muestra y una muestra es un espejismo — ya lo refutó
`DET-ASSERTION-OUTRANKS-INFERENCE-001`. `sustainedDepartureFromAnchor` exige una línea base de
`sustainedDepartureFloorMeters` (**150 m**) más ambas envolventes de precisión, y descarta por arriba
con `sustainedDepartureMaxRateMps` (55 m/s) el teletransporte de caché. 640 m a 24,6 m/s de media no
es una muestra: es terreno cubierto.

### Lo que este ticket deliberadamente NO hace

No toca los umbrales del detector de cadencia (`pedalCadenceMinStepEvents` = 12,
`pedalCadenceMinFixes` = 2, `egressStepMaxSpeedMps` = 3,0 m/s). Ese es el **origen** del falso
positivo y es calibración: cualquier número elegido hoy se elige con los datos de una noche, y
apretar de más devuelve el pin del coche en la playa de Los Toruños. Va en
`DET-PEDAL-CADENCE-CANNOT-CONVICT-A-CAR-IN-TRAFFIC-001`, bloqueado por medición.

## Criterio de éxito

1. **Replay del viaje perdido** (`Trace_*` con el stream real del Redmi 20:19→20:58): la sesión
   confirma por `steps+egress` alrededor de 20:44 en `36.6087,-6.2781`, en vez de degradar a prompt.
2. Cada aserción se verifica **neutralizando su guard** y comprobando que se pone roja — el método de
   `DET-2208-TRIPS-BECOME-REPLAYS-001`. Una aserción que afirma más de lo que demuestra es un bug con
   forma de test verde.
3. **Puerta A aislada**: sesión con `IN_VEHICLE ENTER` + cadencia posterior + sin `ON_BICYCLE` y
   **sin un solo fix por encima de 40 km/h** → no hay veto. Es el caso "todo el viaje por el centro".
4. **No regresión de la bici**: sesión sin ningún evento AR + cadencia → sigue vetada (18-08). Sesión
   con `ON_BICYCLE` posterior al `IN_VEHICLE` → sigue vetada (arbitraje de última subida).
5. Campo: un trayecto en coche por el centro sin pasar de 50 km/h que termine en pin automático.

## Consumidores auditados

`grep -rn "humanPoweredRide\|isHumanPoweredRide\|CloseHumanPowered" composeApp/src`

| Sitio | Qué hace | Clasificación |
|---|---|---|
| `stages/StageInputs.kt:146` | envoltorio único `DetectionSessionState.humanPoweredRide` | **cerrado** — es el punto único de entrada |
| `stages/StageInputs.kt:75` · `:122` | alimenta a los dos evaluadores | **cubierto por convergencia** |
| `stages/ConfidenceScoringStage.kt:124` | `Skip` → degradar a prompt (la vía donde mordió) | **cerrado** |
| `usecase/parking/EvaluateParkingDecisionUseCase.kt:297-299` · `:321` · `:372` | `PromptReason.HUMAN_POWERED` y `CloseHumanPowered` | **cubierto por convergencia** |
| `usecase/parking/EvaluateUnattendedParkingSaveUseCase.kt:133` | el timeout de 15 min lee la misma señal | **cubierto por convergencia** |
| `stages/CandidateStage.kt:105`,`:111` · `stages/SessionStage.kt:363` · `DetectionEffectDispatcher.kt:221` · `DetectionEffectExecutor.kt:342` | lado efecto de `CloseHumanPowered` | **cubierto por convergencia** — sólo corren si el evaluador lo dice |
| `CoordinatorParkingDetector.kt:1117` `onHumanPoweredRide` | estampa `bicycleRideAtMs` desde AR `ON_BICYCLE` | **exento** — es entrada del evaluador, no consumidor del veredicto |
| `AssertedPinAuthority.kt:13` · `physics/SavedParkingShape.kt:28` | sólo referencias en KDoc | **exento** |

⚠️ La estrategia **Bluetooth no consume esta señal por construcción** (una bici no lleva MAC): los
dos carriles siguen separados y este cambio no los mezcla.

## Estado de ejecución

- [x] **Puerta A** — la cadencia cae a la arbitración en vez de retornar, cuando AR presenció el
      embarque (`HumanPoweredRide.kt`). Cero umbrales nuevos.
- [x] **Puerta B** — `DriveProof.motorDisplacementRateMps` banca la mayor tasa sostenida; la medición
      la calcula **una sola vez** el `updateStopTracking` y sale por `StopTracking.sustainedDeparture`
      hasta `DriveProof.onFix`, igual que ya salían la duración parada y las notas.
- [x] **Tests: 1.670 verdes, 0 fallos** (master venía de 1.657). 8 tests nuevos + 1 invertido.
- [x] **Los dos guards verificados EN ROJO** neutralizando cada uno por separado:
      - anulando la puerta B caen exactamente `should_notVeto_when_theMotorWasProvenByDisplacement…`
        y `should_notVeto_when_displacementRefutesAnArCyclingStampToo`;
      - anulando la puerta A caen exactamente `should_notVeto_when_aWitnessedBoardingOutranks…`
        y `should_notVeto_when_cadenceFiredOnACityDriveArHadAlreadyWitnessed`.
      Ninguna neutralización tumbó tests de la otra puerta → son testigos independientes.
- [x] `compileMockDebugKotlinAndroid` + `compileProdDebugKotlinAndroid` verdes, sin warnings.
- [x] Entrada en `docs/detection/PARKING-DETECTION.md` §2.
- [x] Sin strings nuevos, sin pantallas ni estados nuevos → no toca los 9 locales ni el Dev Catalog.
      El texto del prompt no cambia; lo que cambia es **cuántas veces se muestra**.
- [x] `detectionPath` no cambia: el camino que se recupera es `steps+egress`, que ya existe.
- [x] Diagnóstico nuevo en las dos vías: línea `✓ MOTOR witnessed by displacement` en `parkdiag` y
      evento remoto `MOTOR_WITNESSED_BY_DISPLACEMENT`.

### ⚠️ Un assert existente invertido a propósito

`should_stillVeto_when_aLaterBoardingSupersededArButCadenceWasMeasured` → renombrado a
`should_notVeto_when_aWitnessedBoardingOutranksTheMeasuredCadence` y su aserción pasa de `true` a
`false`. **No es daño colateral: es el ticket.** Ese test codificaba el *"known cost, accepted"* de
`HumanPoweredRide.kt:41-43` (*"one tap, the direction asymmetric failure allows"*), y el 26-08 puso
precio a ese tap — nadie tocó, y la plaza se perdió. El test nuevo lleva dentro la evidencia de campo
que justifica el cambio de signo. **Ningún otro assert cambió de signo.**

## Replay del viaje perdido — ✅ hecho, y **reproduce el falso negativo**

`Trace_Gondola2608CadenceVeto.kt`: **341 fixes** (numeración `loc#1`…`loc#341` sin hueco) + **165
pasos registrados**, el stream real del Redmi de 20:19:08 a 20:58:00. Base de epoch
`08-26 00:00:00.000 = 1_787_695_200_000`, verificada contra **dos relojes independientes del mismo
log**: el `trueTime` del AR cae en 20:20:12.462 (su entrega 20:20:22.933 menos el `lag=10471ms` que
la línea declara) y `stoppedSince` cae en 20:46:40.414, cuadrando con el `carRest` de salida.

Matriz de neutralización — **cada puerta anulada por separado en el código de producción**:

| | Puerta A | Puerta B | test 1 (con embarque) | test 2 (sin embarque) |
|---|---|---|---|---|
| fix completo | ✓ | ✓ | pasa | pasa |
| sólo A anulada | ✗ | ✓ | pasa (la sostiene B) | pasa (la sostiene B) |
| sólo B anulada | ✓ | ✗ | **pasa con el embarque SOLO** | cae |
| ambas anuladas | ✗ | ✗ | **cae** | **cae** |

La última fila es la que da valor al replay: con las dos puertas fuera, la traza **reproduce el
falso negativo de campo**. Y la tercera desmiente una cautela que yo había escrito en el test: la
puerta A sí sostiene este stream real ella sola.

### ⚠️ 12 eventos reconstruidos, y por qué no había alternativa

La primera versión de la traza **pasaba también sin el fix**. Causa: los pasos que activan la
cadencia **no existen en ningún diagnóstico**. `✦ step #N` sólo se emite en tres ramas (pre-drive ·
parado · ancla puesta) y un paso dado conduciendo con el ancla limpiada no cae en ninguna — que es
exactamente la forma de un paso de cadencia.

El silencio del log lo corrobora: entre el seed de conducción (Δ123,9 s) y el latch (Δ183,4 s) no hay
ni una línea `✦ step`, mientras la línea resumen de ese mismo instante declara *"12 steps concurrent
with 3 above-ceiling fixes"*. Sólo son ciertas a la vez si los doce tomaron la rama muda.

Reconstruidos por aritmética, no por gusto: 4 eventos contra cada uno de los 3 últimos fixes en banda
3,0-11,1 m/s antes del latch (Δ173 512 · Δ178 317 · Δ183 360), cada uno dentro de los 10 s de
`pedalCadenceFixFreshnessMs`, el duodécimo en Δ183 385 — el milisegundo exacto en que el móvil
imprimió `♲ pedal cadence`. Doce eventos, tres fixes distintos: las dos cifras que el propio aparato
reportó. No pueden inflar nada más: sin `driveAuthorized` falso, sin ancla y sin parada,
`onStepEvent` deja `stepCount` intacto, así que no inventan evidencia de egress.

**Van marcados en bloque dentro del fixture.** Nada más en ese fichero es inferido.

→ Hueco de telemetría abierto como ticket propio:
`docs/backlog/det-cadence-steps-are-invisible-to-telemetry-001.md`. **Bloquea la calibración** del
ticket del pestillo, porque el corpus que aquél necesita no se puede recoger sin esto.

## Pendiente

- [ ] Field test: un trayecto en coche por el centro sin pasar de 50 km/h que termine en pin
      automático (criterio 5). Es lo único que queda sin cubrir por tests.
