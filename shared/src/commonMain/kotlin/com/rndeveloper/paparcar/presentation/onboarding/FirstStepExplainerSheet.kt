package com.rndeveloper.paparcar.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetBanner
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.first_steps_eyebrow
import paparcar.composeapp.generated.resources.first_steps_explain_close
import paparcar.composeapp.generated.resources.first_steps_explain_park_body
import paparcar.composeapp.generated.resources.first_steps_explain_park_title
import paparcar.composeapp.generated.resources.first_steps_explain_park_way_body
import paparcar.composeapp.generated.resources.first_steps_explain_park_way_title
import paparcar.composeapp.generated.resources.first_steps_explain_spot_body
import paparcar.composeapp.generated.resources.first_steps_explain_spot_note
import paparcar.composeapp.generated.resources.first_steps_explain_spot_title
import paparcar.composeapp.generated.resources.first_steps_explain_spot_way_body
import paparcar.composeapp.generated.resources.first_steps_explain_spot_way_title
import paparcar.composeapp.generated.resources.first_steps_explain_watch_auto_body
import paparcar.composeapp.generated.resources.first_steps_explain_watch_auto_title
import paparcar.composeapp.generated.resources.first_steps_explain_watch_body
import paparcar.composeapp.generated.resources.first_steps_explain_watch_manual_body
import paparcar.composeapp.generated.resources.first_steps_explain_watch_manual_title
import paparcar.composeapp.generated.resources.first_steps_explain_watch_note
import paparcar.composeapp.generated.resources.first_steps_explain_watch_title

/**
 * The explainer behind a first step — opened by TAPPING the step's row.
 * [ONBOARDING-A-SPOT-IS-BORN-TWO-WAYS-001]
 *
 * ### What this sheet exists to say
 * A free spot is born in **two ways that have nothing to do with each other**, and until this
 * ticket the app never said so anywhere:
 *
 * 1. **You leave it.** You mark your parking; the spot appears when you drive off — either because
 *    detection noticed (automatic) or because you pressed "I'm leaving" yourself (manual). The
 *    manual half has its own second answer, "Just release", and hiding it would sell the app as
 *    more intrusive than it is.
 * 2. **You saw it.** Someone else's free space that you spotted from the street. No car of yours,
 *    no parking session, no watching.
 *
 * `COPY-SPOT-IS-NOT-A-PARKING-001` fixed the VOCABULARY for these two ("mark parking" vs "report a
 * spot") but created nowhere to learn the MECHANICS behind those words. Its own sentence — *your
 * parking frees up a spot when you leave* — was said in the body of one dialog and nowhere else.
 *
 * ### The rule this file must not break
 * The two ways never share a verb or a noun. Yours is a PARKING you MARK; the community's is a
 * SPOT you REPORT. Any sentence that mentions both names them differently.
 *
 * It also quotes the app's own buttons verbatim ("I'm leaving", "Just release") rather than
 * paraphrasing them, so a user who goes looking for what they just read finds that exact word.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstStepExplainerSheet(
    step: FirstStep,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        FirstStepExplainerContent(step = step, onDismiss = onDismiss)
    }
}

/**
 * The sheet's BODY, without the modal host. Split out so the state gallery and the previews can
 * render it directly — `ModalBottomSheet` is a window-level composable and does not survive either.
 * Same `XxxContent(state = …)` shape the rest of the project uses for exactly this reason.
 *
 * Public, not internal: the Dev Catalog lives in `:app`, a different Gradle module.
 */
