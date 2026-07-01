package io.apptolast.paparcar.presentation.vehicleregistration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.ui.components.PapPrimaryButton
import io.apptolast.paparcar.ui.components.VehicleIcon
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.vehicle_size_explainer_card1_desc
import paparcar.composeapp.generated.resources.vehicle_size_explainer_card1_title
import paparcar.composeapp.generated.resources.vehicle_size_explainer_card2_desc
import paparcar.composeapp.generated.resources.vehicle_size_explainer_card2_title
import paparcar.composeapp.generated.resources.vehicle_size_explainer_cta
import paparcar.composeapp.generated.resources.vehicle_size_explainer_subtitle
import paparcar.composeapp.generated.resources.vehicle_size_explainer_title

private val TOP_CONTENT_PADDING     = 56.dp
private val BOTTOM_CONTENT_PADDING  = 140.dp
private val TITLE_ICON_SIZE         = 84.dp
private val NODE_SIZE               = 32.dp
private val NODE_ICON_SIZE          = 18.dp
private val CONNECTOR_WIDTH         = 2.dp

@Composable
fun VehicleSizeExplainerScreen(
    onContinue: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = PaparcarSpacing.xxxl)
                .padding(top = TOP_CONTENT_PADDING, bottom = BOTTOM_CONTENT_PADDING)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // New isometric vehicle pictogram (identity brand-green palette, no tint) — mirrors the
            // onboarding heroes as the screen's visual anchor.
            VehicleIcon(
                carbody = null,
                size = VehicleSize.MEDIUM_SUV,
                modifier = Modifier.size(TITLE_ICON_SIZE),
            )
            Spacer(Modifier.height(PaparcarSpacing.sm))
            Text(
                text = stringResource(Res.string.vehicle_size_explainer_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PaparcarSpacing.lg))
            Text(
                text = stringResource(Res.string.vehicle_size_explainer_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PaparcarSpacing.xxl + PaparcarSpacing.md))

            ExplainerTimelineStep(
                icon = Icons.Rounded.Straighten,
                title = stringResource(Res.string.vehicle_size_explainer_card1_title),
                description = stringResource(Res.string.vehicle_size_explainer_card1_desc),
                isLast = false,
            )
            ExplainerTimelineStep(
                icon = Icons.Rounded.Lock,
                title = stringResource(Res.string.vehicle_size_explainer_card2_title),
                description = stringResource(Res.string.vehicle_size_explainer_card2_desc),
                isLast = true,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = PaparcarSpacing.xxl)
                .navigationBarsPadding()
                .padding(bottom = PaparcarSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PapPrimaryButton(
                label = stringResource(Res.string.vehicle_size_explainer_cta),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Timeline step mirroring the permissions screen spine ([PermissionTier]): a circular node holding
 * the step's Material Rounded icon in the left column, a continuous vertical connector down to the
 * next node, and the title + description to the right. Keeps normal-case title (unlike the
 * permissions eyebrow) since these are full sentences.
 */
@Composable
private fun ExplainerTimelineStep(
    icon: ImageVector,
    title: String,
    description: String,
    isLast: Boolean,
) {
    val connectorColor = MaterialTheme.colorScheme.outlineVariant

    // IntrinsicSize.Min → the content column fixes the row height; the connector (weight 1f) fills the
    // remaining space under the node so it links continuously to the next step. [ONB-SCAFFOLD-001]
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().width(NODE_SIZE),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(NODE_SIZE),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(NODE_ICON_SIZE),
                    )
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(CONNECTOR_WIDTH)
                        .weight(1f)
                        .background(connectorColor),
                )
            }
        }

        Spacer(Modifier.width(PaparcarSpacing.lg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(PaparcarSpacing.xs))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isLast) Spacer(Modifier.height(PaparcarSpacing.xl))
        }
    }
}
