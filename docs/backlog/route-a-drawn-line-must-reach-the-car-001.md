# ROUTE-A-DRAWN-LINE-MUST-REACH-THE-CAR-001 · el casado puede corregir la forma del viaje, nunca acortarlo

**Estado:** ✅ Done · en master · ⏳ pendiente captura y viaje de campo de confirmacion · rama `bugfix/ROUTE-A-DRAWN-LINE-MUST-REACH-THE-CAR-001-traza-recortada`
· worktree `../Paparcar-route-reach-car`

## Problema

Campo, 25-09-2026 20:15, Oppo CPH2371, viaje vivo (`FORD FOCUS · EN RUTA`, Camino de la Florida 73,
El Puerto de Santa María). La traza dibujada **es el trayecto**, pero **se acaba en un punto y no
sigue por donde va el coche**. No se recupera sola: el usuario tiene que salir y volver a entrar en
la app para que la línea vuelva a estar completa.

> «es el trayecto pero esta como recortado y se acaba en un punto y no sigue por donde voy, tengo
> que reiniciar la app para que se ponga bien la ruta»

⚠️ **Sin log de ese viaje.** El build instalado en el Oppo es release
(`run-as: package not debuggable`) y el buffer de logcat ya no alcanza las 20:15 del día anterior,
así que no hay `parkdiag.log` que diga cuál de los dos disparadores de abajo actuó. El agujero, en
cambio, **está probado leyendo el código** y no depende de cuál fuera: los dos terminan en el mismo
sitio. Parte de este ticket es dejar el testigo para que la próxima vez sí se sepa.

## Doctrina violada

Ninguna regla escrita lo prohíbe todavía — **ese es el bug**. La que falta:

> **La línea dibujada de un viaje llega SIEMPRE hasta el último punto medido.** El casado (map
> matching) puede *reemplazar* un tramo por su geometría de calle; no puede *borrar* el final.

Y hay que separarla con cuidado de la que sí está escrita, `[ROUTE-LINE-ONROAD-001]`
(*«un fix off-road nunca se dibuja RAW: se descarta y la transición ruteada entre los candidatos
on-road que lo BRACKETEAN puentea el hueco»*). Las dos son compatibles, y la palabra que las
concilia es **bracketean**:

- Un fix off-road **interior** tiene vecinos on-road a los dos lados. Descartarlo no pierde nada: el
  puente ruteado entre esos vecinos cruza el mismo terreno, por la calle. `ROUTE-LINE-ONROAD-001`
  tiene razón.
- Una **cola** off-road (o una cabecera) sólo tiene bracket por un lado. **No hay nada a lo que
  puentear.** Descartarla no sustituye la línea por otra mejor: **borra** terreno que el usuario
  recorrió de verdad. La línea se queda corta del coche y se lee como «la app ha perdido mi viaje».

El KDoc del matcher ya lo pensó a medias y se quedó en la versión todo-o-nada: *«If the whole trail
has no road nearby the raw trail is returned»*. Esa regla existe a nivel de traza entera
(`onRoad.size < 2`) y **no existe a nivel de extremo**, que es donde muerde.

## Señales / datos disponibles

Todo lo necesario ya está en el código; lo que no hay es quien lo compruebe.

**Dónde se corta** — `TrailMapMatcher.match()`
(`shared/src/commonMain/kotlin/com/rndeveloper/paparcar/domain/matching/TrailMapMatcher.kt:147-168`):

```kotlin
val onRoad = measurements.indices.filter { layers[it].isNotEmpty() }
if (onRoad.size < 2) return MatchedRoute(points, emptyList(), emptyList())  // ← sólo TODO o NADA
val keptMs = onRoad.map { measurements[it] }
```

Un fix sin calle a menos de `MAX_SNAP_METERS` (120 m) se cae de `onRoad`. Si los que se caen son los
**últimos**, la línea termina en el último medido que sí tenía calle, y **el valor de retorno no
dice en ningún sitio que se dejó fuera la cola**. `MatchedRoute` lleva provenance de huecos
(`inferredSpans`) y de cortes (`cuts`), pero no lleva *cuánto de la entrada cubre*.

**Por qué se dibuja la corta y no la completa** — `PaparcarMapView.kt:1833-1838`: la vista prefiere
la casada en cuanto `matched.size >= 2`. La traza cruda —que SÍ llega hasta el coche— nunca se
dibuja. La vista tiene que elegir entre *«va por la calle»* y *«está completa»*, y elige la primera.

**Los dos disparadores que dejan la cola sin calle:**

