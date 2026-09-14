# UI-A-ZONE-IS-AN-AREA-NOT-A-PIN-001 · La zona deja de disfrazarse de marker y pasa a ser rótulo de área

**Estado:** ✅ Done · en master · ⏳ quedan DOS números por calibrar en device (ver abajo)

## Problema

`ZoneMarker` está construido como un marker sin serlo, y la lámina de markers exportada el
14-09-2026 (`Documents/Paparcar/Markers 2026-09-14/`) lo enseña de un vistazo: junto a la familia
entera, es el único objeto del mapa que no se sostiene sobre nada. Cinco hechos, todos en el código:

1. **Es el único centrado.** Se registra con ancla `(0.5, 0.5)`; el coche, la plaza, el parking, la
   gasolinera y el cluster van con `(0.5, 1.0)`. Su cápsula no pisa la coordenada: la tapa.
2. **No tiene punto de suelo ni sombra.** Todos los demás comparten `drawSpotShadow` +
   `drawSpotDot` (`PaparcarMapMarkers.kt`) — helpers que la zona no llama. En una familia donde el
   punto ES el "estoy aquí", la zona no dice dónde está.
3. **La cápsula significa dos cosas a la vez.** `FuelStationMarker` copió la cápsula de `ZoneMarker`
   a propósito ("Same capsule grammar as [ZoneMarker] so the map's labels read as one family") y le
   añadió punto y sombra. Hoy la misma forma es *un sitio en este punto* (gasolinera) y *el centro
   de un área* (zona). Una forma, dos significados, en la misma capa y a la vez.
4. **Nivel de icono equivocado para la superficie.** El glifo de zona es Nivel 2 (Material Rounded
   plano, 14 dp, tinte liso) sobre una capa donde todo lo demás es Nivel 3 (ilustración con masa y
   borde de papel). Sobre teselas es el objeto más débil de la pantalla.
