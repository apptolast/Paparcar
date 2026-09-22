# UI-A-MARKER-ID-MUST-BRING-ITS-DRAWING-001 · un marcador cuyo id cambia tiene que traer su dibujo

**Estado:** ✅ Done · en master · `testAll` verde, `:app:compileMockDebugKotlin` y
`compileKotlinIosSimulatorArm64` OK · ⏳ pendiente de verlo en device (empezar a conducir con Home
abierto, sin reiniciar)

Cierra **issue #127** («Home estando abierto, no cambia estado a driving») y la mitad visible de
**#128** (el origen de la ruta no enseña su punto de partida).

## Problema

El user, issue #127: *«en Home, cuando empiezo a conducir el estado no muestra la ruta ni el
vehículo como mi icono en el mapa, hasta que reinicio la app y ya sí aparece»*. Varias veces, no
siempre.

`PaparcarMapView` construye **dos** cosas memorizadas por separado:

| qué | dónde | claves |
|---|---|---|
| la lista de marcadores (cada uno con su `contentId`) | `remember(...)` | …, `themeKey`, **`watchKey`**, … |
| el mapa de dibujos `contentId → @Composable` | `remember(...)` | …, `themeKey`, `puckMeta`, … **sin `watchKey`** |

Y el watch entra en el id, porque el bitmap se cachea por id y el halo lleva el color de la
vigilancia: `departure_as`, `arrival_bt`, `location_active_SEDAN_def_as`.

El watch del viaje llega por `HomeState.drivingMeta` + la lista de vehículos; el puck llega por
`HomeTripController`, que es **otra tubería**. Por construcción no cambian en el mismo frame. Cuando
el watch se resuelve DESPUÉS de aparecer el puck:

1. frame A — aparece el puck (`puckMeta`), el mapa de dibujos se rehace y registra
   `location_active_…_mv` (el watch aún no ha llegado);
2. frame B — llega el watch: la lista de marcadores (que sí lo tiene de clave) pide
   `location_active_…_as`; el mapa de dibujos **no se rehace**, sigue teniendo el `_mv`.

Y un id sin dibujo no cae en un dibujo por defecto. En el fork de kmp-maps:

- marcador **vivo** (el puck del coche) → `customMarkerContent[live.contentId] ?: return`: **no se
  pinta nada**;
- marcador **estático** (el punto de origen / de llegada del viaje) → pin rojo de Google por defecto.

Se queda así hasta que algo más invalide el mapa de dibujos (llegan plazas nuevas, aparcas, cambias
de tema…). Reiniciar la app recompone de cero con las dos tuberías ya resueltas — por eso «reinicio
y ya sí aparece».

## Doctrina violada

**Sistemas, no parches**, y de la peor manera: es la **segunda vez** que el id y su dibujo se
separan. La primera fue la zona que aloja un coche aparcado
([MAP-A-PARKED-CAR-INSIDE-A-ZONE-WEARS-THE-ZONE-001]): el marcador pedía un id construido sin zonas
y el handler registraba uno construido con ellas → pin rojo de Google encima del badge. Aquel se
cerró resolviendo la zona **una sola vez** para el id y para el bitmap.

El carril de las zonas ya aprendió además la otra mitad de la lección y registra **cuatro** bitmaps
por zona (apagada × con nombre) *«o el marcador se queda sin dibujo el fotograma en que se cruza el
umbral»*. Las plazas registran 18 variantes por lo mismo. Los tres marcadores del viaje —origen,
llegada y puck— eran los únicos que registraban **el vestido de ahora**.

## Señales / datos disponibles

- El código: `PaparcarMapView.kt` — `markers` lleva `watchKey` de clave, `customMarkerContent` no.
- El fork (`core-android-0.9.1-puck6`, `Map.kt:72`): `?: return` para el marcador vivo,
  `DefaultPin` para el estático.
