# UI-THE-ASK-IS-A-CAMERA-SUBJECT-001 · la pregunta abre la hoja y la cámara mira a otro sitio

**Estado:** ✅ Done · mergeado en master (squash) sobre `29a9b0a5` · ⏳ sin ver en device

## Problema

Con una pregunta «¿Has aparcado?» abierta, el sheet se abre solo sobre ella y el marcador `?` se
dibuja en su sitio — pero **la cámara sigue mirando donde estaba**. Al abrir la app, eso significa
mirarte a TI.

Reproducción exacta del caso que la funcionalidad existe para servir: aparcas, andas 300 m, abres la
app. El sheet pregunta por un sitio, el marcador `?` está en ese sitio, y la cámara está centrada en
ti a zoom 16. La pregunta y el mapa hablan de dos lugares distintos, y el sitio por el que se
pregunta puede estar fuera de pantalla.

## Doctrina violada

`DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001` — *«la pregunta enseña DÓNDE pregunta»*. Se
implementó el marcador y el tap, pero enseñar un sitio al que la cámara no apunta no es enseñarlo:
es dibujarlo fuera del encuadre.

Y el corolario que este ticket convierte en invariante: **el sujeto del sheet y el sujeto de la
cámara son el mismo**. Cuando el sheet se abre solo por algo, la cámara va con él.

## Señales / datos disponibles

Todo el dato necesario ya existe y ya sobrevive al arranque en frío:

- `PendingPromptWindow.candidate: GpsPoint?` — dónde se vio parar al coche. Persistido en
  preferencias, leído en `HomeViewModel.subscribePromptWindow()`.
- `PendingPromptWindow.shownAtMs: Long` — la **identidad** de la pregunta. Ya es la llave del
  auto-open del sheet (`SheetTransitionEffects`, `HomeSheetPositioning.kt:354`), y por tanto ya es
  la unidad de «una vez por pregunta» del proyecto.
- `HomeSlices` ya expone `unconfirmedParking = promptWindow?.candidate` para pintar el marcador.

Lo que NO existe: ningún camino desde ese punto hasta la cámara. Los dos sujetos de
`HomeCameraEffects` son `state.userParking` y el usuario, y durante una pregunta **no hay sesión**
— eso es justo lo que se pregunta —, así que `centerInitialFocus` cae siempre en la rama «no
aparcado» → centra en el usuario a `FOCUS_SEARCH_ZOOM`.

## Diseño

Una puerta nueva en `HomeUiController`, el sitio donde ya vive TODA la máquina de estados de
encuadre (one-shots, guardas de paneo manual, rango frente al driver-follow):

```kotlin
fun frameTheAsk(shownAtMs: Long, candidate: Pair<Double, Double>)
```

Tres decisiones, y por qué:

1. **Encuadre CERRADO sobre el sitio, sin bounds con el usuario.** El coche aparcado se encuadra
   *contigo* (`frameParking` → bounds si estás a ≤250 m) porque estás volviendo a él: las dos
   posiciones se leen juntas. La pregunta es otra cosa: es *«¿está el coche AHÍ?»*, y responderla
   pide ver esa manzana, no un promedio entre esa manzana y donde tú estés. Encuadre solo, a
   `FOCUS_PARKED_ZOOM`.

   Consecuencia buena y buscada: al no necesitar tu posición, **no depende del primer fix GPS**.
   Dispara en la composición, con la app recién abierta y el GPS todavía sin contestar.

2. **Consume el foco inicial** (`centeredOnUser = true`). Es lo que impide que
   `centerInitialFocus`, que llega después con el primer fix, arrastre la cámara de vuelta a ti.
   Por eso NO hace falta añadirle a `centerInitialFocus` un sujeto `ask` ni reordenar sus ramas: la
   pregunta gana por llegar antes y por cerrar la puerta al salir.

   Efecto lateral asumido: si coexisten una pregunta abierta y un coche aparcado de verdad, la
   pregunta gana el encuadre de apertura. Es el rango correcto — la pregunta tiene 15 minutos de
   plazo y el sheet ya está encima de ella.

3. **Una vez por PREGUNTA, y la identidad vive en el controller** (`framedAskAtMs`), no en la llave
   del `LaunchedEffect`. La misma regla que el auto-open del sheet, con la misma unidad
   (`shownAtMs`): una pregunta nueva se encuadra, la misma pregunta no se re-encuadra. Ponerlo en
   el controller y no en la llave es lo que deja re-encuadrar sin miedo aunque el efecto se
   relance.

   **No** lleva la guarda de `userMovedCameraManually`. Deliberado: una pregunta nueva es un evento
   nuevo, y el sheet ya se abre solo por ella sin pedir permiso. Si la cámara obedeciera la guarda
   de paneo y el sheet no, las dos superficies volverían a discrepar — que es el bug de arriba.

