# UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001 · Los km del historial son una suma parcial presentada como total

**Estado:** ✅ Done · worktree `../Paparcar-history-chart`, encima de
`UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001` (mismas líneas de `HistoryContent.kt`; en
worktrees separados habrían chocado)
**Abierto:** 2026-08-31 sobre master `748648fc`

## Problema

La cabecera del gráfico de actividad dice *"N parkings · X km"*. El `N` cuenta **todas** las
sesiones del rango; los `X km` suman solo **las que tienen ruta**.

`VehicleHistoryCalculator.sumDistanceMeters` (`VehicleHistoryCalculator.kt:103-106`) hace
`mapNotNull { it.routeDistanceMeters }.takeIf { it.isNotEmpty() }?.sum()`. El propio campo declara
cuándo es null (`UserParking.kt:95-99`): *"Null when there is no route (BT/legacy) — a missing
distance is 'unknown', never 0"*.

A nivel de sesión la regla se respeta y tiene test
(`should_returnNullDistance_whenNoSessionCarriesOne`): sin ningún dato no se pinta nada, jamás
"0 km". **Al agregar, esa regla se rompe**: los desconocidos entran en la suma valiendo cero, y el
resultado se presenta junto a un recuento que sí los incluye.

**Cuándo muerde de verdad:** en un vehículo **mixto**. Si ninguna sesión tiene ruta, el `takeIf`
devuelve null y los km no se pintan — correcto. El daño está en el coche que tiene unas cuantas
sesiones con ruta y otras sin ella: entonces sí sale una cifra, y describe un subconjunto sin
decirlo. Eso no es un caso raro: el carril que se usa depende de `resolveStrategy(vehicle,
isBluetoothEnabled)`, así que el mismo coche pasa por Coordinator (con ruta) o por BT (sin ella)
según el Bluetooth esté activo ese día.

Sin marca de cobertura, la cifra es indistinguible entre "condujiste 40 km" y "de los 12
aparcamientos de este mes pudimos medir 3, y suman 40 km".

## Doctrina violada

*Fallo asimétrico y honestidad del dato*: mejor no decir nada que decir algo que no consta. Es la
misma regla que ya está escrita en el campo (`"unknown", never 0`) y en el ticket de la zona
aproximada (*"No inventar un radio por defecto: eso sería afirmar una duda que no consta"*). El
invariante existe a nivel de sesión y **no se propagó al agregado** — que es exactamente el patrón
de "el invariante vive en un sitio y sus consumidores no se barrieron".

## Señales / datos disponibles

Todo local y gratis: por cada rango se puede contar cuántas de las `filteredSessions` traen
`routeDistanceMeters != null`. No hace falta ni una consulta nueva.

⚠️ **Y hay una tercera categoría en camino, no dos.** `EnrichParkingSessionWorker.inferPinToPinRoute`
(`EnrichParkingSessionWorker.kt:141-175`) reconstruye rutas **inferidas** pin-a-pin para
`{safety_net_backfill, manual, user, nudge}`, y `updateParkingSessionRoute` les estampa distancia
igual que a las medidas. O sea que hoy ya hay sesiones cuyos km **no se midieron, se dedujeron**, y
`sumDistanceMeters` las suma sin distinguirlas. Las marcas existen y están en el modelo
(`routeInferredSpans`, `routeInferredResolution`), simplemente el agregado no las mira.

Si además se aprueba `DET-A-BT-DRIVE-LEAVES-NO-TRACE-001`, la proporción de km inferidos sube.

## Diseño

**El invariante: una suma declara su cobertura, o no se muestra.** Vive en `sumDistanceMeters`, que
deja de devolver un `Float?` desnudo y pasa a devolver un value object con la suma **y** de cuántas
sesiones sale, sobre cuántas había. Así ningún call site puede pintar la cifra sin tener a mano el
dato que la califica — que es la razón por la que hoy se pinta desnuda.

Cómo se presenta, con el value object ya disponible (decisión de copy, no de arquitectura):

