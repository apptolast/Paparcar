package com.rndeveloper.paparcar.presentation.onboarding

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.resolveFirstSteps
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme

/**
 * Previews for the guided checklist, in PARITY with the Dev Catalog's
 * "Primeros pasos (checklist)" group. [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
 *
 * Every variant goes through the real [resolveFirstSteps] rather than a hand-built progress object,
 * so a preview can never show a position the product cannot reach.
 */
@Composable
private fun Preview(done: Set<FirstStep>, dark: Boolean) {
    PaparcarTheme(darkTheme = dark) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
        ) {
            FirstStepsCard(
                progress = resolveFirstSteps(
                    done = done,
                    dismissed = false,
                    hasActiveSession = false,
                    isWatching = false,
                    hasTouchedSpots = false,
                ),
                onStartStep = {},
                onDismiss = {},
            )
        }
    }
}

@Preview(name = "First steps · Step 1", showBackground = true)
@Composable
private fun FirstStepsStep1Preview() = Preview(done = emptySet(), dark = false)

@Preview(name = "First steps · Step 2 (no CTA)", showBackground = true)
@Composable
private fun FirstStepsStep2Preview() = Preview(done = setOf(FirstStep.MARK_PARKING), dark = false)

@Preview(name = "First steps · Step 3", showBackground = true)
@Composable
private fun FirstStepsStep3Preview() =
    Preview(done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH), dark = false)

/** Reported a spot before ever parking: step 3 banked, step 1 still the ask. */
@Preview(name = "First steps · Out of order", showBackground = true)
@Composable
private fun FirstStepsOutOfOrderPreview() = Preview(done = setOf(FirstStep.FIND_SPOT), dark = false)

@Preview(name = "First steps · Complete", showBackground = true)
@Composable
private fun FirstStepsCompletePreview() = Preview(done = FirstStep.entries.toSet(), dark = false)

@Preview(
    name = "First steps · Step 1 · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FirstStepsStep1DarkPreview() = Preview(done = emptySet(), dark = true)

@Preview(
    name = "First steps · Complete · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FirstStepsCompleteDarkPreview() = Preview(done = FirstStep.entries.toSet(), dark = true)
