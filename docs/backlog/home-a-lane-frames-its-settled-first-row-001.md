# HOME-A-LANE-FRAMES-ITS-SETTLED-FIRST-ROW-001 · cambiar de carril lleva la cámara a un elemento que la lista no pone primero

**Estado:** ✅ Done · issue **#124** · `./gradlew testAll` verde con los dos testigos falsificados

## Problema

> «Cuando cambio de tab carril, debería llevarme al primer elemento de la lista pero este creo que
> me anima la cámara al primero de la lista sin priorizar cercanía o barata. Comprobarlo también en
> parkings y spot.» — issue #124, con captura.

En la captura (carril **Gasolineras**, orden *Nearest*, producto *Diesel*, 58 estaciones): la
primera fila de la hoja es **ÁREA117 · 11,3 km · CHEAPEST**, la segunda **MOEVE · 2,9 km ·
NEAREST** — y el mapa está encuadrado sobre una tercera, *Grupo Jorpa*, en Ctra. Villaverde a
Vallecas. La cámara no fue ni a la primera fila, ni a la más cercana, ni a la más barata.

Son **dos defectos distintos** que producen el mismo síntoma, y el segundo explica por qué el
primero no se ve en gasolineras ni en parkings:

### A · El carril de PLAZAS no tiene orden

`HomeState.filteredNearbySpots()` ordena **sólo** por `status == PROVISIONAL` (las sin confirmar,
al final). Dentro de cada grupo manda el orden en el que contestó el feed. Los otros dos carriles
llevan orden desde `NearbyOrdering` (parkings: más cercano primero; gasolineras: destacadas,
con precio, y distancia) — las plazas, que son **el producto**, no.

Consecuencia: la primera fila del carril de plazas —y por tanto lo que encuadra la cámara, y por
dónde empieza el stepper ‹ / › del peek— es una plaza cualquiera, no la que tienes al lado.

### B · La cámara encuadra una respuesta que todavía está volando

`laneFirstPoint` pregunta si la respuesta del carril «va de aquí» con
`ViewportRefetch.answerCoversCamera(anchorLat, anchorLon, cameraLat, cameraLon)`. Pero **el ancla
se estampa al PREGUNTAR, no al contestar** (`fetchFuelStations` / `fetchPublicParkings` escriben
`isLoading = true, anchorLat = lat, anchorLon = lon` antes de lanzar la petición), y la lista
anterior **no se vacía** mientras la nueva vuela. En parkings es incluso deliberado: el disco pinta
lo recordado mientras se pregunta [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001].

Así que en el instante del cambio de carril la pregunta estricta contesta **sí, esta lista va de
aquí** sobre una lista que es la de la visita anterior (o la del warm-up
[HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001], hecho desde otra posición de cámara). El
encuadre se dispara con ella, `laneAwaitingFrame` se limpia, y cuando llega la respuesta de verdad
—otro conjunto, otro orden— **la cámara ya no se corrige nunca**.

## Doctrina violada

- **«El primer elemento tiene que ser el que el usuario ve arriba del todo»** — lo dice el propio
  KDoc de `laneFirstPoint`, y [HOME-THE-CAMERA-FRAMES-WHAT-THE-LIST-SHOWS-001] lo arregló para los
  filtros. Volvió a romperse por el eje del TIEMPO en vez del eje del filtro: la lista pintada es la
  correcta, pero se lee un instante en el que aún no es la respuesta.
- **Una proyección, un orden** ([NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]): tres carriles que se
  leen igual no pueden tener dos en `NearbyOrdering` y el tercero con su orden escrito a mano en un
  slice.
- **«Volar la cámara a una fila es otra cosa que enseñarla»** (KDoc de `laneAnsweringHere`): la
  pregunta estricta existe justo para esto y le faltaba la mitad temporal.

## Señales / datos disponibles

- `HomeFuelSlice.isLoading` / `HomeParkingsSlice.isLoading` ya distinguen «preguntando» de
  «contestado»; el peek ya los lee (`laneLoading`).
- Las plazas **no cargan nunca** (suscripción viva), así que su carril siempre está asentado.
- `HomeSlicesTest` ya tiene los testigos de #87 y #119 sobre `laneFirstPoint`.

