package com.rndeveloper.paparcar.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.util.exploreFuelExplainerBody
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetBanner
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.first_steps_eyebrow
import paparcar.composeapp.generated.resources.first_steps_explain_close
import paparcar.composeapp.generated.resources.first_steps_explain_explore_body
import paparcar.composeapp.generated.resources.first_steps_explain_explore_fuel_title
import paparcar.composeapp.generated.resources.first_steps_explain_explore_note
import paparcar.composeapp.generated.resources.first_steps_explain_explore_parkings_body
import paparcar.composeapp.generated.resources.first_steps_explain_explore_parkings_title
import paparcar.composeapp.generated.resources.first_steps_explain_explore_title
import paparcar.composeapp.generated.resources.first_steps_explain_fortify_battery_body
import paparcar.composeapp.generated.resources.first_steps_explain_fortify_battery_title
import paparcar.composeapp.generated.resources.first_steps_explain_fortify_body
import paparcar.composeapp.generated.resources.first_steps_explain_fortify_bt_body
import paparcar.composeapp.generated.resources.first_steps_explain_fortify_bt_title
import paparcar.composeapp.generated.resources.first_steps_explain_fortify_note
import paparcar.composeapp.generated.resources.first_steps_explain_fortify_title
import paparcar.composeapp.generated.resources.first_steps_explain_park_body
import paparcar.composeapp.generated.resources.first_steps_explain_park_note
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
import paparcar.composeapp.generated.resources.first_steps_explain_zone_body
import paparcar.composeapp.generated.resources.first_steps_explain_zone_note
import paparcar.composeapp.generated.resources.first_steps_explain_zone_title
import paparcar.composeapp.generated.resources.first_steps_explain_zone_way_body
import paparcar.composeapp.generated.resources.first_steps_explain_zone_way_title

/**
 * The explainer behind a first step — opened by TAPPING the step's row, on its OWN surface.
 * [ONBOARDING-A-SPOT-IS-BORN-TWO-WAYS-001]
 *
 * ### Why its own sheet and not inside Home's
 * It WAS moved inside Home's sheet on 2026-09-03, to honour *«nunca abrimos modales encima de
 * modales»*. Seen on device it was worse, and it taught what that rule is really about: the problem
 * was never a second layer, it was **two visible surfaces competing**. Embedded, Home's peek ("TU
 * ZONA · Calle Traíña 10") sat right above the explainer's title fighting it for the eye, while
 * meaning nothing here; the content kept the peek's one-line limits and truncated prose with "…"
 * with half a screen empty; and the sheet's full height left a hole under the button.
 *
 * On its own sheet the scrim puts Home to sleep behind it, the height wraps the content, and the
 * page is the only thing on screen — which is what "not stacking" was after.
 * [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
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
internal data class ExplainerWay(
    val title: StringResource,
    val body: StringResource,
    val icon: ImageVector,
)

internal data class ExplainerCopy(
    val title: StringResource,
    val body: StringResource,
    val ways: List<ExplainerWay>,
    /** The caveat without which the explanation would overpromise. Null when there isn't one. */
    val note: StringResource?,
    val icon: ImageVector,
)

