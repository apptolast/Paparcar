package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PaparcarType

/**
 * Canonical section header for Paparcar — single typographic recipe used
 * everywhere a vertical section starts (TU COCHE, PLAZAS LIBRES, ACTIVIDAD
 * SEMANAL, ZONAS HABITUALES, etc.).
 *
 * Recipe: uppercase + the [PaparcarType.sectionHeader] role + a muted tint.
 * Anchored on the Vehicle/History screen's pattern (the one the user
 * explicitly liked) and demoted to a neutral colour by default so it works
 * in both structural and emphasised contexts.
 *
 * The `.uppercase()` and the tint live HERE, and the role owns family, size and
 * weight — which is what makes `sectionHeader` / `subsectionHeader` roles that
 * never leave this file. (This docstring used to spell the recipe out as
 * "labelMedium + ExtraBold + 1sp tracking": wording from before the role
 * system, and by then it also described the wrong scale.)
 * [UI-TYPE-SYSTEM-HYGIENE-001]
 */
@Composable
fun PapSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = title.uppercase(),
        modifier = modifier.fillMaxWidth(),
        style = PaparcarType.current.sectionHeader,
        color = color,
    )
}

/**
 * Section header with leading + trailing slots — same typographic recipe as
 * [PapSectionHeader] but composed inside a [Row] so callers can prepend a
 * status dot or append a count badge without forking the composable.
 */
@Composable
fun PapSectionHeaderRow(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /** Center the title between the slots (pager-style headers) instead of anchoring it to the
     *  leading slot. With equal-width slots on both sides the title sits on the row's optical
     *  centre. [ROUTE-QUALITY-001] */
    centerTitle: Boolean = false,
    /** A header that opens a group INSIDE a section this component already headed — the timeline's
     *  day rows under "APARCADO ACTUALMENTE". Same face, one step down (`subsectionHeader`), so the
     *  two levels are told apart by SIZE, not by swapping family. Keeping it here (instead of a
     *  `style` param) is what makes "the header roles only ever leave this file" hold.
     *  [UI-HISTORY-IDENTITY-AND-SOURCE-001] */
    dense: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        if (centerTitle) Spacer(Modifier.weight(1f))
        Text(
            text = title.uppercase(),
            style = with(PaparcarType.current) { if (dense) subsectionHeader else sectionHeader },
            color = color,
        )
        if (centerTitle) Spacer(Modifier.weight(1f))
        if (trailing != null) {
            if (!centerTitle) Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}
