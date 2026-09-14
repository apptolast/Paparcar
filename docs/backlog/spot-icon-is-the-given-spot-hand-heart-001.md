# SPOT-ICON-IS-THE-GIVEN-SPOT-HAND-HEART-001 · La plaza libre se dibuja con la mano + corazón de «plaza cedida»

**Estado:** ✅ Done — en master (PR [#21](https://github.com/apptolast/Paparcar/pull/21), aprobado sin cambios) ·
GitHub issue [#15](https://github.com/apptolast/Paparcar/issues/15)

## Problema
Issue #15: cambiar el icono de la plaza libre, en todas sus partes (lista, markers, seleccionado,
modal…), por el que usamos para plazas cedidas (mano + corazón).

La plaza libre llevaba `Icons.Rounded.Person` desde [UI-ICONOGRAPHY-PARKING-SPOT], en DOS sitios que
no se hablaban: `PaparcarIcons.CommunitySpot` (solo la pestaña del carril) y un `Icons.Rounded.Person`
suelto dentro de `CommunitySpotOverlay` (puck del mapa, lista, peek, pin de reporte).

## Doctrina
- Iconos nivel 2 (Material Rounded, tintado) sobre el puck nivel 3 (Canvas): se mantiene el patrón,
  solo cambia el glifo. Nunca una «P» para una plaza [COPY-SPOT-IS-NOT-A-PARKING-001].
- Un glifo = un significado: la mano + corazón (`VolunteerActivism`) ya significa «plaza cedida» en
  el tile de cifras; ahora la plaza libre ES esa plaza cedida, así que el significado se amplía sin
  chocar con otro.

## Diseño
- `PaparcarIcons.CommunitySpot` = `Icons.Rounded.VolunteerActivism`, y `CommunitySpotOverlay` lo lee
  de ahí: **una sola fuente** para pestaña, markers (frescura, seleccionado, en camino, zona), pin de
  reporte, fila de lista y lead del peek.
- El glifo llena más su caja que la persona: baja de 28 a 26 unidades, centrado en el puck; no pisa
  el badge del megáfono ni el anillo TTL (constantes `COMMUNITY_GLYPH_*`).
- Se mantiene el badge del megáfono (anuncio) y el megáfono de las ACCIONES de avisar/liberar.
- Comentarios que describían «community person» / «person badge» corregidos. El «person badge» de
  los reportes manuales ya no existía (`tierVisual` ignora `isManual`); los comentarios lo decían.
- La caché de bitmaps no necesita claves nuevas: el glifo es fijo en compilación.

## Criterio de éxito
- ✅ Galería «Mapa · marcadores → Showcase» (emulador Pixel 8 Pro): FRESH/RECENT/STALE/MANUAL/
  seleccionado con mano + corazón, sin choque con el badge.
- ✅ Home (mock): filas de plazas con el puck nuevo, también en camino (azul).
- ✅ Guardarraíles, `assembleMockDebug`, `compileProdDebugKotlin`.

## Consumidores auditados
| Consumidor | Estado |
|---|---|
| `CommunitySpotOverlay` → `FreeSpotMarker`, `SpotPuckIcon`, `ReportCenterPin` | glifo nuevo |
| Filas (`HomeSpotRows`), lead del peek (`PapSheetLead.CommunitySpot`) | vía `SpotPuckIcon` |
| Pestaña «Free spots» (`HomeNearbyLensRow`, icono solo sin cifra) | vía `PaparcarIcons.CommunitySpot` |
| Tile «plazas cedidas» (`VehicleStatsBlock`) | ya era `VolunteerActivism`, sin cambio |
| Megáfono (`Campaign`) en badge, tarjeta de avisar, peek de reporte, diálogo de liberar, onboarding | sin cambio — es la ACCIÓN de avisar |
| `Icons.Rounded.Person` en auth, ajustes y permisos | exentos — es el usuario, no una plaza |
| Clúster de plazas | exento — solo cifra |

## Preguntas que quedaron sin respuesta
El PR se aprobó sin contestarlas; se conserva el comportamiento implementado.
1. ¿«Todas sus partes» incluye también el **megáfono** del badge del puck? Lo he mantenido: indica
   «anunciada», y el megáfono sigue siendo el icono de la acción de avisar.
2. El onboarding (tarjeta «Una plaza para otro») y el checklist usan el megáfono para la acción; no
   los he cambiado.
