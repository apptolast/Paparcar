# MAP-A-TRIP-TRAIL-IS-38-RAW-PIXELS-001 · La traza del viaje mide 38 píxeles crudos, y eso no es un ancho

**Estado:** 🔵 Abierto · sin rama · nace de [MAP-A-RING-YOU-CANNOT-SEE-IS-TWO-BUGS-001]

## Problema

`PaparcarMapView.kt` pinta la traza del viaje con:

```kotlin
private const val TRIP_TRAIL_WIDTH = 38f
```

y se lo entrega tal cual a `Polyline.width` de kmp-maps, que —igual que `Circle.lineWidth`, y por lo
mismo— es **píxeles en Google Maps y puntos en MapKit**.

Dos consecuencias, y la segunda es la que impide arreglarlo de pasada:

1. **En iOS sale ~3× de gruesa.** 38 puntos en un iPhone a 3× son 114 píxeles físicos. Es el mismo
   fallo de unidades que [MAP-A-RING-YOU-CANNOT-SEE-IS-TWO-BUGS-001] arregló para los anillos, y la
   solución ya existe: `mapLineWidth(Dp)`.
2. **En Android ya es incorrecta, y de otra forma.** 38 es un número de PÍXELES fijo, no un dp
   convertido: la traza sale del mismo grosor aparente sólo en pantallas de la densidad para la que
   se eligió, y más gorda cuanto menor es la densidad. En un móvil a 2× se ve casi el doble de ancha
   que en uno a 3×.

## Por qué no entró en el ticket que lo encontró

Los anillos se arreglaron sin que Android cambiase un píxel: su constante ya era un dp y su `actual`
de Android hace exactamente la conversión de antes. Aquí no se puede: elegir el dp equivalente
(≈12,7 dp a 3×, ≈19 dp a 2×) **cambia lo que se ve en Android**, y cuál es el grosor bueno es una
decisión visual que hay que tomar mirando la traza en pantalla, no despejando una ecuación.

Meterlo en aquel ticket habría colado un cambio visual no verificado de Android dentro de un arreglo
de iOS. [feedback: sistemas, no parches — pero tampoco parches disfrazados de sistema]

## Diseño (a decidir al implementar)

1. Elegir el grosor en **dp**, mirándolo en device con un viaje real. Punto de partida: ~13 dp, que
   es lo que hoy se ve en un móvil a 3×, que es donde se ajustó.
2. `TRIP_TRAIL_WIDTH` pasa a `TRIP_TRAIL_WIDTH_DP` y entra por `mapLineWidth(...)`.
3. Barrer el resto de `Polyline` (`width = …`) del fichero: la ruta OSM y la traza cruda comparten
   la constante, pero conviene confirmarlo al tocarla.

## Criterio de éxito

- La traza se ve del mismo grosor en móviles de distinta densidad.
- En iPhone se ve como en Android, no al triple.
- Captura antes/después en Android, porque el valor cambia allí [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001].