- **Cobertura total** → como hoy, "N parkings · X km". Nada que explicar.
- **Cobertura parcial** → la cifra dice sobre qué suma. La forma más barata y sin jerga es que el
  sujeto del que se predican los km sea el subconjunto, no el rango.
- **Cobertura cero** → nada, como hoy.

⚠️ No usar "aproximado" para esto: en el vocabulario del proyecto esa palabra ya significa otra cosa
(la zona con radio, `UI-APPROXIMATE-ZONE-IN-HISTORY-001`). Un km medido de una sesión aproximada
sigue siendo un km medido. Y no mezclar en la misma frase "no medido" con "inferido": si al final
los inferidos se cuentan aparte, son tres estados y probablemente solo dos merecen salir a pantalla.

Decisión abierta para cuando se implemente: si los km inferidos suman con los medidos (marcando el
conjunto) o se quedan fuera hasta que el usuario dé su veredicto (`RouteInferenceResolution`). Lo
segundo es más honesto y hace la cifra más pequeña e inestable —cambia cuando el usuario responde—;
lo primero es más útil. No la resuelvo aquí.

## Cómo quedó

`sumDistanceMeters` devuelve `ScopedDistance(meters, fromParkings, ofParkings)` en vez de un
`Float?` desnudo. **La cobertura viaja con la cifra**, así que ningún call site puede pintarla sin
tenerla delante — que era exactamente cómo se pintaba.

Copy: cobertura total → como siempre (*"12 aparcamientos · 8,4 km"*); parcial → la cifra dice sobre
cuántos se midió (*"12 aparcamientos · 8,4 km en 3 de ellos"*); cobertura cero → nada, nunca "0 km".

`ActivityCard` no cambia de firma: sigue recibiendo `distanceText` ya formateado. El componente no
tiene por qué saber de cobertura; quien no podía ignorarla era el call site.

⚠️ **Decisión aplazada, a propósito**: las rutas INFERIDAS (pin-a-pin) siguen sumando junto a las
medidas, sin distinguirse. `ScopedDistance` deja sitio para separarlas el día que se decida —
`routeInferredResolution` ya está en el modelo— pero contar dos clases de cobertura antes de que
exista el caso BT sería inventar una distinción que hoy no cambia nada en pantalla.

## Criterio de éxito — cumplido

- `should_reportPartialCoverage_when_someSessionsCarryNoRoute` — 2 de 3 no es un total.
- `should_reportCompleteCoverage_when_everySessionCarriesARoute` — el caso común sigue siendo una
  cifra limpia, sin coletilla.
- `should_returnNullDistance_whenNoSessionCarriesOne` — intacto: sin datos no se pinta nada.
- `should_sumPersistedDistances_ignoringRoutelessSessions` — sigue verde (`total?.meters`): sumar
  ignorando las sin ruta nunca fue el error; presentarlo como total, sí.
- String nuevo en los 9 locales. Galería mock: variante "Km de cobertura parcial (mixto BT + ruta)",
  una de cada tres sesiones con ruta.

## Consumidores auditados

- `VehicleHistoryCalculator.sumDistanceMeters` — único productor.
- `HistoryContent.kt` — **único** consumidor, confirmado por grep en todo el worktree: nadie más
  (Home, peek, detalle) suma distancias por su cuenta.
- `ActivityCard` / `ActivityCardTitle` — sin cambios; componen `"N aparcamientos · <texto>"` y el
  texto ya llega calificado.
- `StateGalleryScreen.kt` — variante nueva.

## Relacionados

- `DET-A-BT-DRIVE-LEAVES-NO-TRACE-001` — la causa principal de los nulls. **No lo sustituye**: aunque
  el BT deje de ser routeless, seguirá habiendo nulls (saltos triviales por debajo de
  `MAX_MEASURED_STEP_METERS`, fetch de calles fallido, rutas rechazadas por el usuario, filas
  legacy). Una suma parcial seguirá siendo posible; este ticket es lo que hace que se note.
- `UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001` — mismo bloque de UI
  (`HistoryContent.kt:264-298`), defecto distinto. Si se hacen a la vez, un solo worktree.
