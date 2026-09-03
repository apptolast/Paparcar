package com.rndeveloper.paparcar.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetBanner
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapPrimaryButton
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.first_steps_explain_close

/**
 * A first step's explanation, on its own SCREEN. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
 *
 * ### Why a screen and not a sheet
 * This was tried twice as a sheet on 2026-09-03 and failed twice, each time for the same underlying
 * reason: it is not a decision, it is a PAGE that gets read.
 *
 * 1. As its own `ModalBottomSheet` over Home's sheet: two modal layers, and on a dark theme the
 *    scrim barely dims anything, so Home's peek ("TU ZONA · Calle de la Fragata 33") kept competing
 *    with the title while meaning nothing here. Its content also ran past the bottom edge and cut
 *    "Entendido" in half against the navigation bar.
 * 2. Rendered INSIDE Home's sheet: the peek sat directly above it as a second header, and the
 *    content inherited the peek's one-line limits — prose truncated with "…" while half the screen
 *    was empty.
 *
 * A screen has none of those problems by construction: Home is gone, the height is the device's, and
 * back is the way out. It follows `VehicleSizeExplainerScreen`, the rationale screen this product
 * already had — same anatomy, so the two read as the same kind of page.
 */
@Composable
fun FirstStepExplainerScreen(
    step: FirstStep,
    onClose: () -> Unit,
) {
    val copy = step.explainerCopy()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = PaparcarSpacing.xxl)
                .padding(top = TOP_PAD, bottom = BOTTOM_PAD)
                .verticalScroll(rememberScrollState()),
        ) {
            // The step's own glyph as the anchor, in the disc the checklist gives its current step —
            // so arriving here reads as "this is that row, opened up". [UI-COLOR-DOCTRINE-001]
            PapIconTile(
                icon = copy.icon,
                container = MaterialTheme.colorScheme.primary,
                tint = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                size = HERO_TILE,
                modifier = Modifier.size(HERO_TILE),
            )
            Spacer(Modifier.height(PaparcarSpacing.lg))
            Text(
                text = stringResource(copy.title),
                style = PaparcarType.current.screenTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(PaparcarSpacing.sm))
            Text(
                text = stringResource(copy.body),
                style = PaparcarType.current.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(PaparcarSpacing.xl))

            // The ways this step's thing happens. Same canonical row as everywhere else, and nothing
            // is line-capped here: the screen has the height, and an explanation that ends in "…" is
            // not an explanation. [UI-LIST-ITEM-001]
            Column(verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.md)) {
                copy.ways.forEach { way ->
                    PapListItem(
                        title = stringResource(way.title),
                        subtitle = stringResource(way.body),
                        leading = { PapIconTile(icon = way.icon) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    )
                }
            }

            copy.note?.let { note ->
                Spacer(Modifier.height(PaparcarSpacing.xl))
                PapSheetBanner(title = stringResource(note), maxLines = Int.MAX_VALUE)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = PaparcarSpacing.xxl)
                .navigationBarsPadding()
                .padding(bottom = PaparcarSpacing.xxl),
        ) {
            PapPrimaryButton(
                label = stringResource(Res.string.first_steps_explain_close),
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val HERO_TILE = 64.dp
private val TOP_PAD = PaparcarSpacing.xxl
/** Room for the pinned button so the last line is never hidden under it. */
private val BOTTOM_PAD = 96.dp
