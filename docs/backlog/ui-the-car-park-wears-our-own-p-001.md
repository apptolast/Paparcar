# UI-THE-CAR-PARK-WEARS-OUR-OWN-P-001 · El parking público lleva NUESTRA P, y sólo hay una P

**Estado:** ✅ Done · en master · verificado en device (Oppo + Redmi) el 14-09-2026

## Problema

El parking público se dibuja hoy con `CarParkMark`: el volumen de un edificio con una P dentro. El
glifo tiene un día de vida — lo creó `8be9a576` (13-09-2026), el mismo commit que **retiró
`SpotParkingP`**, la P propia (letra Fredoka rellena, contraforma por winding) que hasta entonces
llevaban los pucks de plaza.

Dos cosas fallan, y la segunda es la grave:

1. **La casita gasta en dos ideas el único hueco de glifo de un marcador.** Comparativa renderizada
   a la misma caja óptica (36 u) en las cuatro superficies reales —
   `Documents/Paparcar/Markers 2026-09-14/parking-p-candidatas.png`: a 46 dp aún se lee, pero al
   62 % (distancia normal de mapa) y a 18 dp (la pestaña de carril) el volumen se come la letra y
   queda una casita con un borrón. Y lo que la casita dice — *instalación, no plaza* — **ya lo dice
   la silueta del marcador**: cuerpo de papel con borde verde frente al puck relleno de frescura de
   una plaza. Repite con la tinta lo que la forma ya resolvió, y lo paga en legibilidad.
2. **Hay DOS "P" en la app y ninguna es nuestra.** El código ya adjudicó el símbolo por escrito, dos
   veces, y ambas veces al parking público:
   - `MapControlButtons.kt:38` — *"⛔ The recenter button is NOT a 'P': `Icons.Rounded.LocalParking`
     is already spoken for as `PaparcarIcons.ParkingPlace`, the POI category for a public car park"*.
   - `OnboardingScreen.kt:219` — *"Aquí había una 'P', que es `PaparcarIcons.ParkingPlace` = la
     categoría de POI 'parking público'"*.

   O sea: la P está **reservada** para el parking público… y la capa de parkings públicos no la
   lleva (lleva la casita), mientras la categoría de POI `PlaceCategory.PARKING` sí lleva una P —
   pero la de **Material**. El símbolo reservado y la cosa para la que se reservó se han separado.

## Doctrina violada

- **Un glifo para toda la vida de un concepto** [SPOT-ICON-IS-THE-GIVEN-SPOT-HAND-HEART-001]: la
  plaza aprendió esta regla (mano+corazón en pestaña, puck, fila y peek). El parking público la
  incumple hoy: casita en su capa, P de Material en su categoría de POI.
- **Iconos, nivel 3 / marcas propias** (`PaparcarIcons`, comentario del bloque de infraestructura):
  *"⛔ No son Material: estos tres no son plumbing de UI, son los productos que la app enseña"*. La
  P de `PlaceCategory.PARKING` es Material, y pinta exactamente uno de esos productos.
- **Sistemas, no parches**: engordar la P dentro de la casita no arregla que haya dos símbolos para
  un concepto.

## Señales / datos disponibles

- `SpotParkingP` se recupera entera con
  `git show 8be9a576^:shared/src/commonMain/kotlin/com/rndeveloper/paparcar/ui/icons/PaparcarIcons.kt`
  (constante `GLYPH_P_PATH`, tres subpaths: asta, bol y contraforma por winding).
- Viene en **viewport 48×48 y con la tinta descentrada**: bbox medido 11,40–39,60 × 5,04–42,96. El
  sistema de iconos es 24×24 (`PaparcarIcons`, convenciones del bloque), así que hay que
  renormalizar, no copiar y pegar.
- `ParkingBay` (la plaza entre marcas viales, también propia) **no tiene ni un call site**: queda
  como icono muerto en el fichero. Se decide en este ticket si se retira.
- La P funciona sin traducir en los 9 locales del proyecto (ES/EN/IT/PT/FR/DE/NL/PL/RO usan todos la
  P de señalética), así que el cambio no toca strings.

## Diseño

**El invariante:** *un concepto, un glifo — y la P es del parking público, en toda la app.*

1. Vuelve `SpotParkingP` como **`PaparcarIcons.CarParkMark`** (mismo nombre de símbolo, para no
   tocar los call sites: lo que cambia es el dibujo, no la API). El `ImageVector` se reconstruye a
   **viewport 24×24 con la tinta centrada** a partir de `GLYPH_P_PATH` renormalizado, no con un
   `scale` en el call site: la caja del icono es propiedad del icono.
2. **`PlaceCategory.PARKING` pasa a la misma marca**, dejando de ser `Icons.Rounded.LocalParking`.
   Es el mismo objeto del mundo real que la capa de parkings (ambos salen de `amenity=parking` en
   Overpass), y los dos comentarios que reservan la P pasan a ser ciertos en vez de aspiracionales.
3. **La casita se retira**, no se guarda "por si hay sitio": un segundo dibujo para el mismo
   concepto es justo lo que este ticket cierra.
4. Revisión óptica obligatoria en los dos sitios donde el ojo de la P se cierra: **18 dp** (pestaña
   de carril) y **tema oscuro** (`PapGreen` sobre `PapInk`, donde el verde neón engorda el trazo).
   Si el ojo no sobrevive, se ajusta la contraforma en la geometría del icono — nunca subiendo el
   tamaño en un call site.

### Fuera de alcance (a propósito)

El cuerpo del marcador no se toca: el parking público sigue siendo el puck de papel con borde verde
que fijó [PARKINGS-ON-THE-MAP-001]. Este ticket cambia la tinta, no la silueta.

## Criterio de éxito

