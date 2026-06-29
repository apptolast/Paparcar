package io.apptolast.paparcar.presentation.vehicles.components

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import io.apptolast.paparcar.ui.components.PapSectionHeaderRow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.empty_records
import paparcar.composeapp.generated.resources.empty_records_dark
import paparcar.composeapp.generated.resources.history_empty_subtitle
import paparcar.composeapp.generated.resources.history_empty_title

/**
 * Active section header — primary-tinted variant of [PapSectionHeaderRow]
 * with a leading pulsing dot. Padding-top 12dp for clearer separation from
 * the timeline.
 */
@Composable
internal fun ActiveSectionHeader(text: String) {
    PapSectionHeaderRow(
        title = text,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        leading = {
            PulsingDot(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(8.dp),
            )
        },
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
 * clara con tema forzado), espejando [io.apptolast.paparcar.ui.illustrations.OnboardingHero].
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TITLE_SUBTITLE_GAP_DP.dp))
        Text(
            stringResource(Res.string.history_empty_subtitle),
            style = MaterialTheme.typography.bodyLarge,
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
private const val SUBTITLE_ALPHA = 0.55f
