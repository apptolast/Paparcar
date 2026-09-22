package com.rndeveloper.paparcar.presentation.permissions

import org.jetbrains.compose.resources.StringResource

/**
 * Las tres frases de la pantalla de permisos que **no pueden ser la misma en las dos plataformas**,
 * porque describen un camino que el usuario recorre con el dedo y ese camino es distinto.
 *
 * No es localización ni es preferencia: es que en Android la ubicación en segundo plano se concede
 * en una pantalla de Ajustes con la opción «Permitir todo el tiempo», y en iOS se concede en un
 * diálogo que el sistema presenta UNA vez y, si ya se gastó, solo desde Ajustes → Paparcar →
 * Ubicación → Siempre. Narrar la de Android en iOS manda al usuario a una pantalla que no existe.
 *
 * Vive como `expect val` y no como un `if (isIos)` en la UI para que añadir la tercera plataforma
 * sea añadir un `actual`, no encontrar los condicionales repartidos.
 * [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
 */
data class PlatformPermissionCopy(
    /** Cuerpo de la guía que se enseña ANTES de pedir la ubicación en segundo plano. */
    val backgroundGuideBody: StringResource,
    /** Razón de la fila «Servicios de ubicación»: dónde está de verdad ese interruptor. */
    val locationServicesReason: StringResource,
    /** Remedio de la fila de precisión: dónde se enciende la ubicación precisa aquí. */
    val preciseLocationReason: StringResource,
)

expect val platformPermissionCopy: PlatformPermissionCopy
