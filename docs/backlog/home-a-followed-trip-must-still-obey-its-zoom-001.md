# HOME-A-FOLLOWED-TRIP-MUST-STILL-OBEY-ITS-ZOOM-001 · Seguir al coche no es encuadrarlo

**Estado:** ✅ Done · sale con la rama `chore/PLAY-SHOTS-A-LISTING-IS-COMPOSED-NOT-CROPPED-001-feed`

## Problema

Abrir la app en mitad de un viaje deja la cámara en la **vista de mundo**, con el coche centrado y
siguiéndolo — pero a un zoom desde el que no se ve ni la ciudad. Es la issue **#66**, dada por
arreglada dos veces, y el user la volvió a ver: *«sólo pasa cuando estamos en driving»*.

Medido el 25-09 en el Pixel 8 (preset `driving`, mundo mock de Dublín): **30 segundos sin tocar la
pantalla y la cámara no se mueve del mundo entero**, con el puck vivo dibujado sobre Irlanda —
coche cenital rotado, traza azul y punto de salida, o sea, `drivingPuck != null` desde el primer
frame. Capturas a 6/12/20/30 s, idénticas.

## Por qué los dos arreglos anteriores no bastaron

Los dos vivían en el **host**, y el host ya hacía lo correcto:
`HomeUiController.followDriver` arma `firstFollowPending` al enganchar y en el PRIMER fix del viaje
pide `FOCUS_DRIVING_ZOOM` (16) con `ZoomIntent.AtLeast`
([HOME-A-LIVE-TRIP-DESERVES-THE-ZOOM-IT-NEEDS-001]). Ese destino llega al mapa y **el mapa lo tira
a la basura**:

- el tween programático se salta entero mientras se sigue (`if (following || userInteracting)
  return`) — correcto: un destino tirando de la posición pelearía con el enganche;
- y la rama de render del enganche devolvía `zoom = actualCamZoom`, el que tuviera la cámara.

O sea: **el zoom no lo mandaba el enganche, no lo mandaba el host, no lo mandaba nadie**. Sin fix
previo la cámara nace en `ZOOM_UNPLACED` (la semilla honesta de «no sé dónde estás»), y ahí se
quedaba todo el viaje. En campo casi nunca se ve porque el puck aparece SEGUNDOS después de abrir,
con la cámara ya posada por el primer fix; en el arranque en frío con viaje vivo, que es justo el
caso de la issue, no hay nada que la pose.

## Doctrina

*El enganche manda DÓNDE; el zoom sigue siendo del host.* Y sigue siendo un **suelo**: a quien ya
mira de cerca no se le aleja, y un viaje de media hora no puede deshacer el pellizco del usuario.

## Diseño

Un veredicto puro en `presentation/map/CameraFraming.kt`, al lado de `settleZoom`:

```kotlin
fun followLockZoom(current: Float, target: CameraTarget?, following: Boolean): Float?
```

`null` = no hay nada que pedir (no se sigue · el destino no trae zoom · el usuario ya está más
cerca). El mapa lo consulta en un efecto propio, clavado al **token del destino que trae zoom**
para que los ~1/s que llegan sin él no cancelen la animación a medias, y la rama de render del
enganche usa ese zoom mientras dura. Un dedo en el mapa lo apaga: el zoom vuelve a ser el real.

## Criterio de éxito

- 4 tests en `CameraFramingTest` (falsificados: con el cuerpo devolviendo `null` los cuatro caen).
- En device: preset `driving` abre **a nivel calle sobre el puck**. Verificado 25-09, Pixel 8.

## iOS

Sin hueco: `PaparcarMapView` y `CameraFraming` son `commonMain`, así que el arreglo viaja a iOS con
el mismo binario. El enganche al puck es el mismo código en las dos plataformas.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `HomeUiController.followDriver` (primer fix con zoom) | ✅ era correcto; ahora su petición llega |
| `resumeDriverFollow` (FAB de localización) | ✅ suelta el enganche y pasa por el tween normal |
| `goToPlace` (fila de la hoja, búsqueda) | ✅ suelta el enganche antes de escribir el destino |
| Fixes del puck (destino sin zoom, ~1/s) | ✅ `followLockZoom` devuelve `null`: no tocan la cámara |
| `ParkingHistoryDetailScreen` (otro host de `cameraTarget`) | ✅ no sigue a nadie: `following` es falso |