1. **Cobertura caducada (el carril vivo).** `HomeTripController.kt:160-176` cachea las calles del
   bbox del viaje + ~400 m. Cuando la traza se sale, re-pide; y si la re-petición **falla o contesta
   vacía** (Overpass: 429/504/timeout/mirror que miente — para eso existen `OverpassClient` y
   `OverpassResponseHealth`), `cachedRoads` **se queda con el set viejo, el pequeño**, y se casa la
   traza entera contra él. Los fixes nuevos no tienen calle *porque no la hemos pedido*, no porque
   estén fuera de la carretera. Reiniciar la app borra `cachedRoads`/`cachedRoadsBbox` → un fetch
   limpio del bbox actual → línea completa. **Eso explica la mitad «sólo se arregla reiniciando».**
2. **Cola genuinamente off-road.** Aunque el fetch vaya perfecto: un parking, una urbanización nueva,
   un túnel, un camino que OSM no tiene. Los últimos fixes están a >120 m de cualquier calle, se
   descartan, y el tramo final desaparece. Este muerde **también en la ruta guardada**
   (`EnrichParkingSessionWorker.snapRoute:117`), donde el recorte se **persiste** en Room y Firestore.

⛔ **Que "sin cobertura" y "off-road" sean indistinguibles es el error de categoría de fondo.** El
matcher sólo recibe `roads`; no puede saber si un fix no tiene calle porque está fuera de la
carretera o porque no le hemos dado esa zona del mapa. Quien lo sabe es el llamante, que es quien
eligió el bbox.

## Diseño

Dos piezas, cada una en el sitio que de verdad conoce el dato. Ninguna toca la doctrina de
`ROUTE-LINE-ONROAD-001`.

### 1 · El matcher declara cuánto de la entrada cubre

`MatchedRoute` gana el índice de la última medición representada en `points`. El recorte deja de ser
silencioso: pasa a estar **en el tipo**, y cualquier llamante puede comprobar el invariante sin
heurísticas de «¿qué punto medido cae cerca del final de la línea?».

Es lo mínimo que hace falta para que la regla sea comprobable, y sirve a los dos consumidores a la
vez.

### 2 · El llamante no pide casar lo que no tiene calles

`HomeTripController` **sí** sabe qué zona cubre su caché: es `cachedRoadsBbox`. Entonces casa sólo
el **prefijo de la traza dentro de la cobertura** y deja el resto como geometría medida. La línea
emitida es on-road donde hay calles y medida donde no las hemos pedido — y **llega hasta el coche
siempre**.

Con eso, el contrato de `TripUpdate.matchedTrail` deja de ser ambiguo: **cuando no está vacía, es la
línea COMPLETA que hay que dibujar**, y la regla que ya tiene la vista (`matched.size >= 2`) pasa a
ser correcta por construcción en vez de por suerte.

### 3 · El testigo

Un viaje recortado tiene que dejar línea en `parkdiag.log` (prefijo `PARKDIAG/`, el único que
`FileAntilog` persiste — por `[IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001]`), diciendo
**cuántos metros de cola se quedaron sin casar y por cuál de los dos disparadores**. Hoy el
`PaparcarLogger.d("map-match: N pts → M snapped")` ni lleva el prefijo ni sobrevive a un release.

### Fuera de alcance, deliberadamente

- **La cola off-road de la ruta GUARDADA** (disparador 2 en `EnrichParkingSessionWorker`). Es el
  mismo invariante, pero el arreglo honesto allí pide una tercera provenance —*medido pero no
  callejeable*, que no es `inferred` (*dibujado sin medir*) ni es `cut`— y eso arrastra
  `InferredRoute`, el DTO, Firestore y el render de segmentos. Se queda auditado aquí abajo y
  tiene ticket propio: `docs/backlog/route-a-stored-line-must-reach-the-parked-car-001.md`. La pieza 1 de este ticket es justo lo que ese ticket necesitará para poder
  detectarlo.
- **Que Overpass falle menos.** Reintentos, mirrors y quota son de `OverpassClient`. Este ticket
  hace que un fallo suyo **no mutile la línea**, no que no ocurra.

## Lo construido

| Pieza | Dónde |
|---|---|
| `MatchedRoute.coveredThrough` + `covers()` / `drawnOver()` / `shortfallMeters()` | `domain/matching/TrailMapMatcher.kt` |
| `decimate()` devuelve ÍNDICES, no puntos — sin eso el índice de cobertura sería una heurística | `domain/matching/TrailMapMatcher.kt` |
| `snap()` entrega la línea a DIBUJAR (la fachada plana ya no puede volver corta) | `domain/matching/TrailMapMatcher.kt` |
| `TripLine.drawn(trail, roads, coverage)` — la regla, pura y testeable | `domain/matching/TripLine.kt` **(nuevo)** |
| `RoadCoverage` — el bbox deja de ser un `Bbox` privado del controlador | `domain/matching/RoadCoverage.kt` **(nuevo)** |
| El testigo `PARKDIAG/Route`, por FLANCO (dos líneas por episodio, no una por tick) | `presentation/home/HomeTripController.kt` |