@Composable
fun FirstStepExplainerContent(
    step: FirstStep,
    onDismiss: () -> Unit,
) {
    val copy = step.explainer()
    PapSheet(
        lead = PapSheetLead.GenericIcon(icon = copy.icon),
        eyebrow = stringResource(Res.string.first_steps_eyebrow),
        eyebrowTone = PapSheetEyebrowTone.Action,
        title = stringResource(copy.title),
        titleMaxLines = TITLE_MAX_LINES,
        subtitle = stringResource(copy.body),
        onDismiss = onDismiss,
        modifier = Modifier.padding(bottom = SHEET_BOTTOM_DP.dp),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(WAY_GAP_DP.dp),
            ) {
                copy.ways.forEach { way -> WayRow(way) }
            }
        },
        // The nuance that keeps each explanation honest, in the slot the sheet reserves for exactly
        // that. Step 2: releasing does NOT force publishing. Step 3: this is not your car.
        banner = copy.note?.let { note ->
            { PapSheetBanner(title = stringResource(note)) }
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.first_steps_explain_close),
                onClick = onDismiss,
                style = PapFooterButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/** One WAY the step's thing happens — the two mechanisms of a departure, or the single act of
 *  reporting. Reuses the canonical row so it reads like every other list in the app. [UI-LIST-ITEM-001] */
@Composable
private fun WayRow(way: ExplainerWay) {
    PapListItem(
        title = stringResource(way.title),
        subtitle = stringResource(way.body),
        leading = {
            PapIconTile(
                icon = way.icon,
                container = MaterialTheme.colorScheme.primary.copy(alpha = WAY_TILE_ALPHA),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = WAY_ROW_V_PAD_DP.dp),
    )
}

private data class ExplainerWay(
    val title: StringResource,
    val body: StringResource,
    val icon: ImageVector,
)

private data class ExplainerCopy(
    val title: StringResource,
    val body: StringResource,
    val ways: List<ExplainerWay>,
    /** The caveat without which the explanation would overpromise. Null when there isn't one. */
    val note: StringResource?,
    val icon: ImageVector,
)

private fun FirstStep.explainer(): ExplainerCopy = when (this) {
    FirstStep.MARK_PARKING -> ExplainerCopy(
        title = Res.string.first_steps_explain_park_title,
        body = Res.string.first_steps_explain_park_body,
        ways = listOf(
            ExplainerWay(
                title = Res.string.first_steps_explain_park_way_title,
                body = Res.string.first_steps_explain_park_way_body,
                icon = Icons.Rounded.Visibility,
            ),
        ),
        note = null,
        icon = Icons.Rounded.AddLocationAlt,
    )

    // ── FORM 1: the spot you leave behind, and its TWO mechanisms ──
    // Both are listed, and the automatic one is NOT presented as the only way: the manual
    // "I'm leaving" is the control the user always has, and a user who does not know it exists
    // is a user who thinks the app decides alone.
    FirstStep.UNDERSTAND_WATCH -> ExplainerCopy(
        title = Res.string.first_steps_explain_watch_title,
        body = Res.string.first_steps_explain_watch_body,
        ways = listOf(
            ExplainerWay(
                title = Res.string.first_steps_explain_watch_auto_title,
                body = Res.string.first_steps_explain_watch_auto_body,
                icon = Icons.Rounded.Sensors,
            ),
            ExplainerWay(
                title = Res.string.first_steps_explain_watch_manual_title,
                body = Res.string.first_steps_explain_watch_manual_body,
                icon = Icons.AutoMirrored.Rounded.Logout,
            ),
        ),
        note = Res.string.first_steps_explain_watch_note,
        icon = Icons.Rounded.Visibility,
    )

    // ── FORM 2: the spot you saw ──
    // Explicitly marked as a DIFFERENT thing. The note does the work the vocabulary alone could
    // not: no car of yours, no parking session, no detection.
    FirstStep.FIND_SPOT -> ExplainerCopy(
        title = Res.string.first_steps_explain_spot_title,
        body = Res.string.first_steps_explain_spot_body,
        ways = listOf(
            ExplainerWay(
                title = Res.string.first_steps_explain_spot_way_title,
                body = Res.string.first_steps_explain_spot_way_body,
                icon = Icons.Rounded.Campaign,
            ),
        ),
        note = Res.string.first_steps_explain_spot_note,
        icon = Icons.Rounded.Explore,
    )
}

private const val SHEET_BOTTOM_DP = 20
private const val WAY_GAP_DP = 4
private const val WAY_ROW_V_PAD_DP = 6
private const val TITLE_MAX_LINES = 2
private const val WAY_TILE_ALPHA = 0.14f
