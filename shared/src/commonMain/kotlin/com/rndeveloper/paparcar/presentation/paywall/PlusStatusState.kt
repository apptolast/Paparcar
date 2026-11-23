package com.rndeveloper.paparcar.presentation.paywall

import androidx.compose.runtime.Immutable
import com.rndeveloper.paparcar.domain.model.PlusOrigin

/**
 * El recap de solo lectura para quien YA es Plus. [MONEY-PLUS-CAN-SEE-ITS-OWN-BENEFITS-001]
 *
 * Hermano del [PaywallState], no un paywall con el botón escondido: aquí no hay ofertas, ni plan
 * seleccionado, ni compra en curso — nada que vender. Lo único que trae de fuera es [origin] y
 * [expiresAtMillis], leídos sin filtrar de [com.rndeveloper.paparcar.domain.usecase.entitlement.ObservePlusGrantUseCase]
 * (el paywall sólo necesita saber si ese Plus SE ACABA; esta pantalla necesita saber CUÁNDO, tanto
 * si se acaba como si se renueva).
 */
@Immutable
data class PlusStatusState(
    /** `null` mientras se resuelve el primer valor del stream (arranque en frío de la pantalla). */
    val origin: PlusOrigin? = null,
    /** Cuándo termina el acceso actual — fin de periodo si [origin] es [PlusOrigin.PURCHASED]
     *  (normalmente se renueva solo), o fin del regalo si es [PlusOrigin.PROMOTIONAL]. `null` =
     *  sin fecha conocida (un regalo de por vida). */
    val expiresAtMillis: Long? = null,
    /** La tienda no tiene puerta de gestión en esta plataforma (iOS, hoy) → la fila no se enseña. */
    val canManageSubscription: Boolean = false,
)
