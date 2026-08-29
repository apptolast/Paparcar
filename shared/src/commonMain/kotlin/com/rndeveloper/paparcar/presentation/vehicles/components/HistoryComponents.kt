package com.rndeveloper.paparcar.presentation.vehicles.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import com.rndeveloper.paparcar.ui.components.PapSectionHeaderRow
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.empty_records
import paparcar.composeapp.generated.resources.empty_records_dark
import paparcar.composeapp.generated.resources.history_empty_subtitle
import paparcar.composeapp.generated.resources.history_empty_title
import com.rndeveloper.paparcar.ui.theme.PapAlpha

/**
 * Active section header — a NEUTRAL [PapSectionHeaderRow] with a leading pulsing dot. Padding-top
 * 12dp for clearer separation from the timeline.
 *
 * "APARCADO ACTUALMENTE" is a STATE, and a state is written in neutral text and told by ANIMATION,
 * never by a hue — so the label sits with every other section header while the dot pulses. Only the
 * dot carries colour, and it carries the VEHICLE's ([accent]), matching the rail dot right below it.
 * [UI-COLOR-DOCTRINE-001 §3.1][UI-HISTORY-IDENTITY-AND-SOURCE-001]
 *
 * @param accent the identity colour of the vehicle this timeline belongs to (blue = Bluetooth,
 *   green = assisted, grey = unwatched), resolved by the caller through `vehicleIdentityColor`.
 */
@Composable
internal fun ActiveSectionHeader(text: String, accent: Color) {
    PapSectionHeaderRow(
        title = text,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        leading = { PulsingDot(color = accent, modifier = Modifier.size(8.dp)) },
    )
}

/**
 * Empty history state (Nivel 3) — ilustración de marca `empty-records` (claro+oscuro), luego bold
 * title + muted body. El bloque se centra vertical/horizontalmente en el espacio que le da el caller
 * (en historial, [HistoryContent] le pasa `fillParentMaxSize` → queda encuadrado en el hueco entre la
 * hero card y la bottom nav). La ilustración va pegada a su texto (gap mínimo) porque el propio
 * drawable ya reserva aire inferior con la sombra de suelo.
 *
 * La ilustración trae su propio color (multicolor) → se pinta con [Image] SIN tint/colorFilter.
 * Theme-aware por luminancia de `surface` (no `isSystemInDarkTheme()`, que devuelve la variante
 * clara con tema forzado), espejando [com.rndeveloper.paparcar.ui.illustrations.OnboardingHero].
 */
@Composable
internal fun EmptyHistoryState(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < ILLUSTRATION_DARK_LUMINANCE
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(
                if (dark) Res.drawable.empty_records_dark else Res.drawable.empty_records,
            ),
            contentDescription = null,
            modifier = Modifier
                .width(ILLUSTRATION_WIDTH_DP.dp)
                .height(ILLUSTRATION_HEIGHT_DP.dp),
        )
        Spacer(Modifier.height(ILLUSTRATION_TEXT_GAP_DP.dp))
        Text(
            stringResource(Res.string.history_empty_title),
            style = PaparcarType.current.cardTitle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TITLE_SUBTITLE_GAP_DP.dp))
        Text(
            stringResource(Res.string.history_empty_subtitle),
            style = PaparcarType.current.subtitle,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA),
            textAlign = TextAlign.Center,
        )
    }
}

private const val ILLUSTRATION_WIDTH_DP = 180
private const val ILLUSTRATION_HEIGHT_DP = 154
private const val ILLUSTRATION_TEXT_GAP_DP = 2
private const val TITLE_SUBTITLE_GAP_DP = 4
private const val ILLUSTRATION_DARK_LUMINANCE = 0.5f
private val SUBTITLE_ALPHA = PapAlpha.subtitle
