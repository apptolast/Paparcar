package com.rndeveloper.paparcar.presentation.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.onboarding.FindSpotAsk
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.FirstStepsProgress
import com.rndeveloper.paparcar.domain.onboarding.WatchAsk
import com.rndeveloper.paparcar.domain.onboarding.WatchReinforcement
import com.rndeveloper.paparcar.ui.components.PapButtonSize
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapIconTileSize
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapListItemGap
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapPrimaryButton
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.first_steps_complete_sub
import paparcar.composeapp.generated.resources.first_steps_complete_title
import paparcar.composeapp.generated.resources.first_steps_deferred_sub
import paparcar.composeapp.generated.resources.first_steps_deferred_title
import paparcar.composeapp.generated.resources.first_steps_done_cta
import paparcar.composeapp.generated.resources.first_steps_eyebrow
import paparcar.composeapp.generated.resources.first_steps_fortify_battery_sub
import paparcar.composeapp.generated.resources.first_steps_fortify_battery_title
import paparcar.composeapp.generated.resources.first_steps_fortify_bt_sub
import paparcar.composeapp.generated.resources.first_steps_fortify_bt_title
import paparcar.composeapp.generated.resources.first_steps_fortify_done_sub
import paparcar.composeapp.generated.resources.first_steps_fortify_done_title
import paparcar.composeapp.generated.resources.first_steps_not_yet
import paparcar.composeapp.generated.resources.first_steps_park_cta
import paparcar.composeapp.generated.resources.first_steps_park_sub
import paparcar.composeapp.generated.resources.first_steps_park_title
import paparcar.composeapp.generated.resources.first_steps_progress
import paparcar.composeapp.generated.resources.first_steps_skip
import paparcar.composeapp.generated.resources.first_steps_spot_cta
import paparcar.composeapp.generated.resources.first_steps_spot_report_cta
import paparcar.composeapp.generated.resources.first_steps_spot_report_sub
import paparcar.composeapp.generated.resources.first_steps_spot_report_title
import paparcar.composeapp.generated.resources.first_steps_spot_sub
import paparcar.composeapp.generated.resources.first_steps_spot_title
import paparcar.composeapp.generated.resources.first_steps_watch_on_sub
import paparcar.composeapp.generated.resources.first_steps_watch_on_title
import paparcar.composeapp.generated.resources.first_steps_watch_sub
import paparcar.composeapp.generated.resources.first_steps_watch_title
import paparcar.composeapp.generated.resources.home_det_producer_cta
import paparcar.composeapp.generated.resources.home_det_watching_fortify_cta
import paparcar.composeapp.generated.resources.vehicle_registration_bt_cta

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
    /**
     * Open the step's explainer. [ONBOARDING-A-SPOT-IS-BORN-TWO-WAYS-001]
     *
     * EVERY row is tappable, including the done ones — that is what makes replaying the checklist
     * from Settings worth anything once the live state already satisfies all three steps. Without
     * it, a replay would just show three ticks.
     */
    onOpenStep: (FirstStep) -> Unit = {},
    /**
     * "Not yet" on a step that asks for a permission.
     * [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
     *
     * Every asking step carries this second way out, and it is the whole point: a permission the
     * user declines must cost them the permission, not the rest of the tutorial.
     */
    onDeferStep: (FirstStep) -> Unit = {},
    /** Tap on a postponed step: it becomes the current ask again. */
    onResumeStep: (FirstStep) -> Unit = {},
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
                // The steps THIS user has — a checklist with nothing to reinforce never shows the
                // fortify step at all. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
                progress.applicable.forEach { step ->
                    StepRow(
                        step = step,
                        isDone = step in progress.done,
                        isCurrent = step == progress.current,
                        // Answered "not yet" and still pending: it keeps its words and its way back
                        // in. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
                        isDeferred = step in progress.deferred && step !in progress.done,
                        // Which face a step wears is decided by the projection, never by this
                        // composable asking "are there spots?" or "is the watch fragile?".
                        // [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
                        findSpotAsk = progress.findSpotAsk,
                        watchAsk = progress.watchAsk,
                        reinforcement = progress.reinforcement,
                        onStart = { onStartStep(step) },
                        onDefer = { onDeferStep(step) },
                        onResume = { onResumeStep(step) },
                        onOpen = { onOpenStep(step) },
                    )
                }
                // Nothing left to ASK, but something was declined: the checklist has finished its
                // job without the user having finished theirs, and it says exactly that instead of
                // hanging there with no current step. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
                if (progress.current == null && progress.hasDeferrals) {
                    DeferredCloseRow(onDismiss = onDismiss)
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
                progress.doneCount,
                // The steps this user HAS, not the size of the enum: someone with nothing to
                // reinforce is on "1 of 3", not on "1 of 4" with a step they will never see.
                // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
                progress.total,
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
    isDeferred: Boolean,
    findSpotAsk: FindSpotAsk,
    watchAsk: WatchAsk,
    reinforcement: WatchReinforcement,
    onStart: () -> Unit,
    onDefer: () -> Unit,
    onResume: () -> Unit,
    onOpen: () -> Unit,
) {
    val copy = step.copy(findSpotAsk, watchAsk, reinforcement)
    // Interaction lives on the container, layout in the row — the component's own contract. The
    // whole block (row + its CTA) opens the explainer; the button consumes its own taps.
    // [UI-LIST-ITEM-001] [ONBOARDING-A-SPOT-IS-BORN-TWO-WAYS-001]
    // Tapping a row opens its explainer — EXCEPT a deferred one, where the tap is the user coming
    // back to the thing they postponed. Explaining it again is not what they came for.
    // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
    Column(modifier = Modifier.clickable(onClick = if (isDeferred) onResume else onOpen)) {
        PapListItem(
            title = stringResource(copy.title),
            // Only the CURRENT step spends the vertical room on an explanation — three subtitles at
            // once is a wall of text, and two of them would be describing things the user is not
            // being asked to do yet.
            // A deferred step CLOSES like every other one — one line, no buttons. Keeping its
            // subtitle and its CTA open left two steps offering an action at the same time, and the
            // user could not tell which one the app was actually asking for. Tapping the line brings
            // it back. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
            subtitle = if (isCurrent) stringResource(copy.subtitle) else null,
            titleColor = if (isDone) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            // The glyph comes from the copy already resolved above — the step's words and its icon
            // are one decision, and resolving it twice is how the two drift apart.
            leading = { StepMarker(icon = copy.icon, isDone = isDone, isCurrent = isCurrent) },
            contentPadding = PaddingValues(
                horizontal = CARD_H_PAD_DP.dp,
                vertical = ROW_V_PAD_DP.dp,
            ),
        )
        // The CTA gets its OWN line, never the row's trailing slot: this step's label is a two-to-
        // three word phrase in all nine locales ("Marcar aparcamiento", "Marquer le stationnement"),
        // and a trailing child measures BEFORE the weighted text column — it served itself ~190 dp
        // of a 263 dp row and left the title wrapping one letter per line.
        // [ONBOARDING-FIRST-STEPS-MUST-BE-READABLE-AND-FOUND-001]
        copy.cta?.takeIf { isCurrent }?.let { cta ->
            Row(
                modifier = Modifier.padding(start = ctaIndent, bottom = ROW_V_PAD_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CTA_GAP_DP.dp),
            ) {
                PapPrimaryButton(
                    label = stringResource(cta),
                    onClick = onStart,
                    size = PapButtonSize.Compact,
                )
                // The way out, next to the way in. Only steps that ASK FOR A PERMISSION carry it:
                // marking your parking or meeting the community are things the app can keep asking
                // for, but a permission the user declined must not cost them the rest of the
                // tutorial. Quiet on purpose — an invitation with an exit, not two competing CTAs.
                // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
                if (copy.asksForPermission) {
                    TextButton(onClick = onDefer) {
                        Text(
                            text = stringResource(Res.string.first_steps_not_yet),
                            style = PaparcarType.current.cta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The step's status glyph. Three states, three shapes — the state is DRAWN, never spelled by
 * tinting the title: a done step keeps its words, it just stops being the one being asked for.
 */
@Composable
private fun StepMarker(icon: ImageVector, isDone: Boolean, isCurrent: Boolean) {
    when {
        isDone -> Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = PapColor.actionText,
            modifier = Modifier.padding(MARKER_INSET_DP.dp),
        )
        // The current step gets the filled tile: it is the only row asking for something.
        isCurrent -> PapIconTile(
            icon = icon,
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

/** The closing row keeps its button in `trailing`: "Done" is ONE word in all nine locales
 *  (`Hecho`, `Fertig`, `Gata`…), which is what the slot is for. */
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
 * The closing row for a checklist that finished asking while something was left declined.
 * [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
 *
 * It cannot be [CompleteRow]: "You're all set" over a watch the user chose not to reinforce is the
 * app congratulating itself for something that did not happen. It says what is true — you know how
 * this works, and what you left is still there — and points at where it lives, because the detection
 * surface goes back to asking for it the moment this card is gone.
 */
@Composable
private fun DeferredCloseRow(onDismiss: () -> Unit) {
    PapListItem(
        title = stringResource(Res.string.first_steps_deferred_title),
        subtitle = stringResource(Res.string.first_steps_deferred_sub),
        leading = {
            PapIconTile(
                icon = Icons.Rounded.Verified,
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
    /**
     * This face is asking the user to GRANT something. Those get a "not yet" beside their CTA; the
     * ones that ask the user to DO something in the app do not — the app can keep asking for a
     * parking, but it cannot keep hostage a tutorial over a permission.
     * [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
     */
    val asksForPermission: Boolean = false,
)

private fun FirstStep.copy(
    findSpotAsk: FindSpotAsk,
    watchAsk: WatchAsk,
    reinforcement: WatchReinforcement,
): FirstStepCopy = when (this) {
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
    // [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001] Two faces, because the step's own
    // sentence is only true while detection is running.
    //
    // EXPLAIN_RELEASE keeps NO CTA, for the reason it never had one: there is nothing to do — the
    // car is being watched, the honest watch line right below says so, and that is what completes
    // the step.
    //
    // TURN_IT_ON does get one, and it is not a second voice: with detection stopped the checklist
    // TAKES OVER the surface's own "turn on detection" row (`FirstStepsOwnership.DETECTION_OFF`
    // hides it), fires that row's intent, and borrows that row's label — one action, one button on
    // screen. Without the takeover this button would be exactly the drift the old comment warned
    // about. [DET-WATCH-HONEST-001] [DET-TOGGLE-001]
    FirstStep.UNDERSTAND_WATCH -> when (watchAsk) {
        WatchAsk.EXPLAIN_RELEASE -> FirstStepCopy(
            title = Res.string.first_steps_watch_title,
            subtitle = Res.string.first_steps_watch_sub,
            cta = null,
            icon = Icons.Rounded.Visibility,
        )
        WatchAsk.TURN_IT_ON -> FirstStepCopy(
            title = Res.string.first_steps_watch_on_title,
            subtitle = Res.string.first_steps_watch_on_sub,
            // The label of the row this step stands in for, not a new wording of the same button.
            cta = Res.string.home_det_producer_cta,
            // A watch that is OFF is not an eye that sees.
            icon = Icons.Rounded.SensorsOff,
            asksForPermission = true,
        )
    }
    // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] The step that only exists when there is
    // something to reinforce, wearing whichever fix applies to THIS car. NONE never renders — the
    // projection drops the step from `applicable` — but the branch has to resolve to something, and
    // the honest something is the copy of a watch that is already solid.
    FirstStep.FORTIFY_WATCH -> when (reinforcement) {
        WatchReinforcement.BLUETOOTH -> FirstStepCopy(
            title = Res.string.first_steps_fortify_bt_title,
            subtitle = Res.string.first_steps_fortify_bt_sub,
            // The label of the row this step stands in for, again borrowed rather than reworded.
            cta = Res.string.vehicle_registration_bt_cta,
            icon = Icons.Rounded.Bluetooth,
            asksForPermission = true,
        )
        WatchReinforcement.BATTERY -> FirstStepCopy(
            title = Res.string.first_steps_fortify_battery_title,
            subtitle = Res.string.first_steps_fortify_battery_sub,
            cta = Res.string.home_det_watching_fortify_cta,
            icon = Icons.Rounded.BatteryAlert,
            asksForPermission = true,
        )
        WatchReinforcement.NONE -> FirstStepCopy(
            title = Res.string.first_steps_fortify_done_title,
            subtitle = Res.string.first_steps_fortify_done_sub,
            cta = null,
            icon = Icons.Rounded.Verified,
        )
    }
    // Two faces, one step. [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
    // With spots on offer the step SHOWS them; with none it asks for one, which is the half of the
    // community that does not depend on there being a community yet. The old single face said "See
    // spots" and pressed into the REPORT flow — it was wrong in both directions at once.
    FirstStep.FIND_SPOT -> when (findSpotAsk) {
        FindSpotAsk.SEE_NEARBY -> FirstStepCopy(
            title = Res.string.first_steps_spot_title,
            subtitle = Res.string.first_steps_spot_sub,
            cta = Res.string.first_steps_spot_cta,
            icon = Icons.Rounded.Explore,
        )
        FindSpotAsk.REPORT_ONE -> FirstStepCopy(
            title = Res.string.first_steps_spot_report_title,
            subtitle = Res.string.first_steps_spot_report_sub,
            cta = Res.string.first_steps_spot_report_cta,
            // The glyph follows the ask: reporting is putting a spot ON the map, the same
            // AddLocationAlt gesture step 1 uses for your own parking — not exploring for one.
            icon = Icons.Rounded.AddLocationAlt,
        )
    }
}

private const val CARD_H_PAD_DP = 16
private const val CARD_V_PAD_DP = 8
private const val ROW_V_PAD_DP = 8
private const val HEADER_GAP_DP = 8
/** Between the step's CTA and its "not yet" way out. */
private const val CTA_GAP_DP = 4
/** Inset that lines the bare status glyphs up with the 40 dp tile of the current step. */
private const val MARKER_INSET_DP = 8

/** Card padding + the status tile + the row's own gap — the CTA on its own line starts exactly where
 *  the step's words start. READ from the two components, never re-typed: a tile that changed size
 *  would otherwise leave every step's button hanging off its text. */
private val ctaIndent: Dp = CARD_H_PAD_DP.dp + PapIconTileSize + PapListItemGap
