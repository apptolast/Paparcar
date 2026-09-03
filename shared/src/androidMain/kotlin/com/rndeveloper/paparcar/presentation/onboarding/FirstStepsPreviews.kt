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
import com.rndeveloper.paparcar.domain.onboarding.WatchReinforcement
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
private fun Preview(
    done: Set<FirstStep>,
    dark: Boolean,
    hasSpotsOnOffer: Boolean = false,
    isAutoDetectionStopped: Boolean = false,
    reinforcement: WatchReinforcement = WatchReinforcement.NONE,
    deferred: Set<FirstStep> = emptySet(),
) {
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
                    hasSpotsOnOffer = hasSpotsOnOffer,
                    isAutoDetectionStopped = isAutoDetectionStopped,
                    reinforcement = reinforcement,
                    deferred = deferred,
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

@Preview(name = "First steps · Step 2 · explains the release", showBackground = true)
@Composable
private fun FirstStepsStep2Preview() = Preview(done = setOf(FirstStep.MARK_PARKING), dark = false)

/** Step 2 with detection stopped: it asks for it instead of promising a watch that is not running.
 *  [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001] */
@Preview(name = "First steps · Step 2 · turn it on", showBackground = true)
@Composable
private fun FirstStepsStep2TurnOnPreview() = Preview(
    done = setOf(FirstStep.MARK_PARKING),
    dark = false,
    isAutoDetectionStopped = true,
)

/** The reinforcement step, Bluetooth face: the car can be linked to a device the phone already
 *  knows. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] */
@Preview(name = "First steps · Fortify · link Bluetooth", showBackground = true)
@Composable
private fun FirstStepsFortifyBtPreview() = Preview(
    done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH),
    dark = false,
    reinforcement = WatchReinforcement.BLUETOOTH,
)

/** The same step, battery face — and the "not yet" beside its CTA, which is what keeps a declined
 *  permission from costing the user the rest of the tutorial. */
@Preview(name = "First steps · Fortify · battery", showBackground = true)
@Composable
private fun FirstStepsFortifyBatteryPreview() = Preview(
    done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH),
    dark = false,
    reinforcement = WatchReinforcement.BATTERY,
)

/** Nothing left to ask, something left declined: the closing card that does not claim "all set". */
@Preview(name = "First steps · Closed with a deferral", showBackground = true)
@Composable
private fun FirstStepsDeferredClosePreview() = Preview(
    done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH, FirstStep.FIND_SPOT),
    dark = false,
    reinforcement = WatchReinforcement.BATTERY,
    deferred = setOf(FirstStep.FORTIFY_WATCH),
)

/** Step 3, day one: the community has nothing on offer, so the step asks the user to report one.
 *  [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001] */
@Preview(name = "First steps · Step 3 · report one", showBackground = true)
@Composable
private fun FirstStepsStep3ReportPreview() =
    Preview(done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH), dark = false)

/** Step 3 with spots actually nearby: the step shows them instead of asking for one. */
@Preview(name = "First steps · Step 3 · see spots", showBackground = true)
@Composable
private fun FirstStepsStep3SeePreview() = Preview(
    done = setOf(FirstStep.MARK_PARKING, FirstStep.UNDERSTAND_WATCH),
    dark = false,
    hasSpotsOnOffer = true,
)

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
