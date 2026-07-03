package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotFit
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.computeSpotFit
import io.apptolast.paparcar.ui.components.label
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_peek_spot_fit_does_not_fit
import paparcar.composeapp.generated.resources.home_peek_spot_fit_fits
import paparcar.composeapp.generated.resources.home_peek_spot_fit_optimal
import paparcar.composeapp.generated.resources.home_peek_spot_left_by
import paparcar.composeapp.generated.resources.home_peek_spot_size_unknown

/**
 * Tri-state compatibility badge for the home peek sheet.
 *
 * Replaces the legacy boolean compat row with the [SpotFit] outcome from the
 * bidimensional (length × body) categorisation. When the freed spot exposes a
 * [Spot.carbodyType], a secondary "Left by …" line is appended underneath so
 * the user can see *what* freed the bay without committing to the comparison.
 */
@Composable
internal fun SpotFitRow(
    spot: Spot,
    vehicle: Vehicle?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val fit = computeSpotFit(spot, vehicle)

    Column(modifier = modifier.fillMaxWidth()) {
        FitPill(fit = fit, spot = spot, vehicle = vehicle)
        if (spot.carbodyType != null) {
            val leftByLabel = stringResource(Res.string.home_peek_spot_left_by, spot.carbodyType.label())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = leftByLabel,
                    style = PaparcarType.current.label,
                    color = cs.onSurface.copy(alpha = LEFT_BY_LABEL_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FitPill(
    fit: SpotFit,
    spot: Spot,
    vehicle: Vehicle?,
) {
    val cs = MaterialTheme.colorScheme

    // Resolves the icon, accent color and copy from the tri-state outcome. The
    // OPTIMAL branch uses the user's body label, FITS uses the spot's body
    // label, and DOES_NOT_FIT uses the user's body label so the message stays
    // first-person ("your X is too tight for this bay").
    val visual = when (fit) {
        SpotFit.OPTIMAL -> PillVisual(
            icon = Icons.Rounded.Star,
            tint = cs.primary,
            bg = cs.primary.copy(alpha = PILL_BG_ALPHA),
            label = stringResource(
                Res.string.home_peek_spot_fit_optimal,
                vehicle?.carbodyType?.label() ?: spot.carbodyType?.label() ?: "",
            ),
        )
        // Neutral (not blue): tertiary is reserved for Bluetooth/info, and FITS is a spatial verdict.
        // A muted check still reads positive without competing with OPTIMAL's green. [UI-REGRESSION]
        SpotFit.FITS -> PillVisual(
            icon = Icons.Rounded.CheckCircle,
            tint = cs.onSurfaceVariant,
            bg = cs.onSurface.copy(alpha = PILL_BG_ALPHA),
            label = stringResource(
                Res.string.home_peek_spot_fit_fits,
                spot.carbodyType?.label() ?: spot.sizeCategory?.label() ?: "",
            ),
        )
        SpotFit.DOES_NOT_FIT -> PillVisual(
            icon = Icons.Rounded.WarningAmber,
            tint = cs.error,
            bg = cs.error.copy(alpha = PILL_BG_ALPHA),
            label = stringResource(
                Res.string.home_peek_spot_fit_does_not_fit,
                vehicle?.carbodyType?.label() ?: vehicle?.sizeCategory?.label() ?: "",
            ),
        )
        SpotFit.UNKNOWN -> PillVisual(
            icon = Icons.Rounded.Info,
            tint = cs.onSurface.copy(alpha = UNKNOWN_FG_ALPHA),
            bg = cs.onSurface.copy(alpha = UNKNOWN_BG_ALPHA),
            label = stringResource(Res.string.home_peek_spot_size_unknown),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PILL_CORNER_DP.dp))
            .background(visual.bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = visual.tint,
            modifier = Modifier.size(PILL_ICON_DP.dp),
        )
        Text(
            text = visual.label,
            style = PaparcarType.current.caption,
            fontWeight = FontWeight.SemiBold,
            color = visual.tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class PillVisual(
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val label: String,
)

private const val PILL_CORNER_DP = 12
private const val PILL_ICON_DP = 18
private const val PILL_BG_ALPHA = 0.12f
private const val UNKNOWN_BG_ALPHA = 0.08f
private const val UNKNOWN_FG_ALPHA = 0.6f
private const val LEFT_BY_LABEL_ALPHA = 0.6f
