# MAP-NEARBY-MARKERS-GREEN-CONTRASTS-ON-LIGHT-MAP-001 · Los markers de parkings y gasolineras contrastan en el mapa claro

**Estado:** ✅ Done — en master (PR [#20](https://github.com/apptolast/Paparcar/pull/20), aprobado sin cambios) ·
GitHub issue [#18](https://github.com/apptolast/Paparcar/issues/18)

## Problema
Issue #18: en tema claro los markers de parkings y gasolineras llevan un verde muy claro que no
contrasta con el mapa; en tema oscuro sí contrasta.

`PublicParkingMarker` y `FuelStationMarker` (`ui/components/PaparcarMapMarkers.kt`) usaban
`PapGreen` (#25F48C) en los DOS temas para glifo, borde y punto de suelo. Es el verde de marca del
tema OSCURO: sobre baldosas claras y un borde fino al 55 % casi desaparece.

## Doctrina violada
[UI-COLOR-DOCTRINE-001] / `COLOR-SYSTEM.md`: el verde de marca se resuelve por tema (`PapGreen` en
oscuro, su pierna clara en claro). Estos dos markers eran los únicos que usaban la pierna oscura
fija. Además la fila de la lista (`HomePublicParkingRows`, `HomeFuelStationRows`) ya tintaba con
`PapColor.actionText`, así que pin y fila eran de dos verdes distintos.

## Diseño
Un solo sitio: `nearbyMarkerGreen()` = `PapColor.actionText`, el rol que ya usan las filas.
- Oscuro → `colorScheme.primary` (= `PapGreen`, sin cambio: el issue dice que ahí se ve bien).
- Claro → `PapGreenTextLight` #237A46 (5,3:1 sobre blanco), el verde de marca «legible».
- Sin token nuevo → sin fila nueva en `COLOR-SYSTEM.md` ni cambios en `ColorGuardrailTest`.
- Caché de bitmaps: los content ids de ambos markers ya llevan `themeKey`, así que cambiar de tema
  re-renderiza. El estilo del mapa sigue al tema (`MapStyleMode.AUTO`, nadie lo fija a mano).

## Criterio de éxito
- ✅ Galería «Home · dónde repostar → Marcadores de la capa» (emulador Pixel 8 Pro): en claro el
  glifo, el borde y el punto salen en #237A46; en oscuro siguen en el neón.
- ✅ `ColorGuardrailTest`, `assembleMockDebug`, `compileProdDebugKotlin`.
- ⏳ No visto sobre baldosas reales: el emulador devolvió «Authorization failure» del SDK de Maps en
  esta sesión (clave restringida), así que la comparación con el mapa queda para device.

## Consumidores auditados
| Consumidor | Estado |
|---|---|
| `FuelStationMarker` (punto, borde, glifo) | `nearbyMarkerGreen()` |
| `PublicParkingMarker` (punto, anillo, glifo) | `nearbyMarkerGreen()` |
| Filas de parkings/gasolineras | ya en `PapColor.actionText` — ahora iguales al pin |
| `SpotPalette` (plazas), coche propio, `ZoneMarker` | exentos — tienen su propia historia de color |
| `PaparcarMapView` indicador de modo reporte (`PapGreen`) | fuera de alcance — no es un marker de esta capa |

## Preguntas que quedaron sin respuesta
El PR se aprobó sin contestarlas; se conserva el comportamiento implementado.
1. Con el mapa en **satélite** y la app en tema claro, el verde oscuro contrasta menos que el neón
   sobre la foto. ¿Queremos resolver el verde por tipo de mapa además de por tema?