internal fun FirstStep.explainerCopy(): ExplainerCopy = when (this) {
    // The note is what makes this page honest, and it was the one explainer without one. The step
    // reads as a chore ("drag a pin") until it says what hangs off it: `FirstStep.MARK_PARKING` is
    // "what arms everything else" — the session, the fence and the watch — and with no parking open
    // there is no departure to notice and no spot to hand over. The BODY now also says the part the
    // web and the store listing promise and this page did not: by hand only this first time.
    // [COPY-THE-APP-NEVER-SAYS-WHAT-IT-IS-FOR-001]
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
        note = Res.string.first_steps_explain_park_note,
        icon = Icons.Rounded.AddLocationAlt,
    )

    // The exception to "leaving frees your space": a garage nobody else can enter. The note is what
    // keeps the page honest — the zone protects the parkings marked after it exists, and "no garage"
    // is a real answer. [FIRST-STEPS-A-PRIVATE-GARAGE-IS-A-PRIVATE-ZONE-001]
    FirstStep.PRIVATE_ZONE -> ExplainerCopy(
        title = Res.string.first_steps_explain_zone_title,
        body = Res.string.first_steps_explain_zone_body,
        ways = listOf(
            ExplainerWay(
                title = Res.string.first_steps_explain_zone_way_title,
                body = Res.string.first_steps_explain_zone_way_body,
                icon = Icons.Rounded.Lock,
            ),
        ),
        note = Res.string.first_steps_explain_zone_note,
        icon = Icons.Rounded.Lock,
    )

    // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] Its own page. It borrowed the watch
    // explainer at first, and on device that read as a wrong answer: you tap "let it watch without
    // interruptions" and get told what happens when you leave. Related, not the same question — this
    // one is about the watch SURVIVING, and it names the two things that keep it alive without
    // making the user care which one their car uses.
    FirstStep.FORTIFY_WATCH -> ExplainerCopy(
        title = Res.string.first_steps_explain_fortify_title,
        body = Res.string.first_steps_explain_fortify_body,
        ways = listOf(
            ExplainerWay(
                title = Res.string.first_steps_explain_fortify_bt_title,
                body = Res.string.first_steps_explain_fortify_bt_body,
                icon = Icons.Rounded.Bluetooth,
            ),
            ExplainerWay(
                title = Res.string.first_steps_explain_fortify_battery_title,
                body = Res.string.first_steps_explain_fortify_battery_body,
                icon = Icons.Rounded.BatteryAlert,
            ),
        ),
        // The caveat that keeps it honest: nothing here is required, and saying so is what makes the
        // "not yet" an offer rather than a nag.
        note = Res.string.first_steps_explain_fortify_note,
        icon = Icons.Rounded.Verified,
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

    // ── The other two questions the map answers ──
    // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
    //
    // The two ways here are not two mechanisms of one thing, as in the steps above: they are two
    // separate subjects that happen to share a property the user has to know — NEITHER comes from
    // the community. That is what the note says, and it is the only sentence on this page that
    // could not be guessed from the tab row.
    //
    // ⛔ Three words, three things: the plaza is the community's, the aparcamiento is yours, and a
    // car park is a business you drive into. This page names all three, so it is the page where they
    // are easiest to blur. [COPY-SPOT-IS-NOT-A-PARKING-001]
    FirstStep.EXPLORE_NEARBY -> ExplainerCopy(
        title = Res.string.first_steps_explain_explore_title,
        body = Res.string.first_steps_explain_explore_body,
        ways = listOf(
            ExplainerWay(
                title = Res.string.first_steps_explain_explore_parkings_title,
                body = Res.string.first_steps_explain_explore_parkings_body,
                // The "P" belongs to the public car park, and here it is exactly that — the subject
                // of its own line, not the badge of a step about two subjects. Es NUESTRA P, la
                // misma que el marcador del mapa y la fila. [UI-THE-CAR-PARK-WEARS-OUR-OWN-P-001]
                icon = PaparcarIcons.CarParkMark,
            ),
            ExplainerWay(
                title = Res.string.first_steps_explain_explore_fuel_title,
                // El litro y el galón no son la misma promesa: esta línea dice en qué unidad se van
                // a leer esos precios, y tiene que coincidir con lo que luego pinta la fila.
                // [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
                body = exploreFuelExplainerBody(),
                icon = PaparcarIcons.Fuel,
            ),
        ),
        note = Res.string.first_steps_explain_explore_note,
        icon = Icons.Rounded.Map,
    )
}

private const val SHEET_BOTTOM_DP = 20
private const val WAY_GAP_DP = 4
private const val WAY_ROW_V_PAD_DP = 6
private const val TITLE_MAX_LINES = 2
private const val WAY_TILE_ALPHA = 0.14f
