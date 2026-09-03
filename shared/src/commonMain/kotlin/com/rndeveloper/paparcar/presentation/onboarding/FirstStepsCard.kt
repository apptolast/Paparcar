package com.rndeveloper.paparcar.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.FirstStepsProgress
import com.rndeveloper.paparcar.ui.components.PapButtonSize
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapPrimaryButton
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.first_steps_complete_sub
import paparcar.composeapp.generated.resources.first_steps_complete_title
import paparcar.composeapp.generated.resources.first_steps_done_cta
import paparcar.composeapp.generated.resources.first_steps_eyebrow
import paparcar.composeapp.generated.resources.first_steps_park_cta
import paparcar.composeapp.generated.resources.first_steps_park_sub
import paparcar.composeapp.generated.resources.first_steps_park_title
import paparcar.composeapp.generated.resources.first_steps_progress
import paparcar.composeapp.generated.resources.first_steps_skip
import paparcar.composeapp.generated.resources.first_steps_spot_cta
import paparcar.composeapp.generated.resources.first_steps_spot_sub
import paparcar.composeapp.generated.resources.first_steps_spot_title
import paparcar.composeapp.generated.resources.first_steps_watch_sub
import paparcar.composeapp.generated.resources.first_steps_watch_title

/**
 * The guided **first steps** checklist — the one surface that walks a new user from "I just
 * installed this" to "I know what this app does for me".
 * [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
 *
 * Renders the [FirstStepsProgress] resolved by `resolveFirstSteps`: exactly one step is EXPANDED
 * (its subtitle and its CTA), the others are one-line. It does not arbitrate — which step is
 * current, and whether the checklist shows at all, is decided by the pure projection.
 *
 * Brand green throughout: this row is the APP talking about itself, the same voice the set-up rows
 * of the detection surface use. It never wears a vehicle identity colour — no step is about one
 * particular car. [UI-COLOR-DOCTRINE-001]
 */
