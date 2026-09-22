# MAP-A-RING-YOU-CANNOT-SEE-IS-TWO-BUGS-001 · El anillo de zona no se ve, y no es por una sola razón

**Estado:** ✅ Done · mergeado en master el 22-09-2026

## Problema

Issue **#78**, que describe dos cosas seguidas y son dos bugs distintos:

1. *«El área circular no aparece al entrar la modal de zonas, no podemos ver el radio hasta que lo
   colocamos.»*
2. *«Cuando se establece el círculo de área en el mapa tiene un relleno de su color menos opaco; en
   iOS sólo se pinta el borde.»*

Van juntos en un ticket porque son la misma superficie y el mismo gesto —colocar una zona— y porque
arreglar uno solo deja al usuario sin ver el anillo igual. Pero el invariante roto es distinto en
cada uno, y se arreglan en sitios distintos.

---

## Bug 1 · La cámara no tiene centro hasta que el mapa habla

`HomeScreen.kt:876` pinta el anillo de previsualización en `uiController.cameraLat/cameraLon`, y
esas dos propiedades **las escribe sólo `onCameraMoved`**, o sea el callback que el mapa dispara
cuando la cámara se MUEVE. Abres la modal con la cámara quieta → nadie ha llamado a `onCameraMoved`
→ `cameraLat` sigue a `null` → no hay anillo. En cuanto arrastras un milímetro aparece, que es
exactamente lo que cuenta la issue.

⛔ Y `cameraLat == null` **significa** eso y está bien que lo signifique: el propio
`HomeUiController` lo documenta en `pinToProtect` —*«nadie ha posado esta cámara»*— y
`ViewportRefetch` lo lee igual. El fallo no es el campo, es preguntarle a él.

**Diseño.** El centro de la cámara tal como lo conocemos tiene **dos** fuentes, y hay que preguntar
por las dos en orden: lo que el mapa REPORTÓ, y si aún no ha reportado, el sitio al que lo MANDAMOS
(`cameraTarget`). Vive en el controlador como propiedad derivada, no en el call site: la pregunta
«¿dónde está centrado el mapa?» es suya.

⛔ **Y NO se toca `pinToProtect` ni `ViewportRefetch`**: ahí el `null` no es una laguna que rellenar,
es la respuesta — una cámara sin posar no tiene pin que proteger ni ancla con la que entrar en
conflicto. Cambiarlo reabriría [HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001].

---

## Bug 2 · El mismo número es píxeles en un mapa y puntos en el otro

`Circle.lineWidth` de kmp-maps se entrega tal cual a dos APIs que **no miden en lo mismo**:

| | destino | unidad |
|---|---|---|
| Android | `CircleOptions.strokeWidth` (Google Maps) | **píxeles de pantalla** |
| iOS | `MKCircleRenderer.lineWidth` (MapKit) | **puntos** |

Leído en el fork (`kmp-maps/core/src/androidMain/.../Map.kt:353` y
`core/src/iosMain/.../MapDelegate.kt:78`): ninguno de los dos convierte nada.

Y Paparcar le pasa **píxeles**:

```kotlin
val zoneRingStrokePx = with(LocalDensity.current) { ZONE_CIRCLE_STROKE_DP.dp.toPx() }
```

En un iPhone a 3× eso son 2 dp → 6, entregados como 6 **puntos** = 18 píxeles físicos: **el anillo
sale al triple de grueso**. Con el relleno a `ZONE_CIRCLE_FILL_ALPHA = 0.07f` —un 7 %, una insinuación
de color—, un borde tres veces más gordo se lo come visualmente. De ahí *«en iOS sólo se pinta el
borde»*.

⚠️ **El relleno SÍ se pinta**: verificado en el fork, `MKCircleRenderer.fillColor` se asigna
(`MapDelegate.kt:79`) y `toAppleMapsColor()` conserva el canal alfa correctamente. Así que esto no es
una carencia del fork que haya que publicar: es una unidad mal elegida aquí.

**Diseño.** Un `expect`/`actual` que traduce un ancho de diseño a lo que espera el mapa de CADA
plataforma:

- `androidMain`: `dp * density` — **idéntico a lo de hoy**, Android no cambia un píxel.
- `iosMain`: el propio dp — un punto de iOS ES una unidad independiente de densidad.

Vive junto al mapa, en `ui/components/`, porque es una propiedad del transporte al mapa y no de la
pantalla.

## Criterio de éxito

- Al abrir la modal de zonas, con la cámara quieta, el anillo ya está ahí.
- En iPhone el anillo tiene el mismo grosor aparente que en Android, y el relleno al 7 % se ve.
- En Android no cambia nada: mismo número, misma conversión.

## Consumidores auditados

**Quién lee `cameraLat` / `cameraLon`:**

| Call site | Qué necesita | Veredicto |
|---|---|---|
| `HomeScreen:876` preview de zona | dónde está la cruz | **el bug** → pasa al centro derivado |
| `HomeScreen:1316`, `:1499` | un punto cualquiera | ya caen a `userGpsPoint`; sin cambios |
| `HomeScreen:422`, `:1118` → `laneAnsweringHere` | ¿el carril contesta a lo que miro? | exento — `null` = nadie ha posado la cámara |
| `ViewportRefetch:91` | ¿hay conflicto con el ancla? | exento — ídem, y lo dice su comentario |
| `HomeUiController.pinToProtect` | ¿hay pin que defender? | exento — [HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001] |

**Quién entrega anchos al mapa nativo** (no al Canvas de Compose, que sí quiere píxeles):

| Call site | Valor | Veredicto |
|---|---|---|
| `zoneRingStrokePx` (anillo de zona) | `2.dp.toPx()` | **el bug** → convertidor |
| `doubtRingStrokePx` (anillos de duda) | `2.5.dp.toPx()` | **mismo bug** → convertidor |
| `TRIP_TRAIL_WIDTH = 38f` (traza del viaje) | píxeles CRUDOS | ⏳ **misma confusión, decisión distinta** → ticket aparte |
| `PULSE_STROKE_DP`, `RING_STROKE_DP`, `CENTER_DOT_*` (2090-2155) | `.toPx()` | exentos — son `drawCircle` de Compose Canvas, que mide en píxeles |

⏳ **La traza del viaje no entra aquí a propósito.** Su constante no es un dp convertido: es un `38f`
de píxeles crudo, así que hoy YA es más fina en pantallas de menos densidad y arreglarla **cambia
Android**, que es una decisión visual que hay que ver en pantalla. Se abre
`MAP-A-TRIP-TRAIL-IS-38-RAW-PIXELS-001` en el backlog en vez de colarla en este ticket.
