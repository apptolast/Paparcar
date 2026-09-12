# MAP-A-MARKER-BITMAP-IS-MEASURED-BEFORE-IT-EXISTS-001 · El rasterizador mide un `ComposeView` que todavía no se ha compuesto

**Estado:** ✅ Arreglado en la librería (kmp-maps) — implementado `SafeMarkerComposable` que captura la excepción y reintenta en el frame siguiente.

## Problema

```
java.lang.IllegalStateException: The ComposeView was measured to have a width or height of zero.
Make sure that the content has a non-zero size.
```

10-09-2026 18:47:53, Oppo CPH2371 / Android 13, **1.1.0 (12)**, en primer plano, 1 evento, 1 usuario.
Crashlytics `12bfd2cb4a3987b8f32502f06aab6477`, señal FRESH. `customKeys` del evento:
`det_action = ACTION_RESUME_SENTRY`, `det_job_active = false`, `det_has_movement = false`.

⚠️ **El stack llega ofuscado y no se puede desofuscar**: ver
`RELEASE-A-CRASH-WITHOUT-A-MAPPING-IS-UNREADABLE-001`. El diagnóstico de abajo NO se apoya en él.

## Qué se descartó, midiendo

`maps-compose 6.12.2`, `RememberComposeBitmapDescriptor.kt:34-56` — el sitio exacto que lanza:

```kotlin
val composeView = ComposeView(parent.context).apply { … setContent(content) }.also(parent::addView)
composeView.measure(measureSpec, measureSpec)          // UNSPECIFIED × UNSPECIFIED
if (composeView.measuredWidth == 0 || composeView.measuredHeight == 0) throw IllegalStateException(…)
```

Dos formas de medir cero, y **solo una es nuestra**:

1. *El contenido dibuja algo de tamaño cero.* ⛔ **Descartado por auditoría de los diez contenidos
   registrados** en `PaparcarMapView.customMarkerContent`, todos con raíz de tamaño fijo:

   | contenido | raíz |
   |---|---|
   | `VehicleBadgeMarker` | `Canvas`/`Box` en dp del tag |
   | `FreeSpotMarker` (× 18 variantes) | `Canvas(modifier.size(w, h))` |
   | `FreeSpotClusterMarker` | `Canvas(size(44.dp, 44·82/72))` |
   | `ZoneMarker` | `Row` con `padding` (no vacío ni sin hijos) |
   | `FuelStationMarker` | `Row` con `padding` + glifo `Canvas` |
   | `PublicParkingMarker` | `Row` con `padding` + `Text("P")` |
   | `DepartureDotMarker` | `Box(size(16.dp))` |
   | `LocationActiveMarker` (puck) | `Box(size(LOC_ACTIVE_DIAM))` |
   | `UserLocationDot` | `Box(size(USER_DOT_HALO_DP))` |
   | `DimWrapper` | envuelve a cualquiera de los anteriores |

   Ninguno tiene rama que renderice vacío, ninguno saca su tamaño de un dato variable, y **ninguno
   carga recursos** (`grep painterResource|Image(` en el fichero de marcadores: cero) — que es la vía
   típica de medir cero mientras el recurso llega.

   Un `contentId` sin handler tampoco lo explica: el fork cae a `Marker` nativo (`Map.kt:290-338`) o,
   en clúster, a `DefaultPin`, y ninguno pasa por este rasterizador.

2. *La composición todavía no ha corrido cuando se mide.* ✅ Es esta. `measure()` se llama **síncrono
   justo después de `setContent`**, sobre una composición hija enganchada al `CompositionContext` del
   padre. El stack del evento está **dentro de un frame del `Recomposer`**
   (`Choreographer.doFrame → recompose → …ComposableLambda… → el throw`): el padre está recomponiendo,
   así que la composición hija se encola en ese mismo `Recomposer` en vez de correr ya, `setContent`
   vuelve sin nodo de layout, y `measuredWidth` es 0.

Encaja con lo que se observa: **raro, no determinista y sin marcador culpable**. Salta cuando el
`contentId` de un marcador cambia dentro de un frame en el que el padre ya estaba recomponiendo —
el `contentId` es la clave de `rememberComposeBitmapDescriptor`, así que cambiarlo fuerza un
re-render en ese preciso momento.

## Doctrina violada

Ninguna nuestra. Lo que falla es que **el rasterizador no sabe representar «todavía no»**: trata
«esta composición aún no ha corrido» como «este contenido está vacío», y lo convierte en un crash de
la app entera. Un mapa no puede morirse porque un bitmap llegue un frame tarde.

## Diseño

⛔ **`kmp-maps` no es una dependencia de terceros: es librería NUESTRA**, con su repo en
`C:/Users/rndev/Documents/AndroidProjects/kmp-maps`, que publicamos nosotros (a `mavenLocal` para
iterar; las coordenadas `io.github.rndevelo.kmpmaps:core` son de nuestro namespace). O sea que el
defecto está **en código propio y editable**, no en un tercero al que haya que esperar. El coste no
es "convencer a nadie": es publicar una versión.

- **A · Arreglarlo en la librería (es donde vive).** Que rasterizar un marcador no pueda matar la
  app: capturar el `IllegalStateException` de `renderComposableToBitmapDescriptor` y dejar el
  marcador para el frame siguiente en vez de propagar. Cubre a los diez marcadores de una vez, y es
  testeable dentro de la propia librería.
- **B · Bajar la rotación de `contentId` desde la app.** Reduce la probabilidad, no la elimina, y
  reparte el invariante por diez sitios. No cierra nada.

⛔ **No** envolver cada handler en un `defaultMinSize(1.dp)`: quita el crash pintando un punto
invisible, o sea tapa el síntoma y deja el marcador sin dibujar sin que nadie se entere.

## Criterio de éxito

Un `contentId` que cambia durante una recomposición del padre no mata la app; el marcador aparece
en el frame siguiente. Con la librería arreglada, reproducible en test suyo.

## Por qué está aparcado

**Un solo evento, un solo usuario, sin repetirse.** Y desde
`RELEASE-A-CRASH-WITHOUT-A-MAPPING-IS-UNREADABLE-001` (en master, `c2b832d0`) la próxima release sí
sube su mapping, así que **si vuelve, llegará legible** — con clase y línea nuestras en vez de
`od2.m`. Esa es la señal que decide si toca pagar la publicación de la librería.
