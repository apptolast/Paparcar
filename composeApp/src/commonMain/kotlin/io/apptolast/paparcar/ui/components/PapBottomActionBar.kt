package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PaparcarSpacing

/**
 * The bottom action bar of a scaffolded screen — elevated surface + navigation-bars inset +
 * standard padding, with the CTA(s) as content (usually a [PapFooterButton], optionally a hint
 * line under it). Was cloned verbatim in BluetoothConfigScreen and VehicleRegistrationScreen,
 * shadow constant included. [SETTINGS-AUDIT-REMEDIATION-001]
 */
@Composable
fun PapBottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = SHADOW_ELEVATION,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = PaparcarSpacing.lg, vertical = PaparcarSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

private val SHADOW_ELEVATION = 8.dp
