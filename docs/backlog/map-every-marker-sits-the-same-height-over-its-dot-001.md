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

## Segunda vuelta (review del user, 20-09): el punto se veía pegado

> «Habría que separarlo solo un poco ya que en markers como Spot se ve bastante pegado y quiero
> dejar algo de espacio. El de gasolinera se ve bien creo que porque tenemos menos borde?»

Sí, y es medible: no es percepción, es que llevan **bordes de trabajos distintos**.

| Marcador | Cuerpo | Borde |
|---|---|---|
| Plaza | relleno con la rampa de frescura | **3,2 u** de `surface` (~2 dp) — el relleno ES el dato (verde→ámbar→rojo) y sobre tesela oscura un disco de color sin anillo se funde con el mapa |
| Gasolinera / parking | papel (`surface`) | **1 dp** del acento — el cuerpo ya contrasta solo, el borde sólo dice de qué es |

Por eso la gasolinera se ve más limpia: su cuerpo no tiene que defenderse del fondo.

### Qué se cambió, y qué NO

El aire deja de ser un residuo aritmético y pasa a ser una constante con nombre,
**`MARKER_DOT_GAP`**, que sube de **3,5 → 5** unidades (~0,9 dp más a 46 dp de ancho). El centro del
punto se **deriva** de ella (`MARKER_BODY_BOTTOM_Y + MARKER_DOT_GAP + SPOT_DOT_R`), así que ya no
hay literal que se pueda desincronizar — que es exactamente como los 3,5 de antes acabaron sin
autor.

- ⛔ **Se mueve el PUNTO, no el cuerpo.** El cuerpo sigue apoyado en `MARKER_BODY_BOTTOM_Y`. Bajar
  el punto lo acerca además al ancla real del marcador —(0.5, 1.0), el borde inferior del viewBox—,
  así que el aire se gana **sin alejar el punto de la coordenada que marca**. Subir el cuerpo habría
  hecho lo contrario.
- ⛔ **No se toca el borde de 3,2 de la plaza.** Es el número que el user sospechaba, y es un número
  viejo (de cuando el puck era más pequeño), pero adelgazarlo toca el marcador DEL PRODUCTO y su
  legibilidad sobre tesela oscura. Si tras ver el aire nuevo sigue pareciendo grueso, se baja a 2,4
  y se mira en device — pero de uno en uno, no a la vez.
- ⛔ **El tag del coche queda fuera**: otro viewBox (104 × 88) y otra familia (tarjeta + cúpula). Su
  separación de 4 sobre 104 de ancho ya es proporcionalmente menor que la de sus primos, y nadie se
  ha quejado de él.

⏳ **Sin captura del estado final**: no hay device conectado (`PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001`).
