# HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001 · La ruta se cuenta una vez, y «Parar» viaja con ella

**Estado:** ✅ Done (2026-09-14) · mergeado a master con squash · ⏳ pendiente de ver en device

## Problema
Durante un viaje en curso, el sheet abierto de Home enseña **dos superficies que cuentan la misma
noticia**, una encima de la otra:

1. La superficie de detección pinta `DetectionStory.Driving`: «Conduciendo tu X — Detectaremos
   dónde aparcas · [Parar]» (`HomeDetectionSurface.kt:281`).
2. La card/chip del coche ya cuenta ese mismo viaje con más detalle: halo radar en el glifo, borde
   engrosado del color de identidad, glifo de ruta autodibujándose y «En ruta · Calle X» con pulso
   (`HomeParkingRow.kt:128,164` para el chip; `:337,355` para la card).

Es exactamente el choque editorial que motivó `HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001`
para la vigilancia calmada («Watching your Alfa · Parked» justo encima de «Alfa · Parked at Calle X»
son dos veces la misma noticia). Aquella unificación cubrió la fila calmada y el arranque en frío
vía `carCardSaysTheWatch`, pero **`Driving` quedó fuera del filtro**: en `resolveDetectionStory`
(`DetectionStory.kt:240`), `DetectionUiState.Monitoring → drivingStory()` no consulta
`carCardSaysTheWatch`.

## Doctrina violada
- **La regla de apartar por «decir lo MISMO»** (comentario de `carCardSaysTheWatch`,
  `DetectionStory.kt:125-137`): dos superficies que dicen la misma frase no compiten por el ojo, se
  duplican. La card en ruta dice todo lo que dice la fila `Driving` — y además el lugar.
- **[DET-STOP-BUTTON-001]** condiciona la solución: «Parar» es la salida de un viaje que el usuario
  no quiere que se siga, y debe vivir en la superficie que afirma estar siguiéndole, a un solo tap.
  Apartar la fila sin recolocar el CTA sería perder la salida — eso NO se hace.

## Señales / datos disponibles
- `carCardSaysTheWatch = showPersonalBlocks = vehicleCards.isNotEmpty()` (`HomeSheetContent.kt:237`).
- `drivingStory()` ya devuelve `Hidden` cuando no hay card con nombre (`DetectionStory.kt:168-170`):
  la historia `Driving` solo puede pintarse cuando la tira de coches ESTÁ en pantalla. Consecuencia:
  con el gate puesto, la rama `Driving` de `HomeDetectionSurface` queda **inalcanzable desde
  producción** — hay que decidir si se borra limpio (doctrina: al quitar un botón, borrar limpio)
  junto con sus strings, o si sobrevive solo en previews.
- El peek colapsado tiene su propio eyebrow «EN RUTA» — es la voz del sheet CERRADO
  (`DetectionStory.kt:30-32`), deliberada y fuera de alcance de este ticket.
- La `HomeVehicleCard` ya tuvo un botón y se quitó (user, 10-09) porque su `onClick` duplicaba el
  gesto de la propia card. «Parar» no cae en esa regla: es una acción DISTINTA del tap del
  contenedor (que abre el estado del vehículo).

## Diseño (ejecutado)
El SISTEMA es la proyección `resolveDetectionStory`, que ya es el único árbitro de qué superficie
habla — la unificación se hace ahí, no con un `if` en el composable (la lección de
[DET-ASK-STATE-001]).

1. **La historia `Driving` se BORRA, no se gatea.** El plan inicial era apartarla vía
   `carCardSaysTheWatch`, pero el gate la dejaba inalcanzable en producción SIEMPRE: `drivingStory()`
   necesitaba una card a la que ponerle nombre, así que solo podía pintarse cuando la tira de coches
   estaba en pantalla — exactamente cuando el gate la apagaría. Una rama viva solo para previews es
   un agujero, no una excepción (misma doctrina que la allowlist de tipografía). Se van con ella:
   `drivingStory()`, el parámetro `drivingMeta` de la proyección, la rama del composable y los 4
   strings `home_det_driving_title/sub` + `home_det_candidate_title/sub` en los 9 locales.
   `DetectionUiState.Monitoring → Hidden` con el porqué escrito encima. Las preguntas
   (`AwaitingAnswer`/`PendingAsk`) siguen resolviéndose ANTES del `when`, así que siguen ganando al
   viaje vivo.
