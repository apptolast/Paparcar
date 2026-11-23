package com.rndeveloper.paparcar.presentation.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rndeveloper.paparcar.domain.model.PlusOrigin
import com.rndeveloper.paparcar.presentation.util.shortDateText
import com.rndeveloper.paparcar.ui.components.PAPARCAR_LOGO_BESIDE_NAME_SIZE
import com.rndeveloper.paparcar.ui.components.PapPremiumCrest
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.theme.PapPageRhythm
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.paywall_close_cd
import paparcar.composeapp.generated.resources.paywall_title
import paparcar.composeapp.generated.resources.plus_status_active
import paparcar.composeapp.generated.resources.plus_status_benefits_title
import paparcar.composeapp.generated.resources.plus_status_gift_ends
import paparcar.composeapp.generated.resources.plus_status_gift_lifetime
import paparcar.composeapp.generated.resources.plus_status_manage_subscription
import paparcar.composeapp.generated.resources.plus_status_renews

/**
 * El recap de solo lectura de Paparcar Plus — para quien ya lo tiene.
 * [MONEY-PLUS-CAN-SEE-ITS-OWN-BENEFITS-001]
 *
 * Hermano de [PaywallScreen], no su sustituto: comparte el crest y la lista de beneficios
 * ([PlusBenefits]) porque es la MISMA promesa, pero no hay ofertas, ni plan, ni botón de compra —
 * aquí no hay nada que vender. Único sitio de la app donde un Plus ya pagado puede repasar qué
 * incluye su suscripción (antes de esto, no existía ninguno).
 */
@Composable
fun PlusStatusScreen(onNavigateBack: () -> Unit) {
    val viewModel: PlusStatusViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PlusStatusEffect.OpenUrl -> uriHandler.openUri(effect.url)
            }
        }
    }

    PlusStatusContent(state = state, onIntent = viewModel::handleIntent, onClose = onNavigateBack)
}

@Composable
fun PlusStatusContent(
    state: PlusStatusState,
    onIntent: (PlusStatusIntent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PapPageRhythm.sideInset),
            ) {
                Spacer(Modifier.height(CLOSE_ROW_HEIGHT_DP.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CREST_TEXT_GAP_DP.dp),
                ) {
                    PapPremiumCrest(size = PAPARCAR_LOGO_BESIDE_NAME_SIZE)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.paywall_title),
                            style = PaparcarType.current.screenTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        PlusStatusLine(state)
                    }
                }
                // Cabecera + el crest de arriba: el hueco antes de una sección.
                Spacer(Modifier.height(PapPageRhythm.beforeSection))
                PapSectionHeader(stringResource(Res.string.plus_status_benefits_title))
                // La cabecera de sección y su primer bloque (la lista de ventajas).
                Spacer(Modifier.height(PapPageRhythm.afterSection))
                PlusBenefits()
                if (state.canManageSubscription) {
                    // Dos bloques hermanos de la página: la lista y el botón de gestión.
                    Spacer(Modifier.height(PapPageRhythm.block))
                    TextButton(onClick = { onIntent(PlusStatusIntent.ManageSubscription) }) {
                        Text(
                            text = stringResource(Res.string.plus_status_manage_subscription),
                            style = PaparcarType.current.cta,
                        )
                    }
                }
                Spacer(Modifier.height(PapPageRhythm.edgeInset))
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(CLOSE_PADDING_DP.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.paywall_close_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * La única línea que cambia por cuenta: cuándo se renueva (comprado), cuándo termina el regalo (el
 * caso defensivo — en la práctica un regalo con fecha de fin abre el paywall de decisión, no esta
 * pantalla), o que es un regalo sin fecha de fin. Nada mientras el stream no ha emitido todavía.
 */
@Composable
private fun PlusStatusLine(state: PlusStatusState) {
    if (state.origin == null) return
    val text = when {
        state.expiresAtMillis != null && state.origin == PlusOrigin.PROMOTIONAL ->
            stringResource(Res.string.plus_status_gift_ends, shortDateText(state.expiresAtMillis))
        state.expiresAtMillis != null ->
            stringResource(Res.string.plus_status_renews, shortDateText(state.expiresAtMillis))
        state.origin == PlusOrigin.PROMOTIONAL -> stringResource(Res.string.plus_status_gift_lifetime)
        else -> stringResource(Res.string.plus_status_active)
    }
    Text(
        text = text,
        style = PaparcarType.current.body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}

private const val CLOSE_ROW_HEIGHT_DP = 48
private const val CLOSE_PADDING_DP = 4
private const val CREST_TEXT_GAP_DP = 16
