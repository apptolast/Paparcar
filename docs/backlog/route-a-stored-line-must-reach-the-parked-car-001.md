# ROUTE-A-STORED-LINE-MUST-REACH-THE-PARKED-CAR-001 · la ruta GUARDADA también puede quedarse corta, y ahí el recorte se persiste

**Estado:** 🔵 Abierto · sin rama · follow-up deliberado de
[`route-a-drawn-line-must-reach-the-car-001.md`](route-a-drawn-line-must-reach-the-car-001.md)

## Problema

El mismo invariante que aquel ticket cerró para la traza VIVA sigue abierto para la ruta que se
guarda al aparcar:

> **La línea dibujada de un viaje llega SIEMPRE hasta el último punto medido.**

`EnrichParkingSessionWorker.snapRoute()` (`:117`) casa la ruta cruda y escribe el resultado en
`routePolyline`. Si los últimos fixes están a más de `TrailMapMatcher.MAX_SNAP_METERS` (120 m) de
cualquier calle de OSM —un parking, una urbanización nueva, un túnel, un camino sin mapear—
`match()` los descarta, la línea termina antes, y **eso se persiste**: Room, Firestore, el espejo del
coche compartido y el historial guardan una ruta que se queda corta del pin. Para siempre, y en
todos los dispositivos del grupo.

Es justo el tramo final del viaje: el que acaba donde el usuario aparcó, que es la parte que más
mira.

## Por qué NO se arregló en el ticket padre

Allí la cola sin calle era **cobertura que faltaba** (el bbox cacheado se había quedado atrás), y
eso se arregla sin tocar semántica: se casa el prefijo cubierto y el resto se dibuja medido, en
memoria, sin persistir nada.

Aquí el fetch cubre el bbox completo de la ruta de una sola vez, así que no hay cobertura que
arreglar: los fixes están **genuinamente fuera de cualquier calle conocida**. Y dibujarlos crudos
sin más contradice `[ROUTE-LINE-ONROAD-001]`, que existe por buenas razones.

## Lo que hace falta: una TERCERA provenance

Hoy un tramo persistido sólo puede ser una de dos cosas:

| Provenance | Significa |
|---|---|
| medido (por defecto) | se midió Y va por la calle |
| `inferredSpans` | **dibujado sin medir** — reconstrucción por carretera de un hueco de datos |
| `cuts` | no hay puente plausible: la línea se rompe |

La cola off-road no es ninguna: está **medida** (la anduvimos) pero **no es callejeable** (no
sabemos por qué calle). Meterla en `inferredSpans` sería mentir justo al revés de como miente hoy —
marcar como *adivinado* lo único que sí se midió.

Blast radius de la tercera: `InferredRoute` (codificación), `ParkingHistoryDto` + `SharedCarDto`,
las reglas de Firestore si cambia la forma, el render por segmentos de `PaparcarMapView` y
`ParkingHistoryDetailScreen`, y la paridad de deserializadores. Por eso va aparte.

## Lo que el padre ya dejó hecho para esto

`MatchedRoute.coveredThrough` + `covers()` + `shortfallMeters()` ya existen en
`domain/matching/TrailMapMatcher.kt`: **el worker ya puede DETECTAR el recorte** sin heurísticas.
Lo que falta es decidir qué escribe cuando lo detecta.

## Opciones a decidir (no decididas)

1. **Tercera provenance** — lo honesto y lo caro.
2. **Ensanchar el radio sólo para el fix FINAL**, simétrico con `ORIGIN_SNAP_METERS` (300 m). Barato,
   pero el KDoc del matcher **ya lo rechazó por escrito** (*«widening it would drag a genuinely
   off-road trail onto a distant road»*), así que reabrirlo exige argumentar contra esa razón, no
   ignorarla.
3. **No escribir la ruta recortada**: si no cubre, dejarla sin `snapped` y quedarse con la cruda
   (`acceptRawRouteAsFinal` ya hace algo parecido cuando se agotan los reintentos). La más barata de
   las tres y la que menos promete.

## Criterio de éxito

- Un viaje que acaba en un parking subterráneo guarda una ruta que **llega al pin**, o dice por qué
  no llega — pero no se queda corta en silencio.
- Test sobre `snapRoute` con una cola off-road: hoy persiste una polilínea más corta que la cruda.
