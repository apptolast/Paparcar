package com.rndeveloper.paparcar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * El grosor de una línea del mapa NATIVO, en las unidades que espera el mapa de esta plataforma.
 * [MAP-A-RING-YOU-CANNOT-SEE-IS-TWO-BUGS-001]
 *
 * ⛔ **`Circle.lineWidth` y `Polyline.width` de kmp-maps no miden lo mismo en las dos plataformas**,
 * y la librería no convierte nada — leído en el fork:
 *
 * | | acaba en | unidad |
 * |---|---|---|
 * | Android | `CircleOptions.strokeWidth` (Google Maps) | **píxeles de pantalla** |
 * | iOS | `MKCircleRenderer.lineWidth` (MapKit) | **puntos** |
 *
 * Paparcar le pasaba píxeles a los dos (`dp.toPx()`), así que en un iPhone a 3× el anillo de zona
 * salía **al triple de grueso** — y con el relleno al 7 %, un borde así se lo come: la issue #78 lo
 * describe como *«en iOS sólo se pinta el borde»*.
 *
 * ⛔ **Esto no es lo mismo que el Canvas de Compose.** Ahí `drawCircle` y compañía SÍ miden en
 * píxeles en las dos plataformas, y `.toPx()` es lo correcto. La diferencia es el destino, no la
 * unidad de origen: un dp es un dp, y quien tiene que saber traducirlo es quien conoce al mapa.
 */
@Composable
expect fun mapLineWidth(width: Dp): Float
