package com.rndeveloper.paparcar.presentation.vehicles

import androidx.compose.runtime.Immutable

/**
 * Lo que una fila del historial tiene que añadir cuando la lista es de VARIOS conductores.
 * [VEH-SHARED-001]
 *
 * Dos hechos, y el segundo importa tanto como el primero: quién condujo, y si de ese viaje se puede
 * dibujar la ruta. Sin lo segundo, una fila sin línea se lee como un fallo de la app en vez de como
 * la decisión que es.
 */
@Immutable
data class SharedRowNote(
    val authorName: String?,
    val routeWithheld: Boolean,
)
