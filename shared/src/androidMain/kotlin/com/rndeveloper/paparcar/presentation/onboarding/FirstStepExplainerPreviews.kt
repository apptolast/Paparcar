package com.rndeveloper.paparcar.presentation.onboarding

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme

/**
 * Previews for the first-step explainers, in PARITY with the Dev Catalog's
 * "Primeros pasos (explicadores)" group. [ONBOARDING-A-SPOT-IS-BORN-TWO-WAYS-001]
 *
 * They render the CONTENT, not the modal host: `ModalBottomSheet` is a window-level composable and
 * does not survive a preview.
 */
@Composable
private fun Preview(step: FirstStep, dark: Boolean) {
    PaparcarTheme(darkTheme = dark) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            FirstStepExplainerContent(step = step, onDismiss = {})
        }
    }
}

@Preview(name = "Explainer · Mark parking", showBackground = true)
@Composable
private fun ExplainerParkPreview() = Preview(FirstStep.MARK_PARKING, dark = false)

/** The one that carries BOTH mechanisms of a departure — automatic and "I'm leaving". */
@Preview(name = "Explainer · The two ways to free a spot", showBackground = true)
@Composable
private fun ExplainerWatchPreview() = Preview(FirstStep.UNDERSTAND_WATCH, dark = false)

/** The OTHER way a spot is born: one you saw, not one you left. */
@Preview(name = "Explainer · Report a spot you saw", showBackground = true)
@Composable
private fun ExplainerSpotPreview() = Preview(FirstStep.FIND_SPOT, dark = false)

@Preview(
    name = "Explainer · The two ways · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ExplainerWatchDarkPreview() = Preview(FirstStep.UNDERSTAND_WATCH, dark = true)
