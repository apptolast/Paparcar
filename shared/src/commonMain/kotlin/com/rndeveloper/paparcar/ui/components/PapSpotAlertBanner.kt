package com.rndeveloper.paparcar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType

/**
 * «Ha salido una plaza en tu zona», contado DENTRO de la app.
 * [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
 *
 * ⛔ **Flota, no empuja.** El banner de conectividad es el primer hijo de la columna raíz y desplaza
 * la app hacia abajo, que es correcto para un estado que DURA. Éste dura unos segundos y habla de
 * algo que está justo debajo: empujar el mapa movería lo que el aviso quiere que mires.
 *
 * ⚠️ **No ofrece «Ver», y es deliberado.** Llevar la cámara desde la raíz exigiría un canal
 * raíz → Home para mover el mapa, y ése es exactamente el segundo dueño de la cámara que
 * [HOME-WHAT-IS-REMEMBERED-IS-THE-PLACE-YOU-CHOSE-001] acaba de quitar de en medio. El aviso te hace
 * MIRAR; el mapa ya la está enseñando, porque el pin aparece por la misma suscripción viva que
 * disparó el aviso.
 */
@Composable
fun PapSpotAlertBanner(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        shape = PapShapes.chip,
        shadowElevation = SPOT_ALERT_ELEVATION_DP.dp,
        modifier = modifier.clickable(onClick = onDismiss),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = text,
                style = PaparcarType.current.label,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SPOT_ALERT_CLOSE_DP.dp),
            )
        }
    }
}

/** La animación de entrada y salida del aviso, para que el sitio donde se usa no la invente. */
@Composable
fun PapSpotAlertBannerHost(
    visible: Boolean,
    text: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && text != null,
        enter = slideInVertically(PapMotion.medium()) { it } + fadeIn(PapMotion.medium()),
        exit = slideOutVertically(PapMotion.medium()) { it } + fadeOut(PapMotion.medium()),
        modifier = modifier,
    ) {
        PapSpotAlertBanner(text = text.orEmpty(), onDismiss = onDismiss)
    }
}

private const val SPOT_ALERT_ELEVATION_DP = 6
private const val SPOT_ALERT_CLOSE_DP = 16