@Composable
fun FirstStepsCard(
    progress: FirstStepsProgress,
    /** Fires the REAL flow the step teaches — never a simulation of it. */
    onStartStep: (FirstStep) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PapOutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = CARD_V_PAD_DP.dp)) {
            Header(progress = progress, onDismiss = onDismiss)

            if (progress.isComplete) {
                // The closing card. The checklist does NOT vanish the instant the last step lands —
                // it would disappear under the finger that finished it, and the user would never
                // learn they had finished anything.
                CompleteRow(onDismiss = onDismiss)
            } else {
                FirstStep.entries.forEach { step ->
                    StepRow(
                        step = step,
                        isDone = step in progress.done,
                        isCurrent = step == progress.current,
                        onStart = { onStartStep(step) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(progress: FirstStepsProgress, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CARD_H_PAD_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HEADER_GAP_DP.dp),
    ) {
        Text(
            text = stringResource(Res.string.first_steps_eyebrow).uppercase(),
            style = PaparcarType.current.eyebrow,
            color = PapColor.actionText,
        )
        Text(
            // A count inside a line of text is LECTURA, not CIFRA — the voice changes only for a
            // number that is the subject of its own block. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
            text = stringResource(
                Res.string.first_steps_progress,
                progress.done.size,
                FirstStep.entries.size,
            ),
            style = PaparcarType.current.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        // "Skip" is a quiet way out, never a second CTA competing with the step's own action.
        TextButton(onClick = onDismiss) {
            Text(
                text = stringResource(Res.string.first_steps_skip),
                style = PaparcarType.current.cta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepRow(
    step: FirstStep,
    isDone: Boolean,
    isCurrent: Boolean,
    onStart: () -> Unit,
) {
    val copy = step.copy()
    PapListItem(
        title = stringResource(copy.title),
        // Only the CURRENT step spends the vertical room on an explanation — three subtitles at
        // once is a wall of text, and two of them would be describing things the user is not being
        // asked to do yet.
        subtitle = if (isCurrent) stringResource(copy.subtitle) else null,
        titleColor = if (isDone) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        leading = { StepMarker(step = step, isDone = isDone, isCurrent = isCurrent) },
        trailing = copy.cta?.takeIf { isCurrent }?.let { cta ->
            {
                PapPrimaryButton(
                    label = stringResource(cta),
                    onClick = onStart,
                    size = PapButtonSize.Compact,
                )
            }
        },
        contentPadding = PaddingValues(horizontal = CARD_H_PAD_DP.dp, vertical = ROW_V_PAD_DP.dp),
    )
}

/**
 * The step's status glyph. Three states, three shapes — the state is DRAWN, never spelled by
 * tinting the title: a done step keeps its words, it just stops being the one being asked for.
 */
@Composable
private fun StepMarker(step: FirstStep, isDone: Boolean, isCurrent: Boolean) {
    when {
        isDone -> Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = PapColor.actionText,
            modifier = Modifier.padding(MARKER_INSET_DP.dp),
        )
        // The current step gets the filled tile: it is the only row asking for something.
        isCurrent -> PapIconTile(
            icon = step.copy().icon,
            container = MaterialTheme.colorScheme.primary,
            tint = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
        )
        else -> Icon(
            imageVector = Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(MARKER_INSET_DP.dp),
        )
    }
}

@Composable
private fun CompleteRow(onDismiss: () -> Unit) {
    PapListItem(
        title = stringResource(Res.string.first_steps_complete_title),
        subtitle = stringResource(Res.string.first_steps_complete_sub),
        leading = {
            PapIconTile(
                icon = Icons.Rounded.CheckCircle,
                container = MaterialTheme.colorScheme.primary,
                tint = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            )
        },
        trailing = {
            PapPrimaryButton(
                label = stringResource(Res.string.first_steps_done_cta),
                onClick = onDismiss,
                size = PapButtonSize.Compact,
            )
        },
        contentPadding = PaddingValues(horizontal = CARD_H_PAD_DP.dp, vertical = ROW_V_PAD_DP.dp),
    )
}

/**
 * The words and the glyph of one step, resolved in ONE place. A `when` per property would let the
 * icon of a step drift away from its title the next time someone adds a fourth step.
 */
private data class FirstStepCopy(
    val title: StringResource,
    val subtitle: StringResource,
    /** Null = this step has nothing to press. See [FirstStep.UNDERSTAND_WATCH]. */
    val cta: StringResource?,
    val icon: ImageVector,
)

private fun FirstStep.copy(): FirstStepCopy = when (this) {
    // Same glyph as the cold-start detection row this step replaces while the checklist is up, so
    // the two surfaces read as one instruction rather than two. [UX-DETECTION-STORY-001]
    FirstStep.MARK_PARKING -> FirstStepCopy(
        title = Res.string.first_steps_park_title,
        subtitle = Res.string.first_steps_park_sub,
        cta = Res.string.first_steps_park_cta,
        icon = Icons.Rounded.AddLocationAlt,
    )
    // The eye is the app's established "we are watching this car" glyph (the Watching story row).
    //
    // NO CTA on purpose. There is nothing for the user to do here: either the car is being watched
    // — and the honest watch line right below in the detection surface says so, which is what
    // completes the step — or the watch is down, and that same surface already owns the
    // "Reactivar" / "Activar" button for it. A button here would be a second voice for one action,
    // the exact drift this ticket suppresses one row further down. [DET-WATCH-HONEST-001]
    FirstStep.UNDERSTAND_WATCH -> FirstStepCopy(
        title = Res.string.first_steps_watch_title,
        subtitle = Res.string.first_steps_watch_sub,
        cta = null,
        icon = Icons.Rounded.Visibility,
    )
    FirstStep.FIND_SPOT -> FirstStepCopy(
        title = Res.string.first_steps_spot_title,
        subtitle = Res.string.first_steps_spot_sub,
        cta = Res.string.first_steps_spot_cta,
        icon = Icons.Rounded.Explore,
    )
}

private const val CARD_H_PAD_DP = 16
private const val CARD_V_PAD_DP = 8
private const val ROW_V_PAD_DP = 8
private const val HEADER_GAP_DP = 8
/** Inset that lines the bare status glyphs up with the 40 dp tile of the current step. */
private const val MARKER_INSET_DP = 8