5. **Tercer verde en el mismo encuadre.** `zoneIdentityColor(false)` = `colorScheme.primary`
   (#009F5E) convive con #05876D (vigilancia activa del coche) y #4FA80A (plaza fresca). Tres
   verdes, tres significados distintos, y uno de ellos pintando un NOMBRE.

## Doctrina violada

- **Color — identidad por MÉTODO, estado en texto** [UI-COLOR-DOCTRINE-001]: *el glifo lleva el
  color; el nombre queda en `onSurface`*. La zona pinta el nombre… bueno, en `onSurface` (correcto),
  pero rodea el conjunto de un borde de marca que lo lee como sujeto verde. Y sobre todo: gasta el
  verde de marca en un objeto que ni es acción ni es vigilancia.
- **Un esqueleto, no una re-implementación** [UI-LIST-ITEM-001, en espíritu]: la capa de markers ya
  tiene helpers compartidos y la zona los esquiva, así que "no encaja" es literal en el fuente.
- **Sistemas, no parches**: engordar el glifo o subir el contraste del borde no arregla que la zona
  esté en la clase equivocada.

## Señales / datos disponibles

- El **anillo nativo ya existe** y es el héroe declarado desde [ZONE-AREA-001]:
  `PaparcarMapView.kt:1552 zoneCircle(...)`, dibujado como `Circle` nativo bajo los markers, con las
  mismas alphas para la zona guardada y para la preview de `AddingZone`.
- El **zoom ya está disponible** en la vista (`currentZoom`, `PaparcarMapView.kt:1837`) y ya se usa
  para decidir agrupación (`clusterThresholdDeg(zoom)`), así que gatear la etiqueta por tamaño en
  pantalla no exige fontanería nueva.
- `zoneIdentityColor` / `zoneRingColor` (`ui/theme/ZoneIdentity.kt`) son ya **un solo resolver** con
  dos piernas, así que el cambio de identidad se hace en un sitio.
- El marcador se cachea por `zoneContentId(zone.id, isPrivate, dim)`: cualquier variante nueva
  (p. ej. "sin nombre") tiene que entrar en esa clave o se servirá el bitmap del otro estado.

## Diseño

**El invariante:** *el mapa tiene dos clases de objeto, y cada una tiene UN vestido.*

| clase | qué es | ancla | suelo | caja | ejemplos |
|---|---|---|---|---|---|
| **Puntual** | ocupa una coordenada | `(0.5, 1.0)` | punto + sombra | sí (puck / etiqueta / cápsula) | coche, plaza, parking, gasolinera, cluster |
| **Ámbito** | califica una región | `(0.5, 0.5)` | ninguno | **ninguna** | zona |

Eso se escribe en el KDoc de cabecera de `PaparcarMapMarkers.kt` — que hoy sigue diciendo
"three-marker family" cuando hay doce — y deja de ser una convención que cada marcador nuevo
reinventa.

Consecuencias concretas (opción A elegida por el user el 14-09-2026):

1. **`ZoneMarker` → rótulo de área.** Fuera `background` y `border`: glifo + nombre, sin píldora.
   La legibilidad sobre teselas la da un **halo de papel** (`TextStyle.drawStyle = Stroke(...)` en
   `surface` por debajo, relleno en `onSurface` por encima), que es lo que hace la cartografía con
   los nombres de barrio y parque. Sin caja no puede competir con un puck: no es del mismo mundo.
2. **El anillo sube a sujeto.** Es quien lleva el color de identidad y quien dice pública/privada.
   El nombre queda en `onSurface` y el glifo se queda el tinte: el verde de marca deja de pintar
   texto.
3. **Lo privado se dice en el anillo** (trazo discontinuo, sobre el `outline` que ya usa) **+ el
   candado pegado al glifo**, no suelto al final de una píldora que ya no existe.
4. **Aparición por zoom.** La etiqueta completa sólo se dibuja cuando el anillo mide más de
   `ZONE_LABEL_MIN_RING_PX` en pantalla; por debajo colapsa a glifo solo. El cálculo
   (radio en metros × zoom × latitud → px) vive en una **función pura testeable**, no dentro del
   composable.
5. **La cápsula se queda como marca EXCLUSIVA de lo puntual con cifra** — la gasolinera. No se toca
   `FuelStationMarker`: la ambigüedad se resuelve retirando al impostor, no cambiando al legítimo.
6. **`ZoneCenterPin` hereda el mismo rótulo**, con lo que la familia de pins de colocación vuelve a
   cumplir su propia regla ("el pin que colocas ES el marker que quedará"): hoy es un glifo suelto
   que no se parece ni a lo que hay ni a lo que habrá.

### Pregunta abierta (para la sesión de diseño, no se decide aquí)

Al sacar el nombre del verde quedan todavía tres verdes en escena, uno de ellos en el glifo de zona.
¿Debería la zona ir SIEMPRE en `onSurfaceVariant` — pública y privada — y reservar el verde de marca
para acción y vigilancia? Se deja anotado; cambiarlo es tocar `zoneIdentityColor`, que también pinta
el chip de cabecera, y eso es otro barrido.

## Criterio de éxito

- En una escena con zona + coche aparcado + plazas: **ningún objeto sin punto de suelo lleva caja
  opaca**, y el nombre de la zona no se pinta en ningún verde.
- La etiqueta colapsa a glifo cuando el anillo baja del umbral, y vuelve al acercar.
- Test unitario de la función pura de visibilidad (`should_hideLabel_when_ringIsSmallerThanThreshold`
  y su simétrico), sin ceremonia de composable.
- `ColorGuardrailTest` y `TypographyGuardrailTest` siguen verdes (el rótulo usa rol, no `fontSize`).
- Dev Catalog en la MISMA tarea: variantes pública / privada / nombre largo / bajo umbral en
  `StateGalleryScreen.kt`, en paridad con `PaparcarMapMarkersPreviews.kt`.
- Sin strings nuevos (el rótulo muestra el nombre que el usuario escribió).

## Consumidores auditados

| sitio | qué asume | estado |
|---|---|---|
| `PaparcarMapView.kt` (`zoneHandlers`) | `ZoneMarker` cabe en un bitmap cacheado por `zoneContentId` | ✅ cubierto — `zoneContentId` gana la pierna `named`, y se registran **cuatro** variantes por zona (apagada × con nombre) para que el id que elija la lista exista ya |
| `PaparcarMapView.kt` (lista de marcadores) | la lista se rehace con `zones`/`dimSpots` | ✅ cubierto — entra `zoneNamed` como clave: se reconstruye al CRUZAR el umbral, no en cada tic de zoom |
| `PaparcarMapView.kt` (`CenterPinKind.Zone`) | el pin de zona es un glifo suelto | ✅ cubierto — es el mismo rótulo, sin nombre todavía |
| `PaparcarMapView.kt` (`zoneCircle`) | el anillo es fondo, la etiqueta es el sujeto | ✅ cubierto — se invierte: trazo 0.30 → 0.42 · ⛔ el discontinuo **no se pudo hacer**, ver abajo |
| `ui/theme/ZoneIdentity.kt` | dos piernas, un resolver | ✅ cubierto — sigue siendo el único sitio, sin tocar |
| `HomeZoneChips.kt:83` (chip de cabecera) | usa `zoneIdentityColor` | ✅ exento — superficie de lista, no del mapa; sólo cambiaría si se resuelve la pregunta abierta |
| `StateGalleryScreen.kt` · `PaparcarMapMarkersPreviews.kt` | galería y previews espejo | ✅ cubierto — y con **las dos caras del umbral**, no sólo la del nombre |
| `MapMarkerIdsTest.kt` | enumera los ids de zona para probar que son distintos | ✅ cubierto — ahora recorre también `named` |
| `FuelStationMarker` | tomaba PRESTADAS `ZONE_LABEL_BG_ALPHA`, `_BORDER` y `_BORDER_ALPHA` | ✅ cubierto — las tres se mudan a `FUEL_LABEL_*`: la cápsula deja de ser compartida y pasa a ser suya, que era el fondo del problema |

## Lo que NO se pudo hacer, y por qué

**El anillo de la zona privada no puede ir discontinuo.** El círculo es una primitiva NATIVA del mapa
(`Circle` de kmp-maps 0.9.1-puck4, nuestro fork) y su API entera es `center`, `radius`, `color`,
`lineColor` y `lineWidth` — no hay patrón de guiones. Fingirlo con un `Polygon` de arcos sería
dibujar a mano el círculo geodésico que la librería ya calcula, y ahí es donde empiezan las
aproximaciones Web-Mercator que [ZONE-AREA-001] retiró a propósito.

Lo privado se sigue diciendo con sus dos piernas de siempre —tinte neutro (anillo y glifo) + el
candado, ahora pegado al glifo— así que el contrato de
[UI-A-PRIVATE-ZONE-IS-PRIVATE-ON-EVERY-SURFACE-001] queda entero. Si alguna vez se quiere el
discontinuo, es un ticket en el FORK (`lineDashPattern` en `Circle` → `PolylineOptions.pattern` en
Android), no aquí.

## De propina: el "+" de la fila de chips

Reportado por el user al ver la fila: el chip de añadir quedaba **separado** del resto. El hueco era
el mismo 8 dp que entre los demás — lo que cambiaba era el ÓPTICO: el "+" era un cuadrado de 34, es
decir un círculo exacto, y un casquete redondo se curva alejándose de su vecino, así que deja más
aire del que mide. Ahora es una **píldora de 42×34** con el mismo padding horizontal (12 dp) que un
chip de zona da a su glifo, así que sus casquetes tienen el mismo radio que los de al lado. Es la
proporción del "+" de Vehículos (`PaparcarAddChip`): se copia la proporción, no el componente,
porque este flota sobre el mapa y necesita el contrato de cristal + sombra de FAB.

Se lleva por delante el inset animado de 4 dp que existía sólo para corregir el círculo al
expandirse: el glifo queda a 12 + 9 = 21 dp del borde —donde está el icono de un chip de zona— y
ahora en LOS DOS estados. Una geometría, no dos.
[UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001] [UI-THE-ADD-ZONE-CHIP-IS-ONE-CHIP-IN-EVERY-STATE-001]

## Pendiente de calibrar en device

Dos números salen de la fórmula, no del mapa, y ninguna preview los puede juzgar:

- `ZONE_LABEL_MIN_RING_DP = 48f` — el umbral. Con el radio típico (≈80 m) cae cerca del zoom 16.
- `ZONE_CIRCLE_STROKE_ALPHA = 0.42f` — cuánto pesa el anillo ahora que es el sujeto.

Se miran juntos y en el mismo viaje: alejar hasta que el nombre se vaya, acercar hasta que vuelva, y
mirar el anillo sobre tesela clara y sobre satélite.

## Origen

Exportación de la plantilla de markers del 14-09-2026 (lámina reconstruida 1:1 desde el código, en
`Documents/Paparcar/Markers 2026-09-14/`, generador `gen.py` incluido). El user eligió la opción A
—rótulo de área— frente a la opción B (hacer de la zona un marker puntual de pleno derecho, con
suelo y sombra, asumiendo que competiría con las plazas).