`initialFocusWasParking` sigue siendo *«el foco inicial encuadró la SESIÓN»*, y encuadrar la
pregunta no lo pone a true. Por eso responder «Sí» sigue funcionando: nace la sesión,
`refocusOnParkingArrival` la ve llegar y re-encuadra el pin real.

Consumidor: un efecto en `HomeCameraEffects` — el sitio que el propio fichero declara como
«camera choreography» — keyado por `state.promptWindow?.shownAtMs`, con la misma guarda de viaje
vivo que el refocus de parking (`drivingPuck.value == null`): si hay viaje en curso, el puck manda
y la pregunta es de una parada anterior. `[DET-READY-TRIP-OVER-PARKED-001]`

## Criterio de éxito

- `HomeUiControllerTest`: encuadrar la pregunta y **luego** llamar a `centerInitialFocus` con el
  primer fix deja la cámara en el sitio de la pregunta. Es el test de regresión del bug reportado —
  se valida por falsación (sin el `centeredOnUser = true`, rojo).
- Una segunda pregunta mueve la cámara; la misma pregunta vista otra vez, no (el token no avanza).
- Responder «Sí» re-encuadra el pin real.
- En campo: abrir la app con una pregunta pendiente deja el marcador `?` centrado y el sheet
  abierto sobre él, sin tocar nada.

## Consumidores auditados

Grep de todo lo que mueve la cámara en Home y de todo lo que lee `promptWindow`:

| Sitio | Veredicto |
|---|---|
| `HomeUiController.centerInitialFocus` | **cubierto** — la pregunta consume su one-shot antes de que llegue el primer fix |
| `HomeUiController.refocusOnParkingArrival` | **cubierto** — sigue vivo: encuadrar la pregunta no pone `initialFocusWasParking`, así que la sesión que nace del «Sí» re-encuadra |
| `HomeUiController.followDriver` / `resumeDriverFollow` | **cerrado** por la guarda `drivingPuck.value == null` del efecto: viaje vivo → el puck manda |
| `SheetTransitionEffects` (auto-open del sheet) | **cubierto** — misma unidad de identidad (`shownAtMs`); sheet y cámara abren la misma pregunta una sola vez |
| `onAskMarkerClick` (`HomeScreen.kt:538`) | **cubierto** — ya hacía `goToPlace(candidate)`; sigue siendo la puerta manual (rango de usuario, revoca follow) |
| FAB «mi coche aparcado» (`HomeMapFabsSection`) | **exento** — cicla entre `activeSessions`, y una pregunta abierta NO es una sesión. Meterla ahí haría que el botón prometiera un coche que quizá no está ahí; la pregunta ya tiene sus dos puertas (marcador y encuadre automático) |
| FAB punto medio (`onMidpoint`) | **exento** — mismo motivo: enmarca `userParking` + tú, y aquí no hay `userParking` |
| `HomeEffect.MoveCameraTo` (chip de zona, búsqueda) | **exento** — peticiones explícitas del usuario, rango superior por diseño `[UI-MAP-A-TAPPED-PLACE-OUTRANKS-THE-FOLLOWED-CAR-001]` |

## Resultado

**Implementado, sin commitear.** 3 ficheros de producción/tests + 2 de doc:

- `HomeUiController.kt` — `frameTheAsk(shownAtMs, candidate)` + `framedAskAtMs`.
- `HomeScreen.kt` — el efecto en `HomeCameraEffects`, declarado **antes** del foco inicial.
- `HomeUiControllerTest.kt` — 7 tests nuevos.

**2011 tests, 0 fallos** (`--rerun-tasks`, sin caché) · `:app:compileProdDebugKotlin` y
`:app:compileMockDebugKotlin` verdes.

Validado **por falsación**, dos veces, porque un test de prohibición que no se ve fallar siempre pasa:

| Se quita | Rojo |
|---|---|
| `centeredOnUser = true` | `keepTheAskedPlace_when_theFirstGpsFixArrives` · `outrankAParkedCar_when_aQuestionIsOpenAtTheSameTime` · `frameTheParkedCar_when_theAnswerTurnsTheQuestionIntoASession` |
| `if (framedAskAtMs == shownAtMs) return` | `notMoveTheCameraAgain_when_theSameQuestionIsSeenTwice` |

Ambos verdes tras restaurar.

**Sin strings nuevos** (no hay copy nuevo) y **sin cambios en el Dev Catalog**: no nace pantalla ni
estado — `StateGalleryScreen` ya tiene sus tres variantes de `AwaitingAnswer`, y el encuadre de
cámara no es un estado que la galería renderice (pinta `XxxContent(state=)`, sin mapa ni controller).

⏳ **Sin ver en device.** Lo que hay que mirar en campo: abrir la app con una pregunta pendiente y
comprobar que el marcador `?` queda centrado a la vez que el sheet se abre sobre él.
