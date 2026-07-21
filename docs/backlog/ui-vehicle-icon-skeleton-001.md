# UI-VEHICLE-ICON-SKELETON-001 — El icono del vehículo no parpadea al genérico mientras carga

> **Estado**: EN CURSO en rama `feature/UI-VEHICLE-ICON-SKELETON-001-loading-glyph` (off master).
> Origen: petición de campo — "en las modales de Home parpadea el coche genérico hasta que
> carga el vehículo, aunque ya lo tengamos seleccionado". Prioridad: P2 (pulido).

## Problema

Son **dos casos distintos** con el mismo síntoma (icono que "no es mi coche"):

### A · "el dato ya está, pero pintamos genérico" (no parpadea, está siempre mal)
Diálogos/sheets que hardcodean `Icons.Rounded.DirectionsCar` teniendo el vehículo disponible:
- `SetActiveConfirmDialog` (VehiclesScreen) — **RESUELTO en la rama VEH-ACTIVE-FENCE-001**
  (commit aparte: pasa carbody/size/color + `heroContent` con el pictograma real).
- `BluetoothRecommendationDialog` (VehicleRegistrationScreen ~:598) — tiene el vehículo recién
  guardado. **DIFERIDO** (ver abajo).
- `ConfirmationBottomSheet` (~:100) — `PapSheetLead.GenericIcon(DirectionsCar)`; revisar si sabe
  qué coche. **DIFERIDO**.

### B · "parpadeo a genérico mientras carga" (el flicker real) — RESUELTO aquí
El icono lo alimenta un estado que arranca vacío y se rellena de Room un frame después. `vehicleIconPainter`
cae al genérico solo cuando `carbody == null && size == null`, es decir cuando el vehículo aún no
resolvió. Clave: en `Vehicle`, `sizeCategory` es NO-NULL → **un `Vehicle` ya cargado nunca llega al
genérico**; el genérico es *siempre* un artefacto de carga, no un estado válido. Pero el resolutor
recibe `(carbody, size)` descompuestos y no puede distinguir "cargando" de "legacy sin carrocería".

## Solución (sistémica, un solo sitio)

Hacer explícita la distinción cargando/cargado en la capa del icono:
- **`PapShimmerBox`** (nuevo, `ui/components/PapShimmer.kt`): primitivo único de skeleton
  (pulso alpha 0.15→0.40 sobre `PapMotion.Breathe`). `PeekLocationSkeleton` (BrowsePeek)
  refactorizado para usarlo → una sola fuente de shimmer, cero duplicación.
- **`PapSheetLead.Vehicle`** gana `loading: Boolean = false`; `PapSheetLeadTile` (PapSheet.kt)
  pinta el skeleton cuando `loading`, el pictograma cuando no. Render centralizado en UN punto.
- Los 3 peeks con lead de vehículo pasan `loading = <vehículo> == null`
  (BrowsePeek aparcado + conduciendo, ParkingPeek, AddingParkingPeek). Es semánticamente correcto:
  toda sesión aparcada tiene `vehicleId` [[parking_vehicleid_invariant]], así que `null` = "aún no
  resuelto" = cargando (se resuelve solo al llegar de Room).

## Hecho en esta rama

- `PapShimmerBox` + refactor de `PeekLocationSkeleton`.
- `PapSheetLead.Vehicle(loading)` + render skeleton en `PapSheetLeadTile`.
- 4 leads de peek pasan `loading`.
- Dev Catalog: nueva variante "Peek · coche resolviéndose (skeleton icono)" + fix de la variante
  "parking seleccionado" (le faltaba `vehicles`, habría mostrado skeleton permanente).
- prod debug compila + tests verdes; mock compila.

## Diferido (fast-follow, documentado para no perderlo)

1. **Diálogos genéricos de master** (Problema A): aplicar el `heroContent`/pictograma a
   `BluetoothRecommendationDialog` y revisar `ConfirmationBottomSheet`. Bloqueado hasta que
   **VEH-ACTIVE-FENCE-001 mergee** (ese branch añade `heroContent` a `PapAlertDialog`; hacerlo aquí
   duplicaría el cambio y chocaría al rebasar).
2. **Marcadores de mapa** (`VehicleBadgeMarker`, `ParkedVehicleSummary`) y **puck de conducción**
   (`LocationActiveMarker`, `tripRender` arranca `IDLE`): también parpadean al genérico, pero un
   skeleton sobre un marker del mapa pide otro tratamiento (¿no dibujar el marker hasta resolver?).
   Menor prominencia que el peek.
3. **Pager de Vehículos** (`VehiclesScreen:363`): flicker de un frame al abrir; estable tras cargar.
4. **`UserParking`/`ParkedVehicleSummary`**: `sizeCategory` es nullable ahí (legacy) → el genérico
   podría ser legítimo. A futuro, resolver la talla del `Vehicle` dueño.

## Validación

Compila prod+mock, tests verdes. **Pendiente: validación visual en device** (el skeleton solo se
ve en el frame de carga real; en la galería mock la variante dedicada lo muestra permanente). iOS
pendiente (commonMain, sin código de fuente propio).
