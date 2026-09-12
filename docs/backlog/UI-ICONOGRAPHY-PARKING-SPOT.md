# UI-ICONOGRAPHY-PARKING-SPOT · Diferenciar parkings públicos y plazas comunitarias

**Estado:** ✅ Done · rama `feature/UI-ICONOGRAPHY-PARKING-SPOT` · worktree `../Paparcar-UI-ICONOGRAPHY-PARKING-SPOT`

## Problema
La letra `P` se usa actualmente para representar plazas libres y parkings públicos, mezclando una infraestructura fija con un reporte de la comunidad.

## Doctrina violada
La iconografía debe comunicar el concepto de la entidad de forma consistente en markers, listas, tabs y peek.

## Señales / datos disponibles
Los markers y las superficies cercanas comparten iconos definidos en `PaparcarIcons` y componentes de markers; hay que auditar todos sus consumidores.

## Diseño
Reservar `P` para parkings públicos. Representar las plazas libres con un usuario como icono central y un megáfono en la insignia superior derecha, reutilizando esos conceptos en todas las superficies.

## Criterio de éxito
Parkings públicos muestran siempre `P`; plazas libres muestran usuario + megáfono en marker, lista, tabs y demás UI correspondiente, con escalado correcto y sin iconos ambiguos.

## Consumidores auditados
- `PaparcarMapMarkers.kt`: `FreeSpotMarker` y `SpotPuckIcon` comparten la composición
  `Person` + `Campaign`; `PublicParkingMarker` usa exclusivamente `CarParkMark`.
- `PaparcarMapView.kt`: `CenterPinKind.Report` documentado como composición comunitaria;
  `CenterPinKind.Parking` conserva el `P`.
- `HomeNearbyLensRow.kt`: tab de plazas usa `CommunitySpot`; tab de parkings usa `CarParkMark`.
- `HomeSpotRows.kt`, `PapSheet.kt` y `SpotPeek.kt`: consumen el puck/composición comunitaria
  compartida; no mantienen un glyph `P` propio.
- `PaparcarMapMarkersPreviews.kt` y `StateGalleryScreen.kt`: reutilizan los composables de
  marker, por lo que muestran las variantes nuevas sin duplicar iconografía.
- `PaparcarIcons.kt`: eliminados los glyphs `P` de plaza (`SpotPin`, `SpotParkingP`,
  `SpotBayMark`); `CarParkMark` queda como única fuente del `P`.