2. **«Parar» vive en la card del coche** (`HomeParkingRow.kt`, parámetro `onStopTrip`, solo leído
   con `isDriving`):
   - `HomeVehicleCard` (1 coche): pill INLINE en el canto derecho del pie de conducción — un CTA de
     cuatro letras no se gana una banda a lo ancho ([UI-DET-STOP-BELONGS-BESIDE-THE-STORY-001]).
     Los textos van en una fila interior con `weight(1f)` para que una calle larga nunca lo empuje
     fuera.
   - `HomeVehicleChip` (2+ coches): pill apilada a lo ancho bajo el pie — ahí «a lo ancho» son
     ~150–200 dp y el pie compacto no tiene hueco inline. Solo la lleva el chip que ES el viaje.
   - Pill: 34 dp, tinta tonal del color de identidad del coche (`accent` al 0.16 + texto `accent`),
     el mismo lenguaje de contenedor que usaba el tono de identidad de la superficie.
3. **`PapCtaPill` nace en `ui/components`**: la `CtaPill` privada de `HomeDetectionSurface` pasa a
   componente compartido (2 consumidores desde hoy). `PapPrimaryButton` no valía: sus ejes son
   Brand/Destructive y esta pill habla en el tono del anfitrión (severidad o identidad del coche).
4. El wiring del intent ya existía (`HomeIntent.StopDetection` → `stopByUser()` → snackbar
   `home_det_trip_stopped_msg`): solo cambió la superficie que lo dispara. `home_det_driving_stop_cta`
   («Parar») se conserva — lo usa la pill nueva.
5. **Solo `Driving` se apartó.** `BlockedCore`, `AwaitingAnswer`, `PendingAsk`, `NoVehicle`,
   `Inactive` y los avisos `WATCHING_FRAGILE` / `WATCH_INTERRUPTED` dicen cosas que la card NO dice
   y quedan enteros.

## Criterio de éxito
- Con un viaje en curso y la tira de coches en pantalla, la noticia «en ruta» aparece UNA vez (en
  la card del coche) y «Parar» está a un tap en esa misma card, en la variante de 1 coche y en la
  de 2+. ⏳ **pendiente de ver en device.**
- ✅ Testigo en la proyección:
  `should_hideTheLiveTrip_because_theVehicleCardTellsItWholeAndCarriesTheStop` (Monitoring →
  Hidden), y `should_ask_the_open_question_over_the_live_trip` sigue probando que la pregunta
  abierta gana al viaje.
- ✅ Suite verde (`:shared:testDebugUnitTest`) y ambos flavors compilan
  (`:app:compileMockDebugKotlin :app:compileProdDebugKotlin`).

## Consumidores auditados
Grep `DetectionStory.Driving|resolveDetectionStory|home_det_driving|home_det_candidate|onStopDetection`:
- `DetectionStory.kt` — historia y `drivingStory()` borrados; `Monitoring → Hidden` comentado;
  header actualizado. → **cerrado**
- `HomeDetectionSurface.kt` — rama `Driving` y `onStopDetection` fuera; imports y constantes
  muertos barridos (incluidos 4 pre-existentes: `FontWeight`, `RoundedCornerShape`, `TILE_DP`…);
  su `CtaPill` privada promovida a `PapCtaPill`. → **cerrado**
- `HomeSheetContent.kt` — ya no pasa `drivingMeta` ni `onStopDetection` a la superficie; cablea
  `onStopTrip = StopDetection` a card y chip. → **cerrado**
- `HomeParkingRow.kt` — `onStopTrip` + pill en las dos anatomías. → **cerrado**
- `DetectionStoryTest.kt` — 4 tests de Driving sustituidos por el testigo de Hidden; todas las
  llamadas sin `drivingMeta`. → **cerrado**
- `HomeDetectionSurfacePreviews.kt` — 3 entradas Driving fuera, nota de dónde vive ahora. → **cerrado**
- `HomeSheetPreviews.kt` — los previews en ruta de card y chip pasan `onStopTrip = {}` y enseñan la
  pill. → **cerrado**
- `StateGalleryScreen.kt` — 3 variantes Driving de la superficie fuera; los variants de sheet
  completo («coche candidato», «chips 2+ con uno en ruta») pasan por `HomeSheetContent` y enseñan
  la pill sin tocarlos. → **cerrado**
- `OnboardingScreen.kt` — el comentario del glifo `Navigation` citaba la fila borrada; reescrito. → **cerrado**
- `docs/detection/PARKING-DETECTION.md` (entrada DET-STOP-BUTTON-001) — nota de actualización
  añadida (log cronológico: no se reescribe). `08-flujo-e2e.md` §B.4 — arista `HOME_ROW_CTA`
  reescrita a `CARD_STOP_PILL`. → **cerrado**
- Peek colapsado (eyebrow «EN RUTA») — voz del sheet cerrado, deliberadamente distinta. → **exento**
- `home_det_trip_stopped_msg` (snackbar) y `home_det_driving_stop_cta` («Parar», reutilizado) — se
  conservan. → **exento**
- Notificación FGS «Parar detección» (`ParkingConfirmationReceiver`) — carril propio, intacto. → **exento**