## Diseño

**Un solo sitio decide la primera fila de un carril, y otro solo sitio decide cuándo esa fila es
citable.**

1. `NearbyOrdering.spots(spots, user)` — nace la tercera función de orden, al lado de las de
   gasolineras y parkings, con la misma regla de medida (**desde el USUARIO**, como las otras dos y
   como la fila de plaza). Sin fix no se inventa una distancia: se conserva el orden de la fuente.
   `filteredNearbySpots()` pasa a filtrar y delegar el orden.
2. `laneAnsweringHere` —cuyo único cliente es `laneFirstPoint`— añade la mitad que le faltaba: un
   carril **que está cargando no tiene respuesta de aquí**, porque lo que enseña es la anterior. El
   encuadre queda ARMADO (ya lo estaba: `laneAwaitingFrame`) y se dispara con la primera lista
   asentada, que es exactamente lo que prometía [HOME-A-LANE-CHANGE-TAKES-THE-CAMERA-WITH-IT-001]
   («si no está cargado, pues cuando carguen los elementos»).

⛔ Lo que **no** se toca: `laneShowable`. Enseñar la lista recordada mientras se pregunta es
correcto y es la razón de ser de [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]. Lo
que no vale es **volar** a ella.

## Criterio de éxito

- `NearbyOrderingTest`: con fix, la plaza más cercana es la primera; una PROVISIONAL cercana sigue
  detrás de una confirmada lejana; sin fix, el orden de la fuente sobrevive.
- `HomeSlicesTest`: un carril con `isLoading = true` y la lista de la visita anterior **no**
  encuadra nada; el mismo estado con `isLoading = false` sí.
- `HomeSlicesTest`: el primer punto encuadrado del carril de plazas es el de la primera fila que
  pinta la hoja (`toBrowseListSlice().filteredSpots.first()`).
- En device: entrar en Gasolineras desde otra zona lleva la cámara a la fila que encabeza la lista
  YA cargada, no a una estación de la visita anterior.

## Consecuencia que conviene mirar en mano

Con el orden *Nearest* del carril de gasolineras, la primera fila **no es la más cercana**: es la
más BARATA, adelantada a propósito por [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001] («un
badge que hay que buscar no es un badge»). En la captura de la issue eso es ÁREA117, a **11,3 km**.
Como la cámara encuadra la primera fila —que es justo lo que pide la issue—, entrar en el carril
llevará el mapa hasta ella.

Es coherente con lo que el usuario pidió («al que la lista contiene más barato o cercano») y con la
doctrina de que la cámara enseña la fila que la lista pone primera. Si en mano se lee como que el
mapa «se va» de donde estás, la decisión NO es volver a desalinear cámara y lista: es decidir qué
fila encabeza el carril *Nearest*. Queda anotado, sin tocar, hasta verlo.

## Estado de la suite

`./gradlew testAll` ✅ · los dos testigos nuevos de `HomeSlicesTest` **falsificados**: revertida la
producción, `should_frameNothing_when_theLaneIsStillAsking` y
`should_frameTheNearestSpot_when_theSpotsLaneIsShowing` fallan (106 tests, 2 failed).

## iOS

Todo el cambio vive en `commonMain` (`NearbyOrdering`, `HomeSlices`): iOS lo hereda sin hueco.

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `HomeScreen.kt:1126` (encuadre al cambiar de carril) | que `laneFirstPoint` cita la lista asentada | ✅ cubierto por el cambio 2 |
| `toPeekSlice().browsableSpotIds` (stepper ‹ / ›) | el orden de `filteredNearbySpots()` | ✅ hereda el orden nuevo — sigue caminando las filas en el orden pintado |
| `toBrowseListSlice().filteredSpots` (filas de la hoja) | ídem | ✅ hereda |
| `toMapSlice()` / markers de plazas | el CONJUNTO, no el orden | ✅ exento [MAP-THE-MAP-READS-THE-SET-NOT-THE-ORDER-001] |
| `laneShowable` (filas y pins que se ENSEÑAN) | lista recordada visible mientras carga | ✅ intacto a propósito |
| `spotsOnOffer` / checklist | «¿hay alguna?», no cuál | ✅ no le afecta el orden |