- Buscar "P" en la app devuelve **un solo dibujo**: el marcador del mapa, la fila de lista, la
  pestaña de carril, el peek y la categoría de POI enseñan el mismo glifo.
- Cero referencias vivas a `Icons.Rounded.LocalParking` fuera de su comentario de reserva.
- La P se lee a 18 dp y en oscuro (revisión en device o en el Dev Catalog, no sólo en preview).
- Dev Catalog + `PaparcarMapMarkersPreviews` en la MISMA tarea: el marcador de parking ya está en la
  galería (`StateGalleryScreen.kt:2654`), basta con que muestre el glifo nuevo; añadir la variante a
  18 dp si no está.
- Sin strings nuevos.

## Consumidores auditados

El barrido por `LocalParking` (no por `CarParkMark`) encontró **cuatro P vivas más** de las que
listaba la apertura: el peek del propio parking, la línea del explainer y dos demos que pintaban con
señalética de parking cosas que no lo son. La cuenta real era de seis sitios con P, no dos.

| sitio | qué pintaba | estado |
|---|---|---|
| `PaparcarMapMarkers.kt:1272` (`PublicParkingMarker`) | `CarParkMark` (casita) en caja de 36 u | ✅ cubierto — mismo símbolo, dibujo nuevo; el call site no se tocó |
| `HomePublicParkingRows.kt:63` (`PapIconTile`) | `CarParkMark` | ✅ cubierto |
| `HomeNearbyLensRow.kt:103` (pestaña de carril) | `CarParkMark` a ~18 dp | ✅ cubierto — es el tamaño crítico |
| `BrowsePeek.kt:299` (`HomeNearbyLens.PARKINGS`) | `CarParkMark` | ✅ cubierto |
| `PublicParkingPeek.kt:75` (**el peek del parking**) | `Icons.Rounded.LocalParking` | ✅ cubierto — el detalle del parking no llevaba su propia marca |
| `FirstStepExplainer.kt:250` (línea de parkings del explainer) | `Icons.Rounded.LocalParking` | ✅ cubierto — su comentario ya decía que la P es del parking |
| `PlaceCategory.PARKING` → `HomeSpotRows.kt:182` | P de Material | ✅ cubierto — **la segunda P**; pasa a la nuestra |
| `PeekTimeRowOptionsPreviews.kt:173,249` | una PLAZA con la P del parking | ✅ cubierto → `CommunitySpot` (mano + corazón) |
| `DetectionReadinessDesignPreviews.kt:134,156` | TU aparcamiento con señalética de parking público | ✅ cubierto → `DirectionsCar`, lo mismo que eligió el onboarding |
| `PaparcarMapMarkersPreviews.kt:175-178` y `StateGalleryScreen.kt:713-715` | el pin de ZONA demostrado con la P | ✅ cubierto → `zoneIconFor(ZoneIcon.HOME)` |
| `MapControlButtons.kt:38` | comentario que reserva la P; el FAB NO es una P | ✅ cubierto — el comentario ya es cierto y apunta a `CarParkMark` |
| `OnboardingScreen.kt:219` | comentario: el paso 2 lleva coche, no P | ✅ cubierto — sigue siendo coche |
| `PaparcarIcons.ParkingBay` | sin un solo call site | ✅ **retirado** — ver decisión abajo |
| `PublicParkingMarker` cuerpo (puck papel + borde) | la silueta dice "instalación" | ✅ exento — fuera de alcance a propósito |

## Decisiones tomadas durante la ejecución

- **`ParkingBay` se retira.** Cero call sites: era la plaza dibujada como hueco entre marcas viales,
  y la plaza pasó a la mano + corazón. Guardar un icono que nadie pinta es guardar una segunda
  respuesta a una pregunta ya contestada. Con él se fue el comentario del bloque que seguía
  anunciando "tres marcas propias" cuando quedaba una (la gasolinera nunca dejó de ser Material).
- **La caja del glifo es 20 de 24, no 24 de 24.** Es la mancha de los glifos Material del set, así
  que la P no canta al lado de un surtidor en la misma fila. En el marcador (caja de 36 u) eso deja
  la tinta en 30 u, un pelo MÁS grande que los 28,5 u de la casita que sustituye — medido, no
  estimado. Quien quiera más presencia en el mapa que cambie la caja del marcador, no el icono.
- **El relleno pasa de `EvenOdd` a winding no-cero.** No es un detalle: con `EvenOdd` el asta
  abriría un hueco donde cruza el bol y la P se partiría. Está escrito como ⛔ en el KDoc del icono
  y corregida la línea del encabezado del fichero, que prometía `EvenOdd` para todo el set.

## Revisión óptica

Sin móvil conectado, hecha con el mismo pipeline que reconstruyó la plantilla de markers: la P
normalizada (mismos `P_SCALE` / `P_DX` / `P_DY` del icono) a 46 / 32 / 24 / 18 dp en mdpi, xhdpi y
xxhdpi, en claro y en oscuro, más el puck a 46 dp. El ojo aguanta abierto en los doce casos,
incluido el peor (18 dp a 1×) y el oscuro, donde el neón engorda el trazo. **Pendiente de
confirmar en device** cuando haya uno enchufado: `Documents/Paparcar/Markers 2026-09-14/` no
sustituye a mirar la pestaña de carril con el pulgar encima.

## Origen

Comparativa de las tres candidatas (casita actual · nuestra P recuperada · P de Material) sobre el
mismo puck, mismo verde y misma caja óptica, en las cuatro superficies donde vive el glifo:
`Documents/Paparcar/Markers 2026-09-14/parking-p-candidatas.png` (generador `gen2.py`). Decisión del
user el 14-09-2026, tras la exportación de la plantilla de markers.
