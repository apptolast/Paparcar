package com.rndeveloper.paparcar.presentation.util

/**
 * Dónde gestiona su suscripción el usuario de ESTA tienda. [MONEY-PLUS-CAN-SEE-ITS-OWN-BENEFITS-001]
 *
 * La suscripción la gestiona la TIENDA, no la app: cancelar, cambiar de plan o ver el próximo cargo
 * vive en Play (o App Store), nunca en una pantalla propia que intente replicarlo. Mismo patrón que
 * [storeRedeemCodeUrl] — una dirección, no una llamada de compra.
 *
 * `null` = esta plataforma no tiene puerta que abrir, y entonces la fila NO se enseña.
 */
expect fun storeManageSubscriptionUrl(): String?
