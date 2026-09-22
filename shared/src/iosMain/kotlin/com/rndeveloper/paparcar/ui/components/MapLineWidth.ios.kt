package com.rndeveloper.paparcar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * MapKit mide sus trazos en **puntos**, que es la misma idea que un dp: una unidad independiente de
 * la densidad, que el sistema multiplica por la escala de la pantalla al rasterizar.
 * [MAP-A-RING-YOU-CANNOT-SEE-IS-TWO-BUGS-001]
 *
 * Así que aquí NO se convierte nada, y eso es el arreglo. Multiplicar por la densidad —lo que hacía
 * el `toPx()` compartido— pasaba la escala DOS veces: una nosotros y otra el sistema, o sea un
 * anillo al triple de grueso en cualquier iPhone moderno.
 */
@Composable
actual fun mapLineWidth(width: Dp): Float = width.value
