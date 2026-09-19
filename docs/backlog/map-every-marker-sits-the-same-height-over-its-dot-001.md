# MAP-EVERY-MARKER-SITS-THE-SAME-HEIGHT-OVER-ITS-DOT-001 · Todo marcador se apoya a la misma altura sobre su punto

**Estado:** 🔵 En revisión · rama `feature/MAP-EVERY-MARKER-SITS-THE-SAME-HEIGHT-OVER-ITS-DOT-001` ·
worktree `../PaparcarKMP-issue45` · cierra la **issue #45**

## Problema

Pedido por el product owner (#45): el marcador de gasolinera tiene «el container principal muy
separado del punto que marca su lugar». Pide alinear ese rasgo en todos los marcadores tomando como
referencia el de parking o el de plaza.

## Medida

Todo en unidades del viewBox de cada marcador (el de la familia del puck es 72 × 82):

| Marcador | Borde inferior del cuerpo | Borde superior del punto | Separación | Sobre el ancho |
|---|---|---|---|---|
| Plaza (`FreeSpotMarker`) | 63 (disco: centro 34 + radio 29) | 66,5 | **3,5** | 4,9 % |
| Parking (`PublicParkingMarker`) | 63 (mismo disco) | 66,5 | **3,5** | 4,9 % |
| Coche (`VehicleBadgeMarker`, viewBox 104 × 88) | 72 | 76 | **4** | 3,8 % |
| **Gasolinera (`FuelStationMarker`)** | ~48 (alto mínimo de la cápsula, colgada del borde SUPERIOR) | 66,5 | **~18,5** | **25,7 %** |

Los tres primeros ya estaban de acuerdo: la separación es un **rasgo de la familia**, no un número
de cada marcador. La cápsula de la gasolinera era la única que **colgaba de arriba**, así que su
distancia al punto la decidía su propio alto — y con una segunda línea (el nombre del combustible)
cambiaba otra vez. Cinco veces la separación de sus hermanos se lee como un globo suelto encima de
un punto, no como un marcador.

## Diseño

- Constante compartida `MARKER_BODY_BOTTOM_Y = 63f` — *la línea sobre la que se apoya el cuerpo de
  cualquier marcador de la familia del puck*, documentada con la medida de arriba.
- `FuelStationMarker` pasa a `Alignment.BottomCenter` con un `padding(bottom = (82 − 63) · s)`: la
  cápsula se **apoya** en esa línea en vez de colgar de la de arriba. Una cápsula de una línea y una
  de dos se apoyan ahora en el mismo sitio.
- Nada más cambia: ni el tamaño, ni el color, ni el punto, ni el ancla `(0.5, 1.0)` con la que
  kmpmaps clava el marcador en su coordenada.

## Criterio de éxito

- La cápsula de gasolinera y el puck de parking, uno al lado del otro, tienen la misma pinza entre
  cuerpo y punto.
- Una gasolinera con precio + combustible (dos líneas) no se separa más del punto que una sin precio.

## Consumidores auditados

| Sitio | Decisión |
|---|---|
| `FuelStationMarker` | ✅ se apoya en la línea |
| `FreeSpotMarker` / `PublicParkingMarker` | ✅ ya estaban ahí — son la referencia |
| `VehicleBadgeMarker` | ❌ otro viewBox y otra familia (tag + cúpula), y su proporción ya coincide |
| `ZoneMarker` | ❌ desde `UI-A-ZONE-IS-AN-AREA-NOT-A-PIN-001` es un rótulo de área: no tiene punto |
