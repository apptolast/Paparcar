package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapControlHeight

/**
 * The small chip-shaped CTA that rides INSIDE another surface — the detection story rows and the
 * vehicle card's live-trip foot. Caller supplies the two colours because these pills speak in the
 * host's tone (severity amber/error, or a vehicle's identity colour), which is exactly what
 * [PapPrimaryButton]'s fixed Brand/Destructive axes cannot say. Label voice is the CTA role.
 * [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001] [UI-COLOR-DOCTRINE-001]
 */
@Composable
fun PapCtaPill(
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fill the available width and centre the label — used by stacked (full-width) CTAs. */
    fillWidth: Boolean = false,
    /**
     * Row-height variant: the vehicle-card foot uses a shorter pill than the story rows.
     *
     * ⛔ Only a STEP of [PapControlHeight] is a legal argument here — never a literal. The variant
     * survives because 6dp between a chip-height foot and a story-row pill is a difference you can
     * see on a phone, which is the test a step has to pass; the literal `34` that used to be passed
     * in did not, it was just a number in another file.
     * [UI-A-SCALE-NOBODY-USES-IS-NOT-A-SYSTEM-001]
     */
    height: Dp = PapControlHeight.pill,
) {
    Surface(
        shape = PapShapes.chip,
        color = container,
        onClick = onClick,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .height(height)
                .padding(horizontal = PILL_PADDING_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = PaparcarType.current.cta,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val PILL_PADDING_DP = 16