Dos deduplicaciones de propina, no invenciones nuevas: el `Bbox` privado del controlador **era** una
`RoadCoverage` sin nombre, y `MIN_MATCH_POINTS` estaba escrito en el controlador cuando quien decide
qué es casable es quien casa.

**iOS:** nada que rellenar. Todo lo tocado es `commonMain` y `RoadNetworkDataSource` se inyecta desde
el `DataModule` común (`:377`), así que el carril de iOS recibe el arreglo entero sin `expect/actual`.
No compilado aquí: los targets Apple no construyen desde Windows.

## ⛔ Lo que casi se cuela: un test que no podía ponerse rojo

La primera versión de `TripLineTest` daba al matcher una calle que llegaba **hasta el final del
viaje** y sólo mentía en el bbox. Con el bug puesto, el matcher encontraba calle para todos los
fixes, no recortaba nada, y **el test pasaba igual de verde**. Sólo se vio al falsificarlo a
propósito.

El fixture tiene que modelar el mundo, no el síntoma: cuando un fetch falla, lo que falta **son las
calles**, no una etiqueta. Corregido, la falsificación da el mensaje del campo: *«la línea tiene que
llegar al último fix medido; se quedó a 357 m»*.

Los tres testigos se han visto en rojo uno a uno antes de darlos por buenos.

## Criterio de éxito

- Test del invariante sobre el matcher: con una traza cuyos últimos N fixes no tienen calle,
  `MatchedRoute` **declara** que no los cubre (hoy no lo declara y el test falla).
- Test del controlador: con la caché de calles cubriendo sólo el principio del viaje, la línea
  emitida **termina en el último fix medido**, no en el borde de la cobertura.
- Campo: un viaje que salga de la zona cacheada con Overpass caído dibuja la línea entera —
  callejeada donde hay datos, medida donde no— y **sin reiniciar la app**.
- Y si vuelve a pasar, `parkdiag.log` dice cuántos metros y por qué.

## Consumidores auditados

`grep -rn "TrailMapMatcher\." --include=*.kt shared/src app/src mockkit/src` → **dos** llamantes de
producción, y los dos asumen que la línea casada cubre la traza entera:

| Sitio | Qué asume | Estado |
|---|---|---|
| `HomeTripController.kt:182` (`snap`, traza viva) | que `cachedRoads` cubre `trail` | 🔴 **la vía que mordió** — cerrado por las piezas 1+2 |
| `EnrichParkingSessionWorker.kt:117` (`match`, ruta guardada) | que ningún fix final es off-road | 🟡 **abierto** — el fetch de una sola vez cubre el bbox completo, así que el disparador 1 no existe aquí; el 2 sí, y **se persiste**. Ticket propio |
| `EnrichParkingSessionWorker.kt:167` (`match`, pin-a-pin inferido) | nada: entrada de 2 puntos, se descarta si no hay puente | ✅ exento |
| `PaparcarMapView.kt:1833` (render) | que la casada es preferible a la cruda | ✅ cubierto — pasa a ser cierto por construcción (pieza 2) |
| `ParkingHistoryDetailScreen.kt:208-236` (render histórico) | dibuja lo persistido, no casa nada | ✅ exento — hereda lo que escriba el worker |

**Auditado y NO es esto** (mismo síntoma, otro extremo): `MapTrail.MAX_POINTS = 500` recorta por la
**cabeza** (ventana deslizante, los puntos más viejos caen). El usuario describe el final, no el
principio. Queda anotado porque en un viaje largo la línea también miente, por el otro lado.

**Auditado y descartado como causa de ESTE viaje**: la muerte silenciosa del stream
(`[HOME-A-LIVE-STREAM-MUST-NOT-DIE-IN-SILENCE-001]`, issue #127). `trip.updates` ya va envuelto en
`.resilient("tripRender")` (`HomeViewModel.kt:188`), que reintenta 6 veces con backoff y al
reiniciar el `channelFlow` reconstruye la traza desde `freshRecordedRoute()` — o sea, **se cura
sola**. Sólo quedaría congelada si fallara 6 veces seguidas en ~25 s, y en ese caso se congelaría
**también el puck**, no sólo la línea. No se puede descartar del todo sin el log, y por eso existe
la pieza 3.