- La captura de #128: la traza verde del viaje dibujada y **sin** el punto de origen en su extremo.

## Diseño

**El conjunto de vestidos de un marcador es CERRADO, así que el mapa de dibujos los registra todos y
cuál lleve el marcador deja de poder importar.** Es el patrón que ya usan las zonas (named ×  dim),
las plazas (18) y el aparcamiento de fallback (exacto × aproximado); los tres marcadores del viaje
se alinean con él.

- `TRIP_WATCH_VARIANTS` — la lista `(clave, watch)`, derivada de `VehicleWatch.entries + null` y
  deduplicada por clave: son **tres** (`mv`, `bt`, `as`; `Off` comparte bitmap con "sin viaje").
- `tripAccentFor(watch)` — el color se resuelve **dentro** del dibujo, que es `@Composable`, no
  capturado desde el cuerpo: un color guardado en el mapa memorizado es la misma deriva, en el
  pigmento en vez de en el id.
- El puck registra una entrada por vestido para el carbody/color actuales (que sí son clave del
  mapa, vía `puckMeta`).

⛔ Lo que **no** se hace: añadir `watchKey` a las claves del mapa de dibujos. Arregla este caso y
deja viva la clase entera de bug — la siguiente pieza que entre en un id y no en las dos claves
vuelve a apagar un marcador, en silencio.

## Criterio de éxito

- `MapMarkerIdsTest.should_dressEveryWatch_when_theTripMarkersAreRegistered` — todo `VehicleWatch`
  (y el nulo) tiene su clave dentro del conjunto registrado, y el conjunto no colapsa.
- El corpus del test recorre `TRIP_WATCH_VARIANTS`, la MISMA lista que usa el render: un vestido
  nuevo entra a la vez en los dos sitios.
- En campo (#127): empezar a conducir con Home abierto pinta el coche y su traza **sin reiniciar**.
- En campo (#128): la traza del viaje arranca en su punto de origen.

## Consumidores auditados

Todo `contentId` que emite `PaparcarMapView`, contra lo que registra el mapa de dibujos:

| id | lo que varía | veredicto |
|---|---|---|
| `zoneContentId` | `zoneNamed` (sólo clave de la lista) | ✅ cubierto — registra named × dim |
| `freeSpotContentId` | tier (`freshnessMinute`), `approximate` | ✅ cubierto — 18 variantes |
| `fallbackParkingContentId` | `parkingZoneRadiusMeters` | ✅ cubierto — exacto × aproximado |
| `vehicleBadgeContentId` | `zones` (contención) | ✅ cerrado en [MAP-A-PARKED-CAR-INSIDE-A-ZONE-WEARS-THE-ZONE-001] |
| `fuelStationContentId` | precio y etiqueta | ✅ `fuelStations` + labels son clave de **las dos** |
| `publicParkingContentId` | tarifa | ✅ ídem |
| `parkingAskContentId` | `themeKey` | ✅ clave de las dos |
| `departureContentId` / `arrivalContentId` | **`watchKey`** | 🔴 **el bug** → cerrado aquí |
| `locationActiveContentId` (puck) | **`watchKey`** | 🔴 **el bug** → cerrado aquí |

## Fuera de alcance (queda abierto)

- **El remate de la polilínea** (#128, la otra mitad): el fork llama al `Polyline` de maps-compose
  sin `startCap`/`endCap`/`jointType`, así que Google usa `ButtCap` + junta en inglete — la línea
  termina en un filo y una vuelta en U dibuja una púa. Con el punto de origen otra vez visible el
  arranque ya se lee, pero el remate sigue siendo cuadrado: arreglarlo es tocar **nuestro fork** de
  kmp-maps y publicar versión. Ticket propio.
- **iOS**: el carril es el mismo composable de `commonMain`, así que el arreglo viaja tal cual; el
  renderizador de polilíneas de iOS (cap por defecto de `MKPolylineRenderer`) se mira en ese ticket.
