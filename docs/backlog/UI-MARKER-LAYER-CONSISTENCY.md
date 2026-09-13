# UI-MARKER-LAYER-CONSISTENCY · Unificar markers, listas y capas cercanas

**Estado:** ✅ Done · rama `bugfix/UI-MARKER-LAYER-CONSISTENCY` · worktree `../Paparcar-UI-MARKER-LAYER-CONSISTENCY`

## Problema
El marker de parking público no comparte el icono de la lista. El marker de gasolinera pisa
visualmente el punto geográfico, el combustible aparece en minúsculas y su icono es demasiado
pequeño. Además, la selección de parkings no atenúa de forma coherente el resto de markers y al
volver desde Vehículos puede quedar la lista de parkings sin su capa equivalente en el mapa.

## Doctrina violada
Una misma capa cercana debe tener una única fuente visual y una única proyección de datos: lista,
peek y mapa no pueden contradecirse. La selección debe subordinar los elementos no seleccionados
igual en spots, gasolineras y parkings.

## Señales / datos disponibles
`PaparcarMapView` ya recibe las tres capas y genera bitmap por `contentId`; `HomeState.toMapSlice`
decide qué capa pinta el mapa, mientras las lanes usan sus stores propios. `dimSpots` ya expresa el
estado global de selección, pero fuel y parkings no lo incorporan a sus handlers.

## Diseño
Usar `PaparcarIcons.CarParkMark` también en la fila de parking público. Separar el punto geográfico
de la cápsula de gasolinera, normalizar el label de combustible a mayúsculas y aumentar ligeramente
el glyph. Hacer que los IDs/handlers de fuel y parking tengan variantes normal/dim/selected,
igual que spots, manteniendo la selección a opacidad completa. Garantizar que la proyección usada
por la lista activa sea la misma que entrega markers al mapa al recrear Home.

## Criterio de éxito
El icono `P` de parking es idéntico en lista y marker; la cápsula de gasolinera queda por encima
del punto sin pisarlo, muestra combustible en mayúsculas y un glyph mayor; al seleccionar un
parking, el resto de parkings y gasolineras se atenúa y el seleccionado no; al volver a Home,
lista y mapa muestran el mismo conjunto de parkings.

## Consumidores auditados
- `HomePublicParkingRows.kt` y `PaparcarMapMarkers.kt`: ambos usan `PaparcarIcons.CarParkMark`.
- `FuelStationMarker`: etiqueta con primera letra en mayúscula, glyph de 16 dp y cápsula
  replegada sobre el punto sin invadirlo.
- `MapMarkerIds.kt` y `PaparcarMapView.kt`: fuel y parking tienen IDs/handlers `nrm`, `dim` y
  `sel`; `dimSpots` forma parte de la clave de bitmap.
- `HomeSlices.kt`: la proyección de parking de lista y mapa sigue siendo la unión deduplicada de
  store de lane + store de cámara; cubierta por `HomeSlicesTest`.
